package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.internal.store.SingleWriterImpl;

class TeleportEngineTest {

    private static final Logger LOG = LogManager.getLogger(TeleportEngineTest.class);
    private static final Point START = Point.of(0, 0.5D, 64.0D, 0.5D, 90.0F, 0.0F);
    private static final Point HOME = Point.of(0, 5.5D, 64.0D, 5.5D, 45.0F, -10.0F);
    private static final Point SPAWN = Point.of(0, -5.5D, 64.0D, -5.5D);
    private static final int WARMUP_TICKS = EngineRules.DEFAULT_WARMUP_SECONDS * EngineRules.TICKS_PER_SECOND;

    private final AtomicLong clock = new AtomicLong(100_000L);
    private final EngineFixtures.FakeWorlds worlds = new EngineFixtures.FakeWorlds();
    private final EngineFixtures.FakeRights rights = new EngineFixtures.FakeRights();
    private final EngineFixtures.FakeMover mover = new EngineFixtures.FakeMover();
    private final EngineFixtures.FakeEvents events = new EngineFixtures.FakeEvents();
    private final EngineFixtures.TestScheduler scheduler = new EngineFixtures.TestScheduler();
    private final EngineFixtures.Watcher watcher = new EngineFixtures.Watcher(EssentialsEvents.Kind.INFORM);

    private SingleWriterImpl writer;
    private Cooldowns cooldowns;
    private BackLog back;
    private TeleportEngine engine;

    @BeforeEach
    void setUp() {
        worlds.world(0)
            .plate(63, -10, 10);
        worlds.world(-1)
            .plate(63, -10, 10);
        worlds.standing(EngineFixtures.STEVE, START);
        events.register(0, watcher);
        build(rules(EngineRules.DEFAULT_WARMUP_SECONDS));
    }

    @Test
    void teleportWithoutWarmupLandsAtOnce() {
        TeleportJob job = ask(TeleportCause.ADMIN, HOME);

        assertEquals(TeleportJob.State.DONE, job.state());
        assertEquals(
            HOME,
            job.landing()
                .get());
        assertEquals(1, mover.moves());
        assertTrue(engine.idle(), "законченная работа обязана освободить слот");
    }

    @Test
    void warmupHoldsTheJobUntilTheLastTick() {
        ask(TeleportCause.HOME, HOME);

        tick(WARMUP_TICKS - 1);

        assertEquals(TeleportJob.State.WARMUP, current().state());
        assertEquals(0, mover.moves());

        tick(1);

        assertEquals(1, mover.moves());
        assertEquals(
            TeleportJob.State.DONE,
            watcher.moved()
                .get(0)
                .state());
    }

    @Test
    void personalWarmupFromMetaWinsOverTheSetting() {
        rights.meta(EngineFixtures.STEVE, TeleportEngine.WARMUP_META, 1);

        ask(TeleportCause.HOME, HOME);
        tick(EngineRules.TICKS_PER_SECOND);

        assertEquals(1, mover.moves());
    }

