package com.mrleonardos.codeessentials.internal.store;

import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;

public interface SingleWriter {

    String AUTHOR = JsonPlayerDataStore.MODID;

    EssentialsState state();

    StoreResult commit(EssentialsState next, ChangeBatch batch);

    void flush();

    /**
     * Работает ли хранилище. Ложь значит, что имя провайдера из конфига не зарегистрировано и состояние мода выключено.
     */
    boolean working();
}
