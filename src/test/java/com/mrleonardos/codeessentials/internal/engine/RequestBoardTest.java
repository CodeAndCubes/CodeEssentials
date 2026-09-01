package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.service.BackServiceImpl;
import com.mrleonardos.codeessentials.internal.service.StateWriter;

class RequestBoardTest {

    private static final Logger LOG = LogManager.getLogger(RequestBoardTest.class);
    private static final Point AT_STEVE = Point.of(0, 0.5D, 64.0D, 0.5D);
    private static final Point AT_ALEX = Point.of(0, 8.5D, 64.0D, 8.5D);
    private static final Point AT_NOTCH = Point.of(0, -8.5D, 64.0D, -8.5D);

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final EngineFixtures.FakeWorlds worlds = new EngineFixtures.FakeWorlds();
    private final EngineFixtures.FakeRights rights = new EngineFixtures.FakeRights();
    private final EngineFixtures.FakeMover mover = new EngineFixtures.FakeMover();
    private final EngineFixtures.FakeEvents events = new EngineFixtures.FakeEvents();
    private final EngineFixtures.TestScheduler scheduler = new EngineFixtures.TestScheduler();

    private Cooldowns cooldowns;
    private TeleportEngine engine;
    private RequestBoard board;

    @BeforeEach
    void setUp() {
        worlds.world(0)
            .plate(63, -20, 20);
        worlds.standing(EngineFixtures.STEVE, AT_STEVE);
        worlds.standing(EngineFixtures.ALEX, AT_ALEX);
        worlds.standing(EngineFixtures.NOTCH, AT_NOTCH);
        build(rules(2, 60, 0));
    }

