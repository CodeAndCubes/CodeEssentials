package com.mrleonardos.codeessentials.api.teleport;

import java.util.Locale;

/**
 * Почему перенос не состоялся.
 *
 * <p>
 * Причина сама говорит, чем закончилась работа: помеха от игрока или мода даёт {@code CANCELLED},
 * помеха от мира даёт {@code FAILED}. Разводить это соглашением в движке нельзя, иначе один и тот
 * же отказ рано или поздно доедет до игрока двумя разными состояниями.
 *
 * <p>
 * Любая причина отсюда означает одно и то же: игрок не сдвинулся, кулдаун не списан, стек возврата
 * не тронут.
 */
public enum CancelReason {

    /** Игрок попросил новый перенос, старая работа уступила место. */
    SUPERSEDED(TeleportJob.State.CANCELLED),

    /** Игрок отошёл дальше разрешённого радиуса за время задержки. */
    MOVED(TeleportJob.State.CANCELLED),

    /** Игрок получил урон за время задержки. */
    DAMAGED(TeleportJob.State.CANCELLED),

    /** Игрок погиб. */
    DEAD(TeleportJob.State.CANCELLED),

    /** Игрок вышел с сервера. */
    DISCONNECTED(TeleportJob.State.CANCELLED),

    /** Слушатель с видом {@code ENFORCE} не пустил перенос. */
    VETOED(TeleportJob.State.CANCELLED),

    /** Работу сняли командой {@code /ecancel}. */
    BY_COMMAND(TeleportJob.State.CANCELLED),

    /**
     * Кулдаун причины ещё не истёк.
     *
     * <p>
     * Отказ принимает сам движок, поэтому он одинаков для команды игрока, принятой tpa-просьбы и
     * прямого вызова из чужого мода. Нода {@code codeessentials.bypass.cooldown} снимает его.
     */
    COOLDOWN(TeleportJob.State.CANCELLED),

    /** Рядом с целью нет безопасного места. */
    UNSAFE(TeleportJob.State.FAILED),

    /** Чанк цели не загружен, а генерация выключена. */
    CHUNK_MISSING(TeleportJob.State.FAILED),

    /** Измерение цели не поднялось. */
    DIMENSION_MISSING(TeleportJob.State.FAILED),

    /** Работа не вышла из {@code MOVING} за отведённые тики и освободила слот игрока. */
    TIMEOUT(TeleportJob.State.FAILED);

    private final TeleportJob.State state;

    CancelReason(TeleportJob.State state) {
        this.state = state;
    }

    /** Каким состоянием заканчивается работа с этой причиной. */
    public TeleportJob.State state() {
        return state;
    }

    /** Правда ли причина от мира, а не от игрока или мода. */
    public boolean failure() {
        return state == TeleportJob.State.FAILED;
    }

    /** Ключ причины для ключей перевода: имя в нижнем регистре. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
