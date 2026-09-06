package com.mrleonardos.codeessentials.internal.command;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;

public interface KitEditors {

    /**
     * Открыть редактор кита.
     *
     * <p>
     * Отказ {@link Opening#UNSHOWABLE} редактор объясняет админу сам: он один знает, какой слот и чем
     * не годится. Команде остаётся промолчать, иначе поверх точной строки ляжет общая.
     */
    Opening edit(UUID admin, KitDefinition kit);

    /** Слоты админа в порядке кита: кандидат для снимка. Пустой ответ, когда админа нет на сервере. */
    Optional<KitItem[]> capture(UUID admin);

    /** Закрыть все редакторы и записать содержимое. Зовётся на остановке сервера. */
    void closeAll();

    /** Чем кончилась попытка открыть экран. */
    enum Opening {

        /** Экран открыт, дальше правит админ. */
        OPENED,

        /** Админа нет на сервере: экран некому показать. */
        OFFLINE,

        /**
         * Кит держит то, чего сундук не покажет: предмет отсутствующего мода или запись длиннее
         * стопки. Открыть такой кит значит потерять эти записи на закрытии, поэтому правится он руками
         * в файле.
         */
        UNSHOWABLE
    }
}
