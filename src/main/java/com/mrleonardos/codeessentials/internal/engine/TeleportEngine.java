package com.mrleonardos.codeessentials.internal.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.TeleportEvents;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

public final class TeleportEngine implements TeleportService {

    public static final String WARMUP_META = "codeessentials.warmup";

    private final Supplier<EngineRules> rules;
    private final WorldAccess world;
    private final PlayerRights rights;
    private final SafeSpotPolicy policy;
    private final Mover mover;
    private final Cooldowns cooldowns;
    private final BackService back;
    private final TeleportEvents events;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Logger log;

    private final Map<UUID, Slot> board = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public TeleportEngine(Supplier<EngineRules> rules, WorldAccess world, PlayerRights rights, SafeSpotPolicy policy,
        Mover mover, Cooldowns cooldowns, BackService back, TeleportEvents events, Scheduler scheduler,
        LongSupplier clock, Logger log) {
        this.rules = rules;
        this.world = world;
        this.rights = rights;
        this.policy = policy;
        this.mover = mover;
        this.cooldowns = cooldowns;
        this.back = back;
        this.events = events;
        this.scheduler = scheduler;
        this.clock = clock;
        this.log = log;
    }

    @Override
    public TeleportJob request(TeleportRequest request) {
        Objects.requireNonNull(request, "request");
        UUID player = request.player();
        TeleportJob job = TeleportJob.starting(ids.incrementAndGet(), request);
        Optional<BlockView> view = world.view(
            request.destination()
                .dimension());
        if (!view.isPresent()) {
            return refuse(job, CancelReason.DIMENSION_MISSING);
        }
        Point landing;
        if (request.safeSpot()) {
            SafeSpotResult found = policy.find(
                view.get(),
                request.destination(),
                rules.get()
                    .safeSpot());
            if (!found.found()) {
                return refuse(
                    job,
                    found.failure()
                        .orElse(CancelReason.UNSAFE));
            }
            landing = found.spot()
                .get();
        } else {
            landing = SafeSpotFinder.forced(
                request.destination(),
                view.get()
                    .height());
        }
        if (!allowed(job, true)) {
            return refuse(job, CancelReason.VETOED);
        }
        Work work = new Work(
            job,
            world.position(player)
                .orElse(null),
            landing,
            warmupTicks(request, player),
            request.cause() == TeleportCause.RESPAWN);
        Slot slot = board.computeIfAbsent(player, key -> new Slot());
        if (slot.active == null) {
            slot.active = work;
            return begin(player, slot, work);
        }
        if (slot.active.job.state() == TeleportJob.State.WARMUP) {
            close(slot.active, CancelReason.SUPERSEDED);
            slot.active = work;
            return begin(player, slot, work);
        }
        if (slot.next != null) {
            close(slot.next, CancelReason.SUPERSEDED);
        }
        slot.next = work;
        return work.job;
    }

    @Override
    public Optional<TeleportJob> job(UUID player) {
        Slot slot = board.get(player);
        return slot == null || slot.active == null ? Optional.<TeleportJob>empty() : Optional.of(slot.active.job);
    }

    @Override
    public List<TeleportJob> jobs() {
        List<TeleportJob> running = new ArrayList<>();
        for (Slot slot : board.values()) {
            if (slot.active != null) {
                running.add(slot.active.job);
            }
            if (slot.next != null) {
                running.add(slot.next.job);
            }
        }
        return running;
    }

    @Override
    public Optional<TeleportJob> cancel(UUID player, CancelReason reason) {
        Slot slot = board.get(player);
        if (slot == null) {
            return Optional.empty();
        }
        Work work = warming(slot.active) ? slot.active : slot.next;
        if (!warming(work)) {
            return Optional.empty();
        }
        finish(player, work, reason);
        return Optional.of(work.job);
    }

    @Override
    public long cooldownRemaining(UUID player, TeleportCause cause) {
        return cooldowns.remaining(player, cause);
    }

    @Override
    public StoreResult clearCooldowns(UUID player) {
        return cooldowns.clear(player);
    }

    public void tick() {
        if (board.isEmpty()) {
            return;
        }
        for (UUID player : new ArrayList<>(board.keySet())) {
            Slot slot = board.get(player);
            if (slot != null && slot.active != null) {
                countdown(player, slot, slot.active);
            }
        }
    }

    public void damaged(UUID player) {
        if (rules.get()
            .cancelOnDamage()) {
            cancelWarming(player, CancelReason.DAMAGED);
        }
    }

