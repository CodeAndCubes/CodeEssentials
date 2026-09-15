package com.mrleonardos.codeessentials.internal.command;

import com.mrleonardos.codeessentials.api.store.StoreResult;

public interface EssentialsMaintenance {

    StoreResult reloadSettings();

    /** Работает ли хранилище состояния игроков. Ложь значит, что команды состояния отвечают «выключено». */
    boolean stateOn();
}
