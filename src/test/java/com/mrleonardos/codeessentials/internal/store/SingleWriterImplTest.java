package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;

class SingleWriterImplTest {

    private static final Logger LOG = LogManager.getLogger(SingleWriterImplTest.class);
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FOREIGN_ID = "sql-test";

    private final AtomicLong clock = new AtomicLong(10_000L);
    private final MemoryPlayerDataStore.Medium medium = new MemoryPlayerDataStore.Medium();

    @Test
    void commitSwapsTheSnapshotAndTheFlushReachesTheMedium() {
        MemoryPlayerDataStore store = store();
        SingleWriterImpl writer = writer(store);

        StoreResult stored = commitSteve(writer);

        assertTrue(stored.successful());
        assertTrue(
            writer.state()
                .players()
                .containsKey(STEVE));
        assertTrue(writer.unsaved());

        writer.flushNow();

        assertFalse(writer.unsaved());
        assertTrue(
            store().loadPlayers()
                .containsKey(STEVE));
    }

    @Test
    void refusedChangeKeepsTheLastSnapshot() {
        MemoryPlayerDataStore store = store();
        store.refuseApply(StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "the disk is gone"));
        SingleWriterImpl writer = writer(store);
        EssentialsState before = writer.state();

        StoreResult stored = commitSteve(writer);

