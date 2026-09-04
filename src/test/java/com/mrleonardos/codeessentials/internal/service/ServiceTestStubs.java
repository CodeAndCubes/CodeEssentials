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
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.kits.KitHands;
import com.mrleonardos.codeessentials.internal.kits.KitStacking;

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
            task.run();
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

    /** Планировщик, у которого главный поток это отдельный поток: видно, кто где исполняется. */
    static final class Handoff implements Scheduler {

        final List<Runnable> pending = new ArrayList<>();

        Thread main;

        @Override
        public void onMainThread(Runnable task) {
            Thread worker = new Thread(task, "codeessentials-test-main");
            main = worker;
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            }
        }

        @Override
        public void afterTicks(int ticks, Runnable task) {
            pending.add(task);
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
        final List<Thread> threads = new ArrayList<>();
        EssentialsEvents.Kind kind = EssentialsEvents.Kind.INFORM;
        EssentialsEvents.Decision verdict = EssentialsEvents.Decision.allow();
        boolean throwOnBeforeSet;

        @Override
        public EssentialsEvents.Kind kind() {
            return kind;
        }

        @Override
        public EssentialsEvents.Decision beforeSet(UUID player, HomeRecord home, boolean overwrite) {
            threads.add(Thread.currentThread());
            if (throwOnBeforeSet) {
                throw new IllegalStateException("listener is broken");
            }
            seen.add("before " + home.name());
            return verdict;
        }

        @Override
        public void afterSet(UUID player, HomeRecord home, boolean overwrite) {
            threads.add(Thread.currentThread());
            seen.add("after " + home.name());
        }

        @Override
        public void changed(Set<UUID> players) {
            changes.add(players);
        }
    }

    /** Слоты игрока для доставки китов: worn задаёт тест, dress запоминает результат. */
    static final class Hands implements KitHands {

        Optional<KitItem[]> worn = Optional.empty();
        boolean dressable = true;
        boolean dressed;
        KitItem[] given;

        Hands(Optional<KitItem[]> worn) {
            this.worn = worn;
        }

        @Override
        public Optional<KitItem[]> worn(UUID player) {
            return worn;
        }

        @Override
        public boolean dress(UUID player, KitItem[] slots) {
            if (!dressable) {
                return false;
            }
            dressed = true;
            given = slots;
            return true;
        }

        @Override
        public KitStacking stacking() {
            return new KitStacking() {

                @Override
                public int limit(KitItem item) {
                    boolean onePerStack = item.count() == 1 || item.id()
                        .endsWith("helmet")
                        || item.id()
                            .endsWith("boots");
                    return onePerStack ? 1 : 64;
                }

                @Override
                public boolean merges(KitItem held, KitItem added) {
                    return held.sameKind(added) && limit(held) > 1;
                }

                @Override
                public boolean accepts(int slot, KitItem item) {
                    if (slot < KitDefinition.INVENTORY_SLOTS) {
                        return true;
                    }
                    int armorType = KitDefinition.ARMOR_SLOTS - 1 - (slot - KitDefinition.INVENTORY_SLOTS);
                    boolean helmet = item.id()
                        .endsWith("helmet");
                    boolean boots = item.id()
                        .endsWith("boots");
                    return armorType == 0 && helmet || armorType == 3 && boots;
                }
            };
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
