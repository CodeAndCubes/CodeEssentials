package com.mrleonardos.codeessentials.internal.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;

public final class MemoryPlayerDataStore implements PlayerDataStore, BufferedStore {

    public static final String ID = "memory";

    private final Medium medium;
    private final LongSupplier clock;

    private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();
    private final Set<String> appliedOn = Collections.synchronizedSet(new LinkedHashSet<String>());

    private boolean dirty;
    private boolean refuseFlush;
    private StoreResult refusal;

    public MemoryPlayerDataStore(Medium medium, LongSupplier clock) {
        this.medium = medium;
        this.clock = clock;
    }

    public void refuseFlush(boolean value) {
        refuseFlush = value;
    }

    public void refuseApply(StoreResult value) {
        refusal = value;
    }

    public Set<String> appliedOn() {
        return appliedOn;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Map<UUID, PlayerRecord> loadPlayers() {
        players.clear();
        players.putAll(medium.players);
        return new LinkedHashMap<>(players);
    }

    @Override
    public Map<UUID, Map<String, Long>> loadCooldowns() {
        cooldowns.clear();
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Map<String, Long>> entry : medium.cooldowns.entrySet()) {
            Map<String, Long> stamps = new TreeMap<>();
            for (Map.Entry<String, Long> stamp : entry.getValue()
                .entrySet()) {
                if (stamp.getValue()
                    .longValue() > now) {
                    stamps.put(stamp.getKey(), stamp.getValue());
                }
            }
            if (!stamps.isEmpty()) {
                cooldowns.put(entry.getKey(), stamps);
            }
        }
        return copy(cooldowns);
    }

    @Override
    public StoreResult apply(ChangeBatch batch) {
        if (refusal != null) {
            return refusal;
        }
        appliedOn.add(
            Thread.currentThread()
                .getName());
        for (ChangeBatch.Change change : batch.changes()) {
            switch (change.kind()) {
                case UPSERT_PLAYER:
                    players.put(
                        change.player(),
                        change.record()
                            .get());
                    break;
                case REMOVE_PLAYER:
                    players.remove(change.player());
                    break;
                case SET_COOLDOWN:
                    cooldowns.computeIfAbsent(change.player(), key -> new TreeMap<String, Long>())
                        .put(
                            change.cooldownKey()
                                .get(),
                            Long.valueOf(change.expiresAt()));
                    break;
                default:
                    cooldowns.remove(change.player());
                    break;
            }
        }
        dirty = true;
        return StoreResult.success();
    }

    @Override
    public boolean unsaved() {
        return dirty;
    }

    @Override
    public StoreResult flush() {
        if (refuseFlush) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "the medium is gone");
        }
        medium.players.clear();
        medium.players.putAll(players);
        medium.cooldowns.clear();
        medium.cooldowns.putAll(copy(cooldowns));
        dirty = false;
        return StoreResult.success();
    }

    private static Map<UUID, Map<String, Long>> copy(Map<UUID, Map<String, Long>> source) {
        Map<UUID, Map<String, Long>> copied = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Long>> entry : source.entrySet()) {
            copied.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        return copied;
    }

    public static final class Medium {

        private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();
    }
}
