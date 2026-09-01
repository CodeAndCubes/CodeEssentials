package com.mrleonardos.codeessentials.api.event;

import java.util.List;

import com.mrleonardos.codeessentials.api.teleport.TeleportJob;

/**
 * Реестр слушателей переноса.
 *
 * <p>
 * Точек две, и они разные. {@code beforeStart} зовут после поиска безопасной точки и до тёплой
 * задержки: там решают, стоит ли вообще начинать. {@code beforeMove} зовут перед самым переносом,
 * туда идут проверки, которым важно состояние мира в последний момент, например приват региона,
 * который мог смениться за три секунды задержки.
 */
public interface TeleportEvents {

    /**
     * Наблюдатель переноса.
     *
     * <p>
     * Ветить умеют только точки до переноса. {@code afterMove} и {@code cancelled} рассказывают,
     * чем всё кончилось: у первой работа в состоянии {@code DONE} с точкой приземления, у второй
     * причина отмены.
     */
    interface Listener {

        /** Чем слушатель занят: решает или смотрит. */
        EssentialsEvents.Kind kind();

        /** Перед тёплой задержкой, безопасная точка уже найдена. */
        default EssentialsEvents.Decision beforeStart(TeleportJob job) {
            return EssentialsEvents.Decision.allow();
        }

        /** Перед самым переносом. */
        default EssentialsEvents.Decision beforeMove(TeleportJob job) {
            return EssentialsEvents.Decision.allow();
        }

        /** Игрок перенесён, работа несёт точку приземления и отчёт о поправке. */
        default void afterMove(TeleportJob job) {}

        /** Перенос не состоялся, работа несёт причину. */
        default void cancelled(TeleportJob job) {}
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
