package com.mrleonardos.codeessentials.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;
import com.mrleonardos.codeessentials.internal.engine.PlayerRights;
import com.mrleonardos.codeessentials.internal.engine.WorldAccess;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

final class PlatformStubs {

    private PlatformStubs() {}

    static final class Worlds implements WorldAccess {

        private final Map<UUID, Point> here = new LinkedHashMap<>();

        Worlds put(UUID player, Point at) {
            here.put(player, at);
            return this;
        }

        void gone(UUID player) {
            here.remove(player);
        }

        @Override
        public Optional<BlockView> view(int dimension) {
            return Optional.empty();
        }

        @Override
        public Optional<Point> position(UUID player) {
            return Optional.ofNullable(here.get(player));
        }
    }

    static final class Rights implements PlayerRights {

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public OptionalInt number(UUID player, String key) {
            return OptionalInt.empty();
        }
    }

    static final class Memory implements SingleWriter {

        private EssentialsState state = EssentialsState.empty();

        @Override
        public EssentialsState state() {
            return state;
        }

        @Override
        public StoreResult commit(EssentialsState next, ChangeBatch batch) {
            state = next;
            return StoreResult.success();
        }

        @Override
        public void flush() {}
    }

    static final class Now implements Scheduler {

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}
    }

    static final class Teleports implements TeleportService {

        private final List<TeleportRequest> asked = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        CancelReason refusal;

        List<TeleportRequest> asked() {
            return asked;
        }

        @Override
        public TeleportJob request(TeleportRequest request) {
            asked.add(request);
            TeleportJob job = TeleportJob.starting(ids.incrementAndGet(), request);
            return refusal == null ? job : job.stopped(refusal);
        }

        @Override
        public Optional<TeleportJob> job(UUID player) {
            return Optional.empty();
        }

        @Override
        public List<TeleportJob> jobs() {
            return new ArrayList<>();
        }

        @Override
        public Optional<TeleportJob> cancel(UUID player, CancelReason reason) {
            return Optional.empty();
        }

        @Override
        public long cooldownRemaining(UUID player, TeleportCause cause) {
            return 0L;
        }

        @Override
        public StoreResult clearCooldowns(UUID player) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, player.toString());
        }
    }
}
