package com.mrleonardos.codeessentials.internal.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

public final class BackLog implements BackService {

    public static final String DEPTH_META = "codeessentials.backdepth";

    public enum Trigger {

        OFF,
        TELEPORT,
        DEATH,
        BOTH;

        public boolean records(BackPoint.Origin origin) {
            if (this == BOTH) {
                return true;
            }
            return origin == BackPoint.Origin.DEATH ? this == DEATH : this == TELEPORT;
        }
    }

    private final SingleWriter writer;
    private final PlayerRights rights;
    private final Supplier<EngineRules> rules;

    public BackLog(SingleWriter writer, PlayerRights rights, Supplier<EngineRules> rules) {
        this.writer = writer;
        this.rights = rights;
        this.rules = rules;
    }

    @Override
    public Optional<BackPoint> peek(UUID player) {
        return writer.state()
            .player(player)
            .topBack();
    }

    @Override
    public List<BackPoint> stack(UUID player) {
        return writer.state()
            .player(player)
            .back();
    }

    @Override
    public int depthFor(UUID player) {
        OptionalInt asked = rights.number(player, DEPTH_META);
        EngineRules current = rules.get();
        int depth = asked.isPresent() && asked.getAsInt() >= 0 ? asked.getAsInt() : current.backDepth();
        return current.limits()
            .clampBackDepth(depth);
    }

    @Override
    public StoreResult record(UUID player, BackPoint point) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(point, "point");
        Trigger trigger = rules.get()
            .backTrigger();
        if (!trigger.records(point.origin())) {
            return StoreResult.failure(StoreResult.Failure.UNSUPPORTED, "back.on = " + trigger);
        }
        EssentialsState state = writer.state();
        PlayerRecord updated = state.player(player)
            .pushBack(point, depthFor(player));
        return writer.commit(
            state.withPlayer(updated),
            ChangeBatch.builder(SingleWriter.AUTHOR)
                .upsert(updated)
                .build());
    }

    @Override
    public boolean pop(UUID player) {
        EssentialsState state = writer.state();
        PlayerRecord held = state.player(player);
        if (held.back()
            .isEmpty()) {
            return false;
        }
        PlayerRecord updated = held.popBack();
        return writer.commit(
            state.withPlayer(updated),
            ChangeBatch.builder(SingleWriter.AUTHOR)
                .upsert(updated)
                .build())
            .successful();
    }
}
