package com.mrleonardos.codeessentials.internal.engine;

import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

public final class Cooldowns {

    public static final String BYPASS_NODE = "codeessentials.bypass.cooldown";

    private final SingleWriter writer;
    private final PlayerRights rights;
    private final Supplier<EngineRules> rules;
    private final LongSupplier clock;

    public Cooldowns(SingleWriter writer, PlayerRights rights, Supplier<EngineRules> rules, LongSupplier clock) {
        this.writer = writer;
        this.rights = rights;
        this.rules = rules;
        this.clock = clock;
    }

    public boolean bypasses(UUID player) {
        return rights.has(player, BYPASS_NODE);
    }

    public long remaining(UUID player, TeleportCause cause) {
        if (!cause.chargesCooldown()) {
            return 0L;
        }
        return left(player, cause.key());
    }

    public long remainingRequest(UUID player) {
        return left(player, PlayerDataStore.REQUEST_COOLDOWN_KEY);
    }

    public StoreResult charge(UUID player, TeleportCause cause) {
        if (!cause.chargesCooldown()) {
            return StoreResult.success();
        }
        return write(
            player,
            cause.key(),
            rules.get()
                .cooldownSeconds(cause));
    }

    public StoreResult chargeRequest(UUID player) {
        return write(
            player,
            PlayerDataStore.REQUEST_COOLDOWN_KEY,
            rules.get()
                .requestRateSeconds());
    }

    public StoreResult clear(UUID player) {
        EssentialsState state = writer.state();
        Map<String, Long> stamps = state.cooldowns()
            .get(player);
        if (stamps == null || stamps.isEmpty()) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, player.toString());
        }
        return writer.commit(
            state.withoutCooldowns(player),
            ChangeBatch.builder(SingleWriter.AUTHOR)
                .clearCooldowns(player)
                .build());
    }

    private long left(UUID player, String key) {
        if (bypasses(player)) {
            return 0L;
        }
        long expiresAt = writer.state()
            .cooldown(player, key);
        return Math.max(0L, expiresAt - clock.getAsLong());
    }

    private StoreResult write(UUID player, String key, int seconds) {
        if (seconds <= 0 || bypasses(player)) {
            return StoreResult.success();
        }
        long expiresAt = clock.getAsLong() + EngineRules.millis(seconds);
        EssentialsState state = writer.state();
        return writer.commit(
            state.withCooldown(player, key, expiresAt),
            ChangeBatch.builder(SingleWriter.AUTHOR)
                .setCooldown(player, key, expiresAt)
                .build());
    }
}
