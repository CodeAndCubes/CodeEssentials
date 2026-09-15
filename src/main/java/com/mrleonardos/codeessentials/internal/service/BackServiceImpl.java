package com.mrleonardos.codeessentials.internal.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

public final class BackServiceImpl implements BackService {

    private static final String ACTOR = "codeessentials";

    private final Supplier<EssentialsSettings> settings;
    private final PlayerMeta meta;
    private final PlayerStateWriter state;
    private final Logger log;

    public BackServiceImpl(Supplier<EssentialsSettings> settings, PlayerMeta meta, PlayerStateWriter state,
        Logger log) {
        this.settings = settings;
        this.meta = meta;
        this.state = state;
        this.log = log;
    }

    @Override
    public Optional<BackPoint> peek(UUID player) {
        return stateOf(player).topBack();
    }

    @Override
    public List<BackPoint> stack(UUID player) {
        return stateOf(player).back();
    }

    @Override
    public int depthFor(UUID player) {
        EssentialsSettings current = settings.get();
        EssentialsLimits ceilings = current.ceilings();
        int fallback = current.defaultBackDepth(ceilings);
        String raw = meta.value(player, EssentialsSettings.META_BACK_DEPTH, null);
        if (raw == null || raw.trim()
            .isEmpty()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            log.warn("Meta {} of {} is not a number: {}", EssentialsSettings.META_BACK_DEPTH, player, raw);
            return fallback;
        }
        if (parsed < EssentialsLimits.MIN_BACK_DEPTH || parsed > ceilings.backDepth()) {
            log.warn(
                "Meta {} of {} is out of {}..{}: {}",
                EssentialsSettings.META_BACK_DEPTH,
                player,
                Integer.valueOf(EssentialsLimits.MIN_BACK_DEPTH),
                Integer.valueOf(ceilings.backDepth()),
                raw);
            return fallback;
        }
        return parsed;
    }

    @Override
    public StoreResult record(UUID player, BackPoint point) {
        if (player == null || point == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "player and point are required");
        }
        EssentialsSettings current = settings.get();
        boolean wanted = point.fromDeath() ? current.backRecordsDeaths() : current.backRecordsTeleports();
        if (!wanted) {
            return StoreResult.failure(StoreResult.Failure.UNSUPPORTED, "back.on = " + current.backMode());
        }
        PlayerRecord next = stateOf(player).pushBack(point, depthFor(player), current.ceilings());
        return state.commit(
            next,
            ChangeBatch.builder(ACTOR)
                .upsert(next)
                .build());
    }

    @Override
    public boolean pop(UUID player) {
        PlayerRecord held = stateOf(player);
        if (held.back()
            .isEmpty()) {
            return false;
        }
        PlayerRecord next = held.popBack();
        StoreResult written = state.commit(
            next,
            ChangeBatch.builder(ACTOR)
                .upsert(next)
                .build());
        return written.successful();
    }

    private PlayerRecord stateOf(UUID player) {
        return state.player(player)
            .orElseGet(() -> PlayerRecord.empty(player, null));
    }
}
