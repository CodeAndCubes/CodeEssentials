package com.mrleonardos.codeessentials.internal.service;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

public final class StateWriter implements PlayerStateWriter {

    private final SingleWriter writer;

    public StateWriter(SingleWriter writer) {
        this.writer = writer;
    }

    @Override
    public Optional<PlayerRecord> player(UUID player) {
        return Optional.ofNullable(
            writer.state()
                .players()
                .get(player));
    }

    @Override
    public StoreResult commit(PlayerRecord next, ChangeBatch batch) {
        EssentialsState state = writer.state();
        return writer.commit(state.withPlayer(next), batch);
    }

    public StoreResult remember(UUID player, String name) {
        PlayerRecord held = player(player).orElse(null);
        if (held != null && name.equals(held.name())) {
            return StoreResult.success();
        }
        PlayerRecord next = held == null ? PlayerRecord.empty(player, name) : held.withName(name);
        return commit(
            next,
            ChangeBatch.builder(SingleWriter.AUTHOR)
                .upsert(next)
                .build());
    }
}
