package com.mrleonardos.codeessentials.internal.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.TeleportEvents;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.BlockSample;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.internal.service.PlayerMeta;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.MemoryPlayerDataStore;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;
import com.mrleonardos.codeessentials.internal.store.SingleWriterImpl;

public final class EngineFixtures {

    public static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID NOTCH = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID HEROBRINE = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private EngineFixtures() {}

    public static SingleWriterImpl writer(LongSupplier clock) {
        return writer(new MemoryPlayerDataStore.Medium(), clock);
    }

    static SingleWriterImpl writer(MemoryPlayerDataStore.Medium medium, LongSupplier clock) {
        SingleWriterImpl writer = new SingleWriterImpl(
            new MemoryPlayerDataStore(medium, clock),
            new TestScheduler(),
            org.apache.logging.log4j.LogManager.getLogger(EngineFixtures.class),
            clock,
            20);
        writer.start();
        return writer;
    }

    public static final class FakeWorld implements BlockView {

        private final Map<String, BlockSample> blocks = new HashMap<>();
        private final Set<String> unloaded = new HashSet<>();
        private final Set<String> broughtUp = new LinkedHashSet<>();
        private final int dimension;

        private int samples;

        FakeWorld(int dimension) {
            this.dimension = dimension;
        }

        Set<String> broughtUp() {
            return broughtUp;
        }

        FakeWorld put(int x, int y, int z, BlockSample sample) {
            blocks.put(key(x, y, z), sample);
            return this;
        }

        FakeWorld floor(int x, int y, int z) {
            return put(x, y, z, BlockSample.SOLID);
        }

        public FakeWorld plate(int y, int from, int to) {
            for (int x = from; x <= to; x++) {
                for (int z = from; z <= to; z++) {
                    floor(x, y, z);
                }
            }
            return this;
        }

        FakeWorld unloadChunk(int chunkX, int chunkZ) {
            unloaded.add(chunkX + ":" + chunkZ);
            return this;
        }

        int samples() {
            return samples;
        }

        @Override
        public int dimension() {
            return dimension;
        }

        @Override
        public boolean chunkLoaded(int blockX, int blockZ) {
            return !unloaded.contains((blockX >> 4) + ":" + (blockZ >> 4));
        }

        @Override
        public boolean bringUpChunk(int blockX, int blockZ) {
            broughtUp.add((blockX >> 4) + ":" + (blockZ >> 4));
            return chunkLoaded(blockX, blockZ);
        }

        @Override
        public BlockSample sample(int blockX, int blockY, int blockZ) {
            samples++;
            BlockSample held = blocks.get(key(blockX, blockY, blockZ));
            return held == null ? BlockSample.AIR : held;
        }

        @Override
        public int height() {
            return 256;
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    public static final class FakeWorlds implements WorldAccess {

        private final Map<Integer, FakeWorld> worlds = new LinkedHashMap<>();
        private final Map<UUID, Point> positions = new LinkedHashMap<>();

        public FakeWorld world(int dimension) {
            return worlds.computeIfAbsent(Integer.valueOf(dimension), FakeWorld::new);
        }

        public void standing(UUID player, Point point) {
            positions.put(player, point);
        }

        void gone(UUID player) {
            positions.remove(player);
        }

        @Override
        public Optional<BlockView> view(int dimension) {
            return Optional.ofNullable(worlds.get(Integer.valueOf(dimension)));
        }

        @Override
        public Optional<Point> position(UUID player) {
            return Optional.ofNullable(positions.get(player));
        }
    }

    public static final class FakeRights implements PlayerRights, PlayerMeta {

        private final Set<String> nodes = new HashSet<>();
        private final Map<String, String> values = new HashMap<>();

        public void allow(UUID player, String node) {
            nodes.add(player + "|" + node);
        }

        void meta(UUID player, String key, int value) {
            values.put(player + "|" + key, String.valueOf(value));
        }

        @Override
        public boolean has(UUID player, String node) {
            return nodes.contains(player + "|" + node);
        }

        @Override
        public OptionalInt number(UUID player, String key) {
            String raw = values.get(player + "|" + key);
            return raw == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(raw));
        }

        @Override
        public String value(UUID player, String key, String fallback) {
            String raw = values.get(player + "|" + key);
            return raw == null ? fallback : raw;
        }
    }

