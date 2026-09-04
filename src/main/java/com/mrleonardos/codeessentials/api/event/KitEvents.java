package com.mrleonardos.codeessentials.api.event;

import java.util.List;
import java.util.UUID;

/**
 * Реестр слушателей китов.
 *
 * <p>
 * Вето {@code beforeClaim} стоит на выдаче нового получения: до отметки одноразовости, до кулдауна и
 * до всякой доставки. Оно не держит долг: предметы, которые игрок уже получил обещанием, доедут и под
 * вето, поэтому снятое право не отбирает выданное.
 */
public interface KitEvents {

    /**
     * Наблюдатель китов.
     */
    interface Listener {

        /** Чем слушатель занят: решает или смотрит. */
        EssentialsEvents.Kind kind();

        /**
         * Перед выдачей нового получения, своего или выданного админом.
         *
         * @param player кому выдаётся кит
         * @param kit    имя кита в нижнем регистре
         */
        default EssentialsEvents.Decision beforeClaim(UUID player, String kit) {
            return EssentialsEvents.Decision.allow();
        }

        /**
         * Получение состоялось: предметы разложены или отложены в буфер.
         *
         * @param delivered сколько предметов доехало до инвентаря
         * @param buffered  сколько предметов нового получения легло в буфер
         */
        default void afterClaim(UUID player, String kit, int delivered, int buffered) {}
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