    @Test
    void nobodyAsksThemselvesToComeOver() {
        assertEquals(
            RequestBoard.Outcome.SELF,
            board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.STEVE, "Steve", RequestBoard.Kind.TO_TARGET)
                .outcome());
    }

    @Test
    void closedDoorTurnsRequestsAwayAndOpensAgain() {
        assertFalse(board.toggle(EngineFixtures.ALEX), "переключатель закрывает приём");
        assertEquals(RequestBoard.Outcome.BLOCKED, ask(RequestBoard.Kind.TO_TARGET).outcome());

        assertTrue(board.toggle(EngineFixtures.ALEX));
        assertEquals(RequestBoard.Outcome.SENT, ask(RequestBoard.Kind.TO_TARGET).outcome());
    }

    @Test
    void closingTheDoorDropsWhatAlreadyCameIn() {
        ask(RequestBoard.Kind.TO_TARGET);

        board.toggle(EngineFixtures.ALEX);
        board.toggle(EngineFixtures.ALEX);

        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
    }

    @Test
    void acceptWithoutANameTakesTheOnlyRequestAndStartsTheMove() {
        ask(RequestBoard.Kind.TO_TARGET);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.ACCEPTED, answer.outcome());
        TeleportJob job = answer.job()
            .get();
        assertEquals(TeleportJob.State.DONE, job.state());
        assertEquals(EngineFixtures.STEVE, job.player());
        assertEquals(TeleportCause.TPA, job.cause());
        assertEquals(
            AT_ALEX,
            job.landing()
                .get(),
            "по /tpa идёт проситель, а не адресат");
    }

    @Test
    void hereRequestSendsTheOtherWayRound() {
        board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.HERE);

        TeleportJob job = board.accept(EngineFixtures.ALEX, null)
            .job()
            .get();

        assertEquals(EngineFixtures.ALEX, job.player());
        assertEquals(
            AT_STEVE,
            job.landing()
                .get());
    }

    @Test
    void twoRequestsMakeTheAnswerAskWhichOne() {
        ask(RequestBoard.Kind.TO_TARGET);
        board.send(EngineFixtures.NOTCH, "Notch", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.TO_TARGET);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.AMBIGUOUS, answer.outcome());
        assertEquals(Arrays.asList("Steve", "Notch"), answer.names());
        assertEquals(0, mover.moves(), "пока имя не названо, никто никуда не идёт");

        assertEquals(
            EngineFixtures.NOTCH,
            board.accept(EngineFixtures.ALEX, "notch")
                .ticket()
                .get()
                .from(),
            "имя разбирается без оглядки на регистр");
    }

    @Test
    void repeatRequestFromTheSameSenderReplacesTheOldOne() {
        ask(RequestBoard.Kind.TO_TARGET);
        board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.HERE);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.ACCEPTED, answer.outcome(), "второй просьбы того же игрока на доске нет");
        assertEquals(
            RequestBoard.Kind.HERE,
            answer.ticket()
                .get()
                .kind());
    }

    @Test
    void thirdSenderPushesTheOldestOffTheBoard() {
        ask(RequestBoard.Kind.TO_TARGET);
        board.send(EngineFixtures.NOTCH, "Notch", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.TO_TARGET);
        board.send(EngineFixtures.HEROBRINE, "Herobrine", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.TO_TARGET);

        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, "Steve")
                .outcome(),
            "старейшую просьбу вытеснили");
        assertEquals(
            Arrays.asList("Notch", "Herobrine"),
            board.accept(EngineFixtures.ALEX, null)
                .names());
    }

    @Test
    void denyDropsTheRequestAndNobodyMoves() {
        ask(RequestBoard.Kind.TO_TARGET);

        assertEquals(
            RequestBoard.Outcome.DENIED,
            board.deny(EngineFixtures.ALEX, null)
                .outcome());
        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
        assertEquals(0, mover.moves());
    }

    @Test
    void senderTakesTheRequestBack() {
        ask(RequestBoard.Kind.TO_TARGET);

        assertEquals(
            RequestBoard.Outcome.CANCELLED,
            board.cancel(EngineFixtures.STEVE)
                .outcome());
        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
        assertEquals(
            RequestBoard.Outcome.NONE,
            board.cancel(EngineFixtures.STEVE)
                .outcome());
    }

    @Test
    void requestIsGoneWhenItsDeadlinePasses() {
        board.start();
        ask(RequestBoard.Kind.TO_TARGET);

        clock.set(clock.get() + 61_000L);
        scheduler.drain();

        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
        assertTrue(scheduler.pending() > 0, "уборка обязана встать в очередь заново");
        board.stop();
    }

    @Test
    void leavingTheServerClearsBothSidesOfTheBoard() {
        ask(RequestBoard.Kind.TO_TARGET);
        board.send(EngineFixtures.NOTCH, "Notch", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.TO_TARGET);

        board.left(EngineFixtures.STEVE);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.ACCEPTED, answer.outcome());
        assertEquals(
            EngineFixtures.NOTCH,
            answer.ticket()
                .get()
                .from());

        board.left(EngineFixtures.ALEX);

        assertEquals(
            RequestBoard.Outcome.NONE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
    }

    @Test
    void requestRateHoldsBackTheSpammer() {
        build(rules(8, 60, 10));

        assertEquals(RequestBoard.Outcome.SENT, ask(RequestBoard.Kind.TO_TARGET).outcome());

        RequestBoard.Answer second = board
            .send(EngineFixtures.STEVE, "Steve", EngineFixtures.NOTCH, "Notch", RequestBoard.Kind.TO_TARGET);

        assertEquals(RequestBoard.Outcome.RATE_LIMITED, second.outcome());
        assertEquals(10_000L, second.waitMillis());

        clock.set(clock.get() + 11_000L);

        assertEquals(
            RequestBoard.Outcome.SENT,
            board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.NOTCH, "Notch", RequestBoard.Kind.TO_TARGET)
                .outcome());
    }

    @Test
    void theTpaCooldownOfTheCarriedPlayerStopsTheAcceptance() {
        build(cooling(30));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.TPA);
        ask(RequestBoard.Kind.TO_TARGET);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.COOLING_DOWN, answer.outcome());
        assertEquals(30_000L, answer.waitMillis());
        assertEquals(
            EngineFixtures.STEVE,
            answer.ticket()
                .get()
                .moved());
        assertEquals(0, mover.moves());

        clock.set(clock.get() + 31_000L);

        assertEquals(
            RequestBoard.Outcome.ACCEPTED,
            board.accept(EngineFixtures.ALEX, null)
                .outcome(),
            "отказ по кулдауну не съедает просьбу, её принимают позже");
    }

    @Test
    void theCooldownOfATpaHereBelongsToTheAddressee() {
        build(cooling(30));
        cooldowns.charge(EngineFixtures.ALEX, TeleportCause.TPA);
        board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.HERE);

        RequestBoard.Answer answer = board.accept(EngineFixtures.ALEX, null);

        assertEquals(RequestBoard.Outcome.COOLING_DOWN, answer.outcome());
        assertEquals(0, mover.moves(), "по /tpahere несут адресата, его кулдаун и смотрят");
    }

    @Test
    void theCooldownOfTheAskerDoesNotStopATpaHere() {
        build(cooling(30));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.TPA);
        board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.ALEX, "Alex", RequestBoard.Kind.HERE);

        assertEquals(
            RequestBoard.Outcome.ACCEPTED,
            board.accept(EngineFixtures.ALEX, null)
                .outcome(),
            "проситель остаётся на месте, значит его кулдаун ни при чём");
        assertEquals(1, mover.moves());
    }

    @Test
    void theBypassNodeOfTheCarriedPlayerOpensTheWay() {
        build(cooling(30));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.TPA);
        rights.allow(EngineFixtures.STEVE, Cooldowns.BYPASS_NODE);
        ask(RequestBoard.Kind.TO_TARGET);

        assertEquals(
            RequestBoard.Outcome.ACCEPTED,
            board.accept(EngineFixtures.ALEX, null)
                .outcome(),
            "обход смотрят у того, кого несут, а не у того, кто нажал /tpaccept");
        assertEquals(1, mover.moves());
    }

    @Test
    void acceptingAnAbsentPlayerMovesNobody() {
        ask(RequestBoard.Kind.TO_TARGET);
        worlds.gone(EngineFixtures.ALEX);

        assertEquals(
            RequestBoard.Outcome.OFFLINE,
            board.accept(EngineFixtures.ALEX, null)
                .outcome());
        assertEquals(0, mover.moves());
    }

    private RequestBoard.Answer ask(RequestBoard.Kind kind) {
        return board.send(EngineFixtures.STEVE, "Steve", EngineFixtures.ALEX, "Alex", kind);
    }

    private static EngineRules rules(int maxPending, int timeoutSeconds, int rateSeconds) {
        return EngineRules.builder()
            .maxPending(maxPending)
            .requestTimeoutSeconds(timeoutSeconds)
            .requestRateSeconds(rateSeconds)
            .warmupSeconds(0)
            .build();
    }

    private static EngineRules cooling(int tpaSeconds) {
        return EngineRules.builder()
            .maxPending(2)
            .requestTimeoutSeconds(60)
            .requestRateSeconds(0)
            .warmupSeconds(0)
            .cooldown(TeleportCause.TPA, tpaSeconds)
            .build();
    }

    private void build(EngineRules rules) {
        cooldowns = new Cooldowns(EngineFixtures.writer(clock::get), rights, () -> rules, clock::get);
        engine = new TeleportEngine(
            () -> rules,
            worlds,
            rights,
            new SafeSpotFinder(),
            mover,
            cooldowns,
            new BackServiceImpl(
                EssentialsSettings::defaults,
                rights,
                new StateWriter(EngineFixtures.writer(clock::get)),
                LOG),
            events,
            scheduler,
            clock::get,
            LOG);
        board = new RequestBoard(engine, worlds, cooldowns, () -> rules, scheduler, clock::get);
    }
}
