package com.mrleonardos.codeessentials.internal.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;

public final class EssentialsState {

    private static final EssentialsState EMPTY = new EssentialsState(
        0L,
        Collections.<UUID, PlayerRecord>emptyMap(),
        Collections.<UUID, Map<String, Long>>emptyMap());

    private final long revision;
    private final Map<UUID, PlayerRecord> players;
    private final Map<UUID, Map<String, Long>> cooldowns;

    private EssentialsState(long revision, Map<UUID, PlayerRecord> players, Map<UUID, Map<String, Long>> cooldowns) {
        this.revision = revision;
        this.players = players;
        this.cooldowns = cooldowns;
    }

    public static EssentialsState empty() {
        return EMPTY;
    }

    public static EssentialsState of(long revision, Map<UUID, PlayerRecord> players,
        Map<UUID, Map<String, Long>> cooldowns) {
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Map<UUID, Map<String, Long>> copied = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            if (!entry.getValue()
                .isEmpty()) {
                copied.put(entry.getKey(), unmodifiableStamps(entry.getValue()));
            }
        }
        return new EssentialsState(
            revision,
            Collections.unmodifiableMap(new LinkedHashMap<>(players)),
            Collections.unmodifiableMap(copied));
    }

    public long revision() {
        return revision;
    }

    public Map<UUID, PlayerRecord> players() {
        return players;
    }

    public PlayerRecord player(UUID uuid) {
        PlayerRecord held = players.get(uuid);
        return held == null ? PlayerRecord.empty(uuid, null) : held;
    }

    public Map<UUID, Map<String, Long>> cooldowns() {
        return cooldowns;
    }

    public long cooldown(UUID player, String key) {
        Map<String, Long> stamps = cooldowns.get(player);
        if (stamps == null) {
            return 0L;
        }
        Long expiresAt = stamps.get(key);
        return expiresAt == null ? 0L : expiresAt.longValue();
    }

    public EssentialsState withPlayer(PlayerRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.equals(players.get(record.uuid()))) {
            return this;
        }
        Map<UUID, PlayerRecord> updated = new LinkedHashMap<>(players);
        updated.put(record.uuid(), record);
        return new EssentialsState(revision + 1L, Collections.unmodifiableMap(updated), cooldowns);
    }

    public EssentialsState withoutPlayer(UUID player) {
        if (!players.containsKey(player)) {
            return this;
        }
        Map<UUID, PlayerRecord> updated = new LinkedHashMap<>(players);
        updated.remove(player);
        return new EssentialsState(revision + 1L, Collections.unmodifiableMap(updated), cooldowns);
    }

    public EssentialsState withCooldown(UUID player, String key, long expiresAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        if (expiresAt < 0L) {
            throw new IllegalArgumentException("Cooldown deadline must not be negative: " + expiresAt);
        }
        if (cooldown(player, key) == expiresAt) {
            return this;
        }
        Map<UUID, Map<String, Long>> updated = new LinkedHashMap<>(cooldowns);
        Map<String, Long> stamps = new TreeMap<>(updated.getOrDefault(player, Collections.<String, Long>emptyMap()));
        stamps.put(key, Long.valueOf(expiresAt));
        updated.put(player, unmodifiableStamps(stamps));
        return new EssentialsState(revision + 1L, players, Collections.unmodifiableMap(updated));
    }

    public EssentialsState withoutCooldowns(UUID player) {
        if (!cooldowns.containsKey(player)) {
            return this;
        }
        Map<UUID, Map<String, Long>> updated = new LinkedHashMap<>(cooldowns);
        updated.remove(player);
        return new EssentialsState(revision + 1L, players, Collections.unmodifiableMap(updated));
    }

    public EssentialsState prunedCooldowns(long now) {
        Map<UUID, Map<String, Long>> kept = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            Map<String, Long> stamps = new TreeMap<>();
            for (Map.Entry<String, Long> stamp : entry.getValue()
                .entrySet()) {
                if (stamp.getValue()
                    .longValue() > now) {
                    stamps.put(stamp.getKey(), stamp.getValue());
                } else {
                    changed = true;
                }
            }
            if (!stamps.isEmpty()) {
                kept.put(entry.getKey(), unmodifiableStamps(stamps));
            }
        }
        return changed ? new EssentialsState(revision + 1L, players, Collections.unmodifiableMap(kept)) : this;
    }

    @Override
    public String toString() {
        return "revision " + revision + ", " + players.size() + " player(s), " + cooldowns.size() + " on cooldown";
    }

    private static Map<String, Long> unmodifiableStamps(Map<String, Long> stamps) {
        return Collections.unmodifiableMap(new TreeMap<>(stamps));
    }
}