    public static final class MemoryWriter implements SingleWriter {

        private EssentialsState state = EssentialsState.empty();

        StoreResult answer = StoreResult.success();

        @Override
        public EssentialsState state() {
            return state;
        }

        @Override
        public StoreResult commit(EssentialsState next, ChangeBatch batch) {
            if (!answer.successful()) {
                return answer;
            }
            state = next;
            return answer;
        }

        @Override
        public void flush() {}
    }

    public static final class FakeMover implements Mover {

        private final List<Held> held = new ArrayList<>();

        private boolean hold;
        private CancelReason failure;
        private int moves;

        void holdOn() {
            hold = true;
        }

        void failWith(CancelReason reason) {
            failure = reason;
        }

        int moves() {
            return moves;
        }

        void land() {
            for (Held pending : new ArrayList<>(held)) {
                held.remove(pending);
                pending.report.done(pending.landing);
            }
        }

        void fail(CancelReason reason) {
            for (Held pending : new ArrayList<>(held)) {
                held.remove(pending);
                pending.report.failed(reason);
            }
        }

        @Override
        public void move(TeleportJob job, Point landing, Report report) {
            moves++;
            if (failure != null) {
                report.failed(failure);
                return;
            }
            if (hold) {
                held.add(new Held(landing, report));
                return;
            }
            report.done(landing);
        }

        private static final class Held {

            private final Point landing;
            private final Report report;

            Held(Point landing, Report report) {
                this.landing = landing;
                this.report = report;
            }
        }
    }

    public static final class TestScheduler implements Scheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {
            queued.add(task);
        }

        int pending() {
            return queued.size();
        }

        void drain() {
            List<Runnable> taken = new ArrayList<>(queued);
            queued.clear();
            for (Runnable task : taken) {
                task.run();
            }
        }
    }

    public static final class FakeEvents implements TeleportEvents {

        private final List<Listener> listeners = new ArrayList<>();

        @Override
        public void register(int priority, Listener listener) {
            listeners.add(listener);
        }

        @Override
        public void unregister(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public List<Listener> listeners() {
            return listeners;
        }
    }

    public static final class Watcher implements TeleportEvents.Listener {

        private final EssentialsEvents.Kind kind;
        private final List<TeleportJob> started = new ArrayList<>();
        private final List<TeleportJob> moved = new ArrayList<>();
        private final List<TeleportJob> cancelled = new ArrayList<>();

        private EssentialsEvents.Decision beforeStart = EssentialsEvents.Decision.allow();
        private EssentialsEvents.Decision beforeMove = EssentialsEvents.Decision.allow();
        private RuntimeException failure;

        Watcher(EssentialsEvents.Kind kind) {
            this.kind = kind;
        }

        void denyStart(String reason) {
            beforeStart = EssentialsEvents.Decision.deny(reason);
        }

        void denyMove(String reason) {
            beforeMove = EssentialsEvents.Decision.deny(reason);
        }

        void breakWith(RuntimeException value) {
            failure = value;
        }

        List<TeleportJob> started() {
            return started;
        }

        List<TeleportJob> moved() {
            return moved;
        }

        List<TeleportJob> cancelled() {
            return cancelled;
        }

        @Override
        public EssentialsEvents.Kind kind() {
            return kind;
        }

        @Override
        public EssentialsEvents.Decision beforeStart(TeleportJob job) {
            started.add(job);
            if (failure != null) {
                throw failure;
            }
            return beforeStart;
        }

        @Override
        public EssentialsEvents.Decision beforeMove(TeleportJob job) {
            if (failure != null) {
                throw failure;
            }
            return beforeMove;
        }

        @Override
        public void afterMove(TeleportJob job) {
            moved.add(job);
        }

        @Override
        public void cancelled(TeleportJob job) {
            cancelled.add(job);
        }
    }
}
