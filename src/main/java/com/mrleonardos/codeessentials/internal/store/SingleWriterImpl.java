package com.mrleonardos.codeessentials.internal.store;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;

public final class SingleWriterImpl implements SingleWriter {

    public static final long AWAIT_MILLIS = 10_000L;
    public static final String WRITER_THREAD = "codeessentials-writer";

    private final PlayerDataStore builtin;
    private final String configured;
    private final Lookup lookup;
    private final Scheduler scheduler;
    private final Logger log;
    private final LongSupplier clock;
    private final int autosaveTicks;

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ThreadLocal<Boolean> onMainThread = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final LinkedBlockingQueue<Long> saves = new LinkedBlockingQueue<>();

    private volatile EssentialsState current;
    private volatile boolean running;
    private volatile boolean unsupportedTold;
    private volatile long awaitMillis = AWAIT_MILLIS;
    private volatile Thread writer;
    private volatile PlayerDataStore resolved;

    public SingleWriterImpl(PlayerDataStore store, Scheduler scheduler, Logger log, LongSupplier clock,
        int autosaveTicks) {
        this(store, null, null, scheduler, log, clock, autosaveTicks);
    }

    public SingleWriterImpl(PlayerDataStore builtin, String configured, Lookup lookup, Scheduler scheduler, Logger log,
        LongSupplier clock, int autosaveTicks) {
        this.builtin = builtin;
        this.configured = configured;
        this.lookup = lookup;
        this.scheduler = scheduler;
        this.log = log;
        this.clock = clock;
        this.autosaveTicks = Math.max(1, autosaveTicks);
    }

    public static PlayerDataStore resolveProvider(String configured, Lookup lookup, PlayerDataStore builtin,
        Logger log) {
        Optional<PlayerDataStore> foreign = lookup.store(configured);
        if (foreign.isPresent()) {
            return foreign.get();
        }
        if (!JsonPlayerDataStore.ID.equals(configured)) {
            log.warn("Storage provider {} is not registered, falling back to {}", configured, JsonPlayerDataStore.ID);
        }
        return builtin;
    }

