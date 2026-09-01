package com.mrleonardos.codeessentials.internal.service;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;

public interface PlayerStateWriter {

    Optional<PlayerRecord> player(UUID player);

    StoreResult commit(PlayerRecord next, ChangeBatch batch);
}