    public void died(UUID player, Point where) {
        cancelWarming(player, CancelReason.DEAD);
        if (where != null) {
            record(player, BackPoint.of(where, BackPoint.Origin.DEATH, clock.getAsLong()));
        }
    }

    /**
     * Игрок сменил измерение чужой силой: порталом, чужим модом, ванильной командой. Свой же перенос
     * приходит сюда тем же событием, поэтому работа в {@code MOVING} остаётся нетронутой: иначе
     * кроссмирный {@code /home} гасил бы стоящую в слоте работу причиной {@code MOVED}.
     */
    public void dimensionChanged(UUID player) {
        Slot slot = board.get(player);
        if (slot == null || (slot.active != null && slot.active.job.state() == TeleportJob.State.MOVING)) {
            return;
        }
        cancelWarming(player, CancelReason.MOVED);
    }

    public void respawned(UUID player) {
        Slot slot = board.get(player);
        if (slot == null) {
            return;
        }
        if (slot.next != null) {
            slot.next.deferred = false;
        }
        Work work = slot.active;
        if (work == null || !work.deferred) {
            return;
        }
        work.deferred = false;
        if (work.warmupTicks <= 0) {
            startMove(player, slot, work);
        }
    }

    public void left(UUID player) {
        Slot slot = board.remove(player);
        if (slot == null) {
            return;
        }
        if (slot.next != null) {
            close(slot.next, CancelReason.DISCONNECTED);
        }
        if (slot.active != null) {
            close(slot.active, CancelReason.DISCONNECTED);
        }
    }

    public boolean idle() {
        return board.isEmpty();
    }

    private TeleportJob begin(UUID player, Slot slot, Work work) {
        if (work.deferred || work.warmupTicks > 0) {
            return work.job;
        }
        return startMove(player, slot, work);
    }

    private TeleportJob startMove(UUID player, Slot slot, Work work) {
        if (!allowed(work.job, false)) {
            finish(player, work, CancelReason.VETOED);
            return work.job;
        }
        work.origin = world.position(player)
            .orElse(work.anchor);
        work.job = work.job.moving();
        long id = work.job.id();
        int deadline = rules.get()
            .moveDeadlineTicks();
        scheduler.afterTicks(deadline, () -> expire(player, id));
        mover.move(work.job, work.landing, new Mover.Report() {

            @Override
            public void done(Point spot) {
                land(player, work, spot);
            }

            @Override
            public void failed(CancelReason reason) {
                if (!work.job.finished()) {
                    finish(player, work, reason);
                }
            }
        });
        return work.job;
    }

    private void land(UUID player, Work work, Point spot) {
        if (work.job.state() != TeleportJob.State.MOVING) {
            return;
        }
        work.job = work.job.done(spot);
        TeleportCause cause = work.job.cause();
        if (cause.chargesCooldown()) {
            StoreResult charged = cooldowns.charge(player, cause);
            if (!charged.successful()) {
                log.warn("Cooldown {} of {} was not written down: {}", cause.key(), player, charged);
            }
        }
        if (cause.recordsBack() && work.origin != null) {
            record(player, BackPoint.of(work.origin, BackPoint.Origin.TELEPORT, clock.getAsLong()));
        }
        if (cause == TeleportCause.BACK) {
            back.pop(player);
        }
        tellMoved(work.job);
        Slot slot = board.get(player);
        if (slot != null && slot.active == work) {
            release(player, slot);
        }
    }

    private void expire(UUID player, long id) {
        Slot slot = board.get(player);
        if (slot == null || slot.active == null) {
            return;
        }
        Work work = slot.active;
        if (work.job.id() != id || work.job.state() != TeleportJob.State.MOVING) {
            return;
        }
        log.warn(
            "Teleport {} did not land in {} tick(s) and the slot of {} is free again",
            Long.valueOf(id),
            Integer.valueOf(
                rules.get()
                    .moveDeadlineTicks()),
            player);
        finish(player, work, CancelReason.TIMEOUT);
    }

    private void countdown(UUID player, Slot slot, Work work) {
        if (work.deferred || work.job.state() != TeleportJob.State.WARMUP) {
            return;
        }
        if (movedAway(player, work)) {
            finish(player, work, CancelReason.MOVED);
            return;
        }
        if (work.warmupTicks > 0) {
            work.warmupTicks--;
        }
        if (work.warmupTicks <= 0) {
            startMove(player, slot, work);
        }
    }

    private boolean movedAway(UUID player, Work work) {
        if (work.anchor == null) {
            return false;
        }
        Optional<Point> now = world.position(player);
        if (!now.isPresent()) {
            return false;
        }
        Point at = now.get();
        EngineRules current = rules.get();
        return at.horizontalDistanceTo(work.anchor) > current.moveRadius()
            || at.verticalDistanceTo(work.anchor) > current.verticalMoveRadius();
    }