        assertFalse(stored.successful());
        assertSame(before, writer.state());
    }

    @Test
    void providerThatCannotKeepPartOfTheStateStillLetsTheModWork() {
        MemoryPlayerDataStore store = store();
        store.refuseApply(StoreResult.failure(StoreResult.Failure.UNSUPPORTED, "cooldowns are not my business"));
        SingleWriterImpl writer = writer(store);

        StoreResult stored = commitSteve(writer);

        assertTrue(stored.successful(), "отказ UNSUPPORTED обязан оставлять мод на своём снимке");
        assertTrue(
            writer.state()
                .players()
                .containsKey(STEVE));
    }

    @Test
    void throwingProviderKeepsTheLastSnapshotAndNamesTheFailure() {
        ThrowingStore store = new ThrowingStore();
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, clock::get, 20);
        writer.start();
        EssentialsState before = writer.state();

        StoreResult stored = commitSteve(writer);

        assertEquals(
            StoreResult.Failure.PROVIDER_FAILED,
            stored.failure()
                .get());
        assertSame(before, writer.state());
    }

    @Test
    void stopWritesEverythingThatWasNotSavedYet() {
        MemoryPlayerDataStore store = store();
        SingleWriterImpl writer = writer(store);
        commitSteve(writer);

        writer.stop();

        assertFalse(writer.unsaved());
        assertTrue(
            store().loadPlayers()
                .containsKey(STEVE));
    }

    @Test
    void autosaveHandsTheStateToTheBackgroundWriter() throws Exception {
        MemoryPlayerDataStore store = store();
        TestScheduler scheduler = new TestScheduler();
        SingleWriterImpl writer = new SingleWriterImpl(store, scheduler, LOG, clock::get, 20);
        writer.start();
        commitSteve(writer);

        scheduler.drain(1);
        awaitSaved(writer);

        assertFalse(writer.unsaved());
        assertEquals(1, scheduler.queued.size(), "автосейв обязан встать в очередь заново");
        writer.stop();
    }

    @Test
    void failedWriteLeavesTheStateUnsavedAndTheSnapshotWhole() {
        MemoryPlayerDataStore store = store();
        SingleWriterImpl writer = writer(store);
        commitSteve(writer);
        store.refuseFlush(true);

        writer.flushNow();

        assertTrue(writer.unsaved());
        assertTrue(
            writer.state()
                .players()
                .containsKey(STEVE));
    }

    @Test
    void expiredCooldownsDoNotComeBackWithTheWorld() {
        MemoryPlayerDataStore store = store();
        SingleWriterImpl writer = writer(store);
        writer.commit(
            writer.state()
                .withCooldown(STEVE, "home", 12_000L),
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", 12_000L)
                .build());
        writer.stop();

        clock.set(13_000L);
        SingleWriterImpl restarted = writer(store());

        assertEquals(
            0L,
            restarted.state()
                .cooldown(STEVE, "home"));
    }

    @Test
    void commitsFromOtherThreadsAreCarriedToTheMainThread() throws Exception {
        MemoryPlayerDataStore store = store();
        MainThread main = new MainThread();
        SingleWriterImpl writer = new SingleWriterImpl(store, main, LOG, clock::get, 20);
        writer.start();
        int threads = 6;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<StoreResult> answers = Collections.synchronizedList(new ArrayList<>());
        Set<String> applied = Collections.synchronizedSet(new LinkedHashSet<String>());

        for (int index = 0; index < threads; index++) {
            final int base = index;
            pool.submit(() -> {
                try {
                    for (int step = 0; step < perThread; step++) {
                        UUID player = UUID.nameUUIDFromBytes(("p" + base + "_" + step).getBytes());
                        PlayerRecord record = PlayerRecord.empty(player, "p" + base + "_" + step);
                        applied.add(
                            Thread.currentThread()
                                .getName());
                        answers.add(
                            writer.commit(
                                writer.state()
                                    .withPlayer(record),
                                ChangeBatch.builder("test")
                                    .upsert(record)
                                    .build()));
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        main.stop();
        writer.stop();

        assertEquals(threads * perThread, answers.size());
        for (StoreResult answer : answers) {
            assertTrue(answer.successful(), "каждая правка обязана дойти: " + answer);
        }
        assertEquals(
            1,
            store.appliedOn()
                .size(),
            "правки обязаны идти одним потоком, а шли " + store.appliedOn());
        assertEquals(
            MainThread.NAME,
            store.appliedOn()
                .iterator()
                .next());
    }

    @Test
    void changeThatNeverReachesTheMainThreadIsDroppedFromTheQueue() throws Exception {
        MemoryPlayerDataStore store = store();
        DeferredScheduler scheduler = new DeferredScheduler();
        SingleWriterImpl writer = new SingleWriterImpl(store, scheduler, LOG, clock::get, 20);
        writer.start();
        writer.awaitMillis(50L);
        EssentialsState before = writer.state();

        Waiting waiting = new Waiting(writer, before);
        StoreResult stored = waiting.call();

        assertEquals(
            StoreResult.Failure.TIMEOUT,
            stored.failure()
                .get());

        scheduler.runQueued();

        assertSame(before, writer.state());
        assertTrue(
            store().loadPlayers()
                .isEmpty());
    }

    @Test
    void providerIsChosenByName() {
        MemoryPlayerDataStore foreign = new MemoryPlayerDataStore(new MemoryPlayerDataStore.Medium(), clock::get);
        SingleWriterImpl.Lookup lookup = id -> FOREIGN_ID.equals(id) ? Optional.<PlayerDataStore>of(foreign)
            : Optional.<PlayerDataStore>empty();
        MemoryPlayerDataStore builtin = store();

        assertSame(foreign, SingleWriterImpl.resolveProvider(FOREIGN_ID, lookup, builtin, LOG));
        assertSame(builtin, SingleWriterImpl.resolveProvider(JsonPlayerDataStore.ID, lookup, builtin, LOG));
        assertSame(builtin, SingleWriterImpl.resolveProvider("unknown", lookup, builtin, LOG));
    }

    private StoreResult commitSteve(SingleWriterImpl writer) {
        PlayerRecord record = PlayerRecord.empty(STEVE, "Steve");
        return writer.commit(
            writer.state()
                .withPlayer(record),
            ChangeBatch.builder("test")
                .upsert(record)
                .build());
    }

    private MemoryPlayerDataStore store() {
        return new MemoryPlayerDataStore(medium, clock::get);
    }

    private SingleWriterImpl writer(MemoryPlayerDataStore store) {
        SingleWriterImpl writer = new SingleWriterImpl(store, new TestScheduler(), LOG, clock::get, 20);
        writer.start();
        return writer;
    }

    private static void awaitSaved(SingleWriterImpl writer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (writer.unsaved() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static final class Waiting extends Thread {

        private final SingleWriterImpl writer;
        private final EssentialsState before;

        private StoreResult answer;

        Waiting(SingleWriterImpl writer, EssentialsState before) {
            this.writer = writer;
            this.before = before;
        }

        StoreResult call() throws InterruptedException {
            start();
            join(10_000L);
            return answer;
        }

        @Override
        public void run() {
            PlayerRecord record = PlayerRecord.empty(STEVE, "Steve");
            answer = writer.commit(
                before.withPlayer(record),
                ChangeBatch.builder("test")
                    .upsert(record)
                    .build());
        }
    }

    private static final class ThrowingStore implements PlayerDataStore {

        @Override
        public String id() {
            return "broken";
        }

        @Override
        public Map<UUID, PlayerRecord> loadPlayers() {
            return Collections.emptyMap();
        }

        @Override
        public Map<UUID, Map<String, Long>> loadCooldowns() {
            return Collections.emptyMap();
        }

        @Override
        public StoreResult apply(ChangeBatch batch) {
            throw new IllegalStateException("the provider is broken");
        }
    }

    private static final class TestScheduler implements Scheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void onMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {
            queued.add(task);
        }

        void drain(int count) {
            for (int index = 0; index < count && !queued.isEmpty(); index++) {
                queued.remove(0)
                    .run();
            }
        }
    }

    private static final class DeferredScheduler implements Scheduler {

        private final List<Runnable> queued = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onMainThread(Runnable task) {
            queued.add(task);
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}

        void runQueued() {
            List<Runnable> taken;
            synchronized (queued) {
                taken = new ArrayList<>(queued);
                queued.clear();
            }
            for (Runnable task : taken) {
                task.run();
            }
        }
    }

    private static final class MainThread implements Scheduler {

        static final String NAME = "test-main-thread";

        private final LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        private final Thread worker;

        private volatile boolean running = true;

        MainThread() {
            worker = new Thread(this::loop, NAME);
            worker.setDaemon(true);
            worker.start();
        }

        @Override
        public void onMainThread(Runnable task) {
            queued.offer(task);
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {}

        void stop() {
            running = false;
            worker.interrupt();
        }

        private void loop() {
            while (running) {
                try {
                    Runnable task = queued.poll(50L, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }
            }
        }
    }
}
