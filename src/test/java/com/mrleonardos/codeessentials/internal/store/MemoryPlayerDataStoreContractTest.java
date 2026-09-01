package com.mrleonardos.codeessentials.internal.store;

import com.mrleonardos.codeessentials.api.store.PlayerDataStore;

class MemoryPlayerDataStoreContractTest extends PlayerDataStoreContract {

    private final MemoryPlayerDataStore.Medium medium = new MemoryPlayerDataStore.Medium();

    @Override
    protected PlayerDataStore open() {
        return new MemoryPlayerDataStore(medium, clock::get);
    }
}
