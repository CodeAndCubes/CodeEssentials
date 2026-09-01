package com.mrleonardos.codeessentials.internal.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

final class ServiceTestStubs {

    private ServiceTestStubs() {}

    static final class State implements PlayerStateWriter {

        final Map<UUID, PlayerRecord> records = new LinkedHashMap<>();
        final List<ChangeBatch> batches = new ArrayList<>();
        StoreResult answer = StoreResult.success();

        @Override
        public Optional<PlayerRecord> player(UUID player) {
            return Optional.ofNullable(records.get(player));
        }

        @Override
        public StoreResult commit(PlayerRecord next, ChangeBatch batch) {
            if (!answer.successful()) {
                return answer;
            }
            records.put(next.uuid(), next);
            batches.add(batch);
            return answer;
        }

    }

    static final class Meta implements PlayerMeta {

        final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public String value(UUID player, String key, String fallback) {
            return values.containsKey(key) ? values.get(key) : fallback;
        }
    }

    static final class Ticks implements Scheduler {

        final List<Runnable> pending = new ArrayList<>();

        @Override
        public void onMainThread(Runnable task) {
            pending.add(task);
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {
            pending.add(task);
        }

        void tick() {
            List<Runnable> due = new ArrayList<>(pending);
            pending.clear();
            for (Runnable task : due) {
                task.run();
            }
        }
    }

    static final class Homes implements HomeEvents {

        final List<Listener> listeners = new ArrayList<>();

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

    static final class Watcher implements HomeEvents.Listener {

        final List<String> seen = new ArrayList<>();
        final List<Set<UUID>> changes = new ArrayList<>();
        EssentialsEvents.Kind kind = EssentialsEvents.Kind.INFORM;
        EssentialsEvents.Decision verdict = EssentialsEvents.Decision.allow();
        boolean throwOnBeforeSet;

        @Override
        public EssentialsEvents.Kind kind() {
            return kind;
        }

        @Override
        public EssentialsEvents.Decision beforeSet(UUID player, HomeRecord home, boolean overwrite) {
            if (throwOnBeforeSet) {
                throw new IllegalStateException("listener is broken");
            }
            seen.add("before " + home.name());
            return verdict;
        }

        @Override
        public void afterSet(UUID player, HomeRecord home, boolean overwrite) {
            seen.add("after " + home.name());
        }

        @Override
        public void changed(Set<UUID> players) {
            changes.add(players);
        }
    }

    static final class Spots implements SpotCheck {

        SafeSpotResult answer;

        Spots(SafeSpotResult answer) {
            this.answer = answer;
        }

        @Override
        public SafeSpotResult check(com.mrleonardos.codeessentials.api.model.Point point) {
            return answer;
        }
    }

    static final class Files<T> implements ConfigFile<T> {

        private T value;
        boolean loaded = true;
        int saves;
        int reloads;
        RuntimeException failOnSave;

        Files(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            if (!loaded) {
                throw new IllegalStateException("not loaded");
            }
            return value;
        }

        @Override
        public boolean loaded() {
            return loaded;
        }

        @Override
        public void save() {
            if (failOnSave != null) {
                throw failOnSave;
            }
            saves++;
        }

        @Override
        public void reload() {
            reloads++;
        }

        @Override
        public Path path() {
            return Paths.get("memory");
        }
    }
}