    private void record(UUID player, BackPoint point) {
        StoreResult written = back.record(player, point);
        if (written.successful() || written.failure()
            .orElse(null) == StoreResult.Failure.UNSUPPORTED) {
            return;
        }
        log.warn("Return point of {} was not written down: {}", player, written);
    }

    private void cancelWarming(UUID player, CancelReason reason) {
        Slot slot = board.get(player);
        if (slot == null) {
            return;
        }
        if (warming(slot.next)) {
            finish(player, slot.next, reason);
        }
        if (warming(slot.active)) {
            finish(player, slot.active, reason);
        }
    }

    private void finish(UUID player, Work work, CancelReason reason) {
        Slot slot = board.get(player);
        close(work, reason);
        if (slot == null) {
            return;
        }
        if (slot.next == work) {
            slot.next = null;
            forget(player, slot);
            return;
        }
        if (slot.active == work) {
            release(player, slot);
        }
    }

    private void release(UUID player, Slot slot) {
        Work promoted = slot.next;
        slot.active = promoted;
        slot.next = null;
        if (promoted == null) {
            forget(player, slot);
            return;
        }
        promoted.anchor = world.position(player)
            .orElse(promoted.anchor);
        begin(player, slot, promoted);
    }

    private void forget(UUID player, Slot slot) {
        if (slot.active == null && slot.next == null) {
            board.remove(player);
        }
    }

    private TeleportJob refuse(TeleportJob job, CancelReason reason) {
        TeleportJob refused = job.stopped(reason);
        tellCancelled(refused);
        return refused;
    }

    private TeleportJob close(Work work, CancelReason reason) {
        if (work.job.finished()) {
            return work.job;
        }
        work.job = work.job.stopped(reason);
        tellCancelled(work.job);
        return work.job;
    }

    private int warmupTicks(TeleportRequest request, UUID player) {
        int seconds = request.warmupSeconds();
        if (seconds == TeleportRequest.WARMUP_FROM_SETTINGS) {
            if (!request.cause()
                .warmsUp()) {
                return 0;
            }
            OptionalInt own = rights.number(player, WARMUP_META);
            seconds = own.isPresent() && own.getAsInt() >= 0 ? own.getAsInt()
                : rules.get()
                    .warmupSeconds();
        }
        return EngineRules.ticks(
            rules.get()
                .limits()
                .clampWarmupSeconds(seconds));
    }

    private boolean allowed(TeleportJob job, boolean start) {
        for (TeleportEvents.Listener listener : events.listeners()) {
            EssentialsEvents.Decision decision;
            try {
                decision = start ? listener.beforeStart(job) : listener.beforeMove(job);
            } catch (RuntimeException failure) {
                if (listener.kind() == EssentialsEvents.Kind.ENFORCE) {
                    log.error("Teleport check failed and the move is refused: {}", failure.toString(), failure);
                    return false;
                }
                log.error("Teleport listener failed and was skipped: {}", failure.toString(), failure);
                continue;
            }
            if (decision == null || !decision.allowed()) {
                log.debug(
                    "Teleport {} was refused by a listener: {}",
                    job,
                    decision == null ? "no answer" : decision.toString());
                return false;
            }
        }
        return true;
    }

    private void tellMoved(TeleportJob job) {
        for (TeleportEvents.Listener listener : events.listeners()) {
            try {
                listener.afterMove(job);
            } catch (RuntimeException failure) {
                log.error("Teleport listener failed after the move: {}", failure.toString(), failure);
            }
        }
    }

    private void tellCancelled(TeleportJob job) {
        for (TeleportEvents.Listener listener : events.listeners()) {
            try {
                listener.cancelled(job);
            } catch (RuntimeException failure) {
                log.error("Teleport listener failed on a cancelled move: {}", failure.toString(), failure);
            }
        }
    }

    private static boolean warming(Work work) {
        return work != null && !work.deferred && work.job.state() == TeleportJob.State.WARMUP;
    }

    private static final class Slot {

        private Work active;
        private Work next;
    }

    private static final class Work {

        private final Point landing;

        private Point anchor;
        private TeleportJob job;
        private Point origin;
        private int warmupTicks;
        private boolean deferred;

        Work(TeleportJob job, Point anchor, Point landing, int warmupTicks, boolean deferred) {
            this.job = job;
            this.anchor = anchor;
            this.landing = landing;
            this.warmupTicks = warmupTicks;
            this.deferred = deferred;
        }
    }
}