    @Test
    void lastRequestWinsWhileTheFirstIsStillWarmingUp() {
        TeleportJob first = ask(TeleportCause.HOME, HOME);
        tick(10);

        ask(TeleportCause.SPAWN, SPAWN);

        assertEquals(
            CancelReason.SUPERSEDED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertEquals(
            first.id(),
            watcher.cancelled()
                .get(0)
                .id());
        assertEquals(0L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME), "снятая работа бесплатна");

        tick(WARMUP_TICKS);

        assertEquals(
            SPAWN,
            watcher.moved()
                .get(0)
                .landing()
                .get());
        assertTrue(engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.SPAWN) > 0L);
    }

    @Test
    void thirdRequestPushesTheWaitingOneOutOfTheSlot() {
        mover.holdOn();
        TeleportJob moving = ask(TeleportCause.ADMIN, HOME);
        TeleportJob waiting = ask(TeleportCause.ADMIN, SPAWN);
        TeleportJob latest = ask(TeleportCause.ADMIN, HOME);

        assertEquals(TeleportJob.State.MOVING, moving.state());
        assertEquals(
            waiting.id(),
            watcher.cancelled()
                .get(0)
                .id());
        assertEquals(
            CancelReason.SUPERSEDED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertEquals(
            2,
            engine.jobs()
                .size());

        mover.land();

        assertEquals(2, mover.moves(), "после посадки первой работы место занимает последняя просьба");
        assertEquals(
            latest.id(),
            engine.job(EngineFixtures.STEVE)
                .get()
                .id());
    }

    @Test
    void walkingAwayCancelsTheWarmupAndTurningTheCameraDoesNot() {
        ask(TeleportCause.HOME, HOME);
        tick(5);
        worlds.standing(EngineFixtures.STEVE, Point.of(0, 0.5D, 64.0D, 0.5D, 180.0F, 45.0F));

        tick(5);

        assertEquals(TeleportJob.State.WARMUP, current().state(), "поворот камеры не двигает игрока");

        worlds.standing(EngineFixtures.STEVE, Point.of(0, 3.5D, 64.0D, 0.5D, 180.0F, 45.0F));
        tick(1);

        assertEquals(
            CancelReason.MOVED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertTrue(engine.idle());
    }

    @Test
    void stepAsideInsideTheRadiusIsForgiven() {
        ask(TeleportCause.HOME, HOME);
        worlds.standing(EngineFixtures.STEVE, Point.of(0, 2.0D, 64.5D, 0.5D));

        tick(WARMUP_TICKS);

        assertEquals(1, mover.moves());
    }

    @Test
    void damageCancelsWhileTheSettingIsOnAndIsIgnoredWhenItIsOff() {
        ask(TeleportCause.HOME, HOME);

        engine.damaged(EngineFixtures.STEVE);

        assertEquals(
            CancelReason.DAMAGED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertEquals(0L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));

        build(
            EngineRules.builder()
                .cancelOnDamage(false)
                .build());
        ask(TeleportCause.HOME, HOME);
        engine.damaged(EngineFixtures.STEVE);

        assertEquals(TeleportJob.State.WARMUP, current().state());
    }

    @Test
    void deathCancelsTheWarmupAndWritesTheDeathPointToTheStack() {
        ask(TeleportCause.HOME, HOME);

        engine.died(EngineFixtures.STEVE, START);

        assertEquals(
            CancelReason.DEAD,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertEquals(
            BackPoint.Origin.DEATH,
            back.peek(EngineFixtures.STEVE)
                .get()
                .origin());
        assertEquals(
            START,
            back.peek(EngineFixtures.STEVE)
                .get()
                .point());
    }

    @Test
    void leavingTheServerDropsEveryJobOfThePlayer() {
        mover.holdOn();
        ask(TeleportCause.ADMIN, HOME);
        ask(TeleportCause.ADMIN, SPAWN);

        engine.left(EngineFixtures.STEVE);

        assertEquals(
            2,
            watcher.cancelled()
                .size());
        assertEquals(
            CancelReason.DISCONNECTED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertTrue(engine.idle());
    }

    @Test
    void respawnTargetWaitsForTheRespawnEventAndDiesWithTheSession() {
        TeleportJob job = ask(TeleportCause.RESPAWN, SPAWN);
        tick(WARMUP_TICKS);

        assertEquals(TeleportJob.State.WARMUP, job.state());
        assertEquals(0, mover.moves(), "цель респауна ждёт события, а не тиков");

        engine.respawned(EngineFixtures.STEVE);

        assertEquals(1, mover.moves());

        ask(TeleportCause.RESPAWN, SPAWN);
        engine.left(EngineFixtures.STEVE);
        engine.respawned(EngineFixtures.STEVE);

        assertEquals(1, mover.moves(), "выход до респауна отбрасывает цель");
    }

    @Test
    void vetoBeforeTheStartRefusesTheJobRightAway() {
        EngineFixtures.Watcher guard = new EngineFixtures.Watcher(EssentialsEvents.Kind.ENFORCE);
        guard.denyStart("this is a private region");
        events.register(0, guard);

        TeleportJob job = ask(TeleportCause.HOME, HOME);

        assertEquals(TeleportJob.State.CANCELLED, job.state());
        assertEquals(
            CancelReason.VETOED,
            job.reason()
                .get());
        assertEquals(0, mover.moves());
        assertTrue(engine.idle());
    }

    @Test
    void vetoBeforeTheMoveRefusesAfterTheWarmup() {
        EngineFixtures.Watcher guard = new EngineFixtures.Watcher(EssentialsEvents.Kind.ENFORCE);
        guard.denyMove("the region changed hands");
        events.register(0, guard);

        ask(TeleportCause.HOME, HOME);
        tick(WARMUP_TICKS);

        assertEquals(
            CancelReason.VETOED,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertEquals(0, mover.moves());
        assertEquals(0L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void brokenGuardRefusesAndBrokenObserverIsOnlyLogged() {
        EngineFixtures.Watcher guard = new EngineFixtures.Watcher(EssentialsEvents.Kind.ENFORCE);
        guard.breakWith(new IllegalStateException("the region index is not loaded"));
        events.register(0, guard);

        assertEquals(
            CancelReason.VETOED,
            ask(TeleportCause.HOME, HOME).reason()
                .get());

        events.unregister(guard);
        EngineFixtures.Watcher noisy = new EngineFixtures.Watcher(EssentialsEvents.Kind.INFORM);
        noisy.breakWith(new IllegalStateException("the statistics backend is down"));
        events.register(0, noisy);

        assertEquals(TeleportJob.State.WARMUP, ask(TeleportCause.HOME, HOME).state());
    }

    @Test
    void refusalByTheWorldIsFreeOfChargeAndLeavesNoTrace() {
        build(rules(0));
        mover.failWith(CancelReason.CHUNK_MISSING);

        TeleportJob job = ask(TeleportCause.HOME, HOME);

        assertEquals(TeleportJob.State.FAILED, job.state());
        assertEquals(
            CancelReason.CHUNK_MISSING,
            job.reason()
                .get());
        assertEquals(0L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertTrue(
            back.stack(EngineFixtures.STEVE)
                .isEmpty());
    }

    @Test
    void unsafeTargetIsRefusedWithoutTouchingThePlayer() {
        for (int y = 64; y <= 72; y++) {
            worlds.world(0)
                .plate(y, 2, 8);
        }

        TeleportJob job = ask(TeleportCause.HOME, Point.of(0, 5.5D, 64.0D, 5.5D));

        assertEquals(TeleportJob.State.FAILED, job.state());
        assertEquals(
            CancelReason.UNSAFE,
            job.reason()
                .get());
        assertEquals(0, mover.moves());
    }

    @Test
    void unknownDimensionIsRefusedBeforeAnySearch() {
        TeleportJob job = ask(TeleportCause.HOME, Point.of(7, 5.5D, 64.0D, 5.5D));

        assertEquals(
            CancelReason.DIMENSION_MISSING,
            job.reason()
                .get());
        assertEquals(TeleportJob.State.FAILED, job.state());
    }

    @Test
    void forcedMoveSkipsTheSearchAndStaysInsideTheWorld() {
        TeleportJob job = engine.request(
            TeleportRequest.builder(EngineFixtures.STEVE, Point.of(0, 5.5D, 900.0D, 5.5D), TeleportCause.ADMIN)
                .safeSpot(false)
                .build());

        assertEquals(TeleportJob.State.DONE, job.state());
        assertEquals(
            255.0D,
            job.landing()
                .get()
                .y());
    }

    @Test
    void hangingMoveIsFreedByTheDeadline() {
        mover.holdOn();
        TeleportJob job = ask(TeleportCause.ADMIN, HOME);

        assertEquals(TeleportJob.State.MOVING, job.state());

        scheduler.drain();

        assertEquals(
            CancelReason.TIMEOUT,
            watcher.cancelled()
                .get(0)
                .reason()
                .get());
        assertTrue(engine.idle(), "зависшая работа обязана освободить слот игрока");
    }

    @Test
    void deadlineLeavesALandedJobAlone() {
        ask(TeleportCause.ADMIN, HOME);

        scheduler.drain();

        assertTrue(
            watcher.cancelled()
                .isEmpty());
        assertEquals(
            1,
            watcher.moved()
                .size());
    }

    @Test
    void sourcePointIsWrittenAtTheMomentOfTheMoveNotOfTheRequest() {
        build(rules(EngineRules.DEFAULT_WARMUP_SECONDS));
        ask(TeleportCause.HOME, HOME);
        tick(10);
        Point later = Point.of(0, 1.5D, 64.0D, 1.5D, 90.0F, 0.0F);
        worlds.standing(EngineFixtures.STEVE, later);

        tick(WARMUP_TICKS);

        assertEquals(
            later,
            back.peek(EngineFixtures.STEVE)
                .get()
                .point());
        assertEquals(
            BackPoint.Origin.TELEPORT,
            back.peek(EngineFixtures.STEVE)
                .get()
                .origin());
    }

    @Test
    void backCauseTakesTheEntryOffTheStackAndWritesNothingNew() {
        build(rules(0));
        ask(TeleportCause.HOME, HOME);
        assertEquals(
            1,
            back.stack(EngineFixtures.STEVE)
                .size());

        ask(TeleportCause.BACK, START);

        assertTrue(
            back.stack(EngineFixtures.STEVE)
                .isEmpty(),
            "возврат снимает использованную запись и не пишет себя");
    }

    @Test
    void refusedBackKeepsTheEntryOnTheStack() {
        build(rules(0));
        ask(TeleportCause.HOME, HOME);
        mover.failWith(CancelReason.DIMENSION_MISSING);

        ask(TeleportCause.BACK, START);

        assertEquals(
            1,
            back.stack(EngineFixtures.STEVE)
                .size(),
            "отказ переноса не выталкивает запись");
    }

    @Test
    void cancelTakesTheWarmingJobAndLeavesTheMovingOne() {
        ask(TeleportCause.HOME, HOME);

        TeleportJob cancelled = engine.cancel(EngineFixtures.STEVE, CancelReason.BY_COMMAND)
            .get();

        assertEquals(TeleportJob.State.CANCELLED, cancelled.state());
        assertTrue(engine.idle());
        assertFalse(
            engine.cancel(EngineFixtures.STEVE, CancelReason.BY_COMMAND)
                .isPresent());

        mover.holdOn();
        ask(TeleportCause.ADMIN, HOME);

        assertFalse(
            engine.cancel(EngineFixtures.STEVE, CancelReason.BY_COMMAND)
                .isPresent(),
            "начатый перенос уже не отменить");
    }

    @Test
    void cooldownIsChargedOnceThePlayerHasActuallyMoved() {
        build(rules(0));

        ask(TeleportCause.HOME, HOME);

        assertEquals(30_000L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));

        clock.set(clock.get() + 10_000L);

        assertEquals(20_000L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertTrue(engine.clearCooldowns(EngineFixtures.STEVE));
        assertEquals(0L, engine.cooldownRemaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void jobsReportBothTheActiveAndTheWaitingWork() {
        mover.holdOn();
        ask(TeleportCause.ADMIN, HOME);
        ask(TeleportCause.ADMIN, SPAWN);

        List<TeleportJob> running = engine.jobs();

        assertEquals(2, running.size());
        assertEquals(
            TeleportJob.State.MOVING,
            running.get(0)
                .state());
        assertEquals(
            TeleportJob.State.WARMUP,
            running.get(1)
                .state());
    }

    private TeleportJob ask(TeleportCause cause, Point destination) {
        return engine.request(
            TeleportRequest.builder(EngineFixtures.STEVE, destination, cause)
                .build());
    }

    private TeleportJob current() {
        return engine.job(EngineFixtures.STEVE)
            .get();
    }

    private void tick(int times) {
        for (int index = 0; index < times; index++) {
            engine.tick();
        }
    }

    private static EngineRules rules(int warmupSeconds) {
        return EngineRules.builder()
            .warmupSeconds(warmupSeconds)
            .cooldown(TeleportCause.HOME, 30)
            .cooldown(TeleportCause.SPAWN, 15)
            .build();
    }

    private void build(EngineRules rules) {
        writer = EngineFixtures.writer(clock::get);
        cooldowns = new Cooldowns(writer, rights, () -> rules, clock::get);
        back = new BackLog(writer, rights, () -> rules);
        engine = new TeleportEngine(
            () -> rules,
            worlds,
            rights,
            new SafeSpotFinder(),
            mover,
            cooldowns,
            back,
            events,
            scheduler,
            clock::get,
            LOG);
    }
}