    @Override
    public EssentialsState state() {
        EssentialsState known = current;
        if (known != null) {
            return known;
        }
        lock.lock();
        try {
            if (current == null) {
                current = load();
            }
            return current;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StoreResult commit(EssentialsState next, ChangeBatch batch) {
        if (next == null || batch == null) {
            return StoreResult.success();
        }
        if (onMainThread.get()
            .booleanValue()) {
            return apply(next, batch);
        }
        CompletableFuture<StoreResult> done = new CompletableFuture<>();
        AtomicBoolean claimed = new AtomicBoolean();
        scheduler.onMainThread(() -> {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            onMainThread.set(Boolean.TRUE);
            try {
                done.complete(apply(next, batch));
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            } finally {
                onMainThread.set(Boolean.FALSE);
            }
        });
        return await(done, claimed);
    }

    @Override
    public void flush() {
        if (!unsaved()) {
            return;
        }
        saves.offer(Long.valueOf(state().revision()));
        wake();
    }

    public void start() {
        lock.lock();
        try {
            current = load();
            running = true;
        } finally {
            lock.unlock();
        }
        announce(current);
        scheduleAutosave();
    }

    public void stop() {
        running = false;
        flushNow();
        Thread worker;
        synchronized (this) {
            worker = writer;
            writer = null;
            notifyAll();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    public void flushNow() {
        if (unsaved()) {
            write();
        }
    }

    public boolean unsaved() {
        BufferedStore buffered = buffered();
        return buffered != null && buffered.unsaved();
    }

    void awaitMillis(long millis) {
        awaitMillis = millis;
    }

    private EssentialsState load() {
        PlayerDataStore store = provider();
        Map<UUID, PlayerRecord> players = store.loadPlayers();
        Map<UUID, Map<String, Long>> cooldowns = store.loadCooldowns();
        return EssentialsState.of(1L, players, cooldowns)
            .prunedCooldowns(clock.getAsLong());
    }

    private void announce(EssentialsState state) {
        int homes = 0;
        for (PlayerRecord player : state.players()
            .values()) {
            homes += player.homes()
                .size();
        }
        log.info(
            "Player storage runs on {}: {} player(s), {} home(s), {} player(s) on cooldown",
            provider().id(),
            Integer.valueOf(
                state.players()
                    .size()),
            Integer.valueOf(homes),
            Integer.valueOf(
                state.cooldowns()
                    .size()));
    }

    private StoreResult apply(EssentialsState next, ChangeBatch batch) {
        lock.lock();
        try {
            StoreResult stored = provider().apply(batch);
            if (!stored.successful()) {
                if (stored.failure()
                    .get() != StoreResult.Failure.UNSUPPORTED) {
                    log.warn("Provider {} refused the change: {}", provider().id(), stored);
                    return stored;
                }
                tellUnsupported(stored);
            }
            current = next;
            return StoreResult.success();
        } catch (RuntimeException failure) {
            log.error("Change was not applied, the last snapshot stays: {}", failure.toString(), failure);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        } finally {
            lock.unlock();
        }
    }

    private void tellUnsupported(StoreResult stored) {
        if (!unsupportedTold) {
            unsupportedTold = true;
            log.warn(
                "Provider {} does not keep part of the state ({}), the mod runs on its own snapshot",
                provider().id(),
                stored.message()
                    .orElse("no reason given"));
        }
    }

    private PlayerDataStore provider() {
        PlayerDataStore known = resolved;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (resolved == null) {
                resolved = lookup == null ? builtin : resolveProvider(configured, lookup, builtin, log);
            }
            return resolved;
        }
    }

    private BufferedStore buffered() {
        PlayerDataStore store = provider();
        return store instanceof BufferedStore ? (BufferedStore) store : null;
    }

    private StoreResult write() {
        BufferedStore buffered = buffered();
        if (buffered == null) {
            return StoreResult.success();
        }
        writeLock.lock();
        try {
            StoreResult written = buffered.flush();
            if (!written.successful()) {
                log.error("Player state was not saved: {}", written);
            }
            return written;
        } finally {
            writeLock.unlock();
        }
    }

    private StoreResult await(CompletableFuture<StoreResult> done, AtomicBoolean claimed) {
        try {
            return done.get(awaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            return abandon(done, claimed, "interrupted while waiting");
        } catch (TimeoutException timeout) {
            return abandon(done, claimed, "the main thread did not take the change within " + awaitMillis + " ms");
        } catch (Exception failure) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        }
    }

    private StoreResult abandon(CompletableFuture<StoreResult> done, AtomicBoolean claimed, String reason) {
        if (claimed.compareAndSet(false, true)) {
            log.warn("Change was dropped from the queue: {}", reason);
            return StoreResult.failure(StoreResult.Failure.TIMEOUT, reason);
        }
        try {
            return done.get(awaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "interrupted while waiting");
        } catch (Exception failure) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        }
    }

    private void scheduleAutosave() {
        if (running) {
            scheduler.afterTicks(autosaveTicks, this::autosave);
        }
    }

    private void autosave() {
        try {
            flush();
        } finally {
            scheduleAutosave();
        }
    }

    private void wake() {
        synchronized (this) {
            if (writer == null) {
                Thread worker = new Thread(this::drain, WRITER_THREAD);
                worker.setDaemon(true);
                writer = worker;
                worker.start();
            }
            notifyAll();
        }
    }

    private void drain() {
        while (running || !saves.isEmpty()) {
            Long pending;
            synchronized (this) {
                while (saves.isEmpty() && running) {
                    try {
                        wait(1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread()
                            .interrupt();
                        return;
                    }
                }
                pending = saves.poll();
            }
            if (pending != null) {
                write();
            }
        }
    }

    public interface Lookup {

        Optional<PlayerDataStore> store(String id);
    }
}
