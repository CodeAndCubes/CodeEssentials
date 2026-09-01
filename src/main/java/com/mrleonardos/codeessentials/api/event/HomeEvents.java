package com.mrleonardos.codeessentials.api.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.HomeRecord;

/**
 * Реестр слушателей домов.
 *
 * <p>
 * Через вето на {@code beforeSet} приват-мод отказывает дому на чужой земле, ничего не требуя от
 * CodeEssentials взамен. Уведомление {@code changed} собирается за тик: пять правок одного игрока
 * дают одно событие с одним идентификатором.
 */
public interface HomeEvents {

    /** Наблюдатель домов. */
    interface Listener {

        /** Чем слушатель занят: решает или смотрит. */
        EssentialsEvents.Kind kind();

        /**
         * Перед записью дома.
         *
         * @param overwrite правда, если дом с таким именем уже был и переезжает на новую точку
         */
        default EssentialsEvents.Decision beforeSet(UUID player, HomeRecord home, boolean overwrite) {
            return EssentialsEvents.Decision.allow();
        }

        /** Дом записан. */
        default void afterSet(UUID player, HomeRecord home, boolean overwrite) {}

        /** Дома этих игроков поменялись за прошедший тик. */
        default void changed(Set<UUID> players) {}
    }

    /**
     * Добавить слушателя.
     *
     * @param priority меньшее число означает более ранний вызов
     */
    void register(int priority, Listener listener);

    /** Убрать слушателя. */
    void unregister(Listener listener);

    /** Слушатели в порядке вызова. */
    List<Listener> listeners();
}
