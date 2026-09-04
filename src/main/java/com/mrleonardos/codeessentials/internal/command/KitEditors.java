package com.mrleonardos.codeessentials.internal.command;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;

public interface KitEditors {

    /**
     * Открыть редактор кита.
     *
     * @return ложь, когда админа нет на сервере: экран некому показать
     */
    boolean edit(UUID admin, KitDefinition kit);

    /** Слоты админа в порядке кита: кандидат для снимка. Пустой ответ, когда админа нет на сервере. */
    Optional<KitItem[]> capture(UUID admin);

    /** Закрыть все редакторы и записать содержимое. Зовётся на остановке сервера. */
    void closeAll();
}
