package com.mrleonardos.codeessentials.api.teleport;

import java.util.Locale;

/**
 * Откуда взялась просьба о переносе.
 *
 * <p>
 * Причина решает три вещи сразу: ждать ли тёплую задержку, списывать ли кулдаун и писать ли точку
 * отправления в стек возврата. Держать эти ответы при причине, а не при каждой команде, дешевле:
 * новый вход в движок не забудет ни один из них.
 */
public enum TeleportCause {

    /** Дом игрока по {@code /home}. */
    HOME(true, true, true),

    /** Спавн по {@code /spawn}. */
    SPAWN(true, true, true),

    /** Варп по {@code /warp}. */
    WARP(true, true, true),

    /** Возврат по {@code /back}. Сам в стек не пишет, иначе {@code /back} зацикливается. */
    BACK(true, true, false),

    /** Принятый tpa-запрос. */
    TPA(true, true, true),

    /** Точка после смерти. Задержки нет, кулдауна нет, стек уже получил место гибели. */
    RESPAWN(false, false, false),

    /** Административный перенос {@code /tp} и {@code /tppos}: сразу и бесплатно. */
    ADMIN(false, false, true),

    /** Просьба чужого мода. Идёт тем же конвейером, что и команда игрока. */
    API(true, true, true);

    private final boolean warmsUp;
    private final boolean chargesCooldown;
    private final boolean recordsBack;

    TeleportCause(boolean warmsUp, boolean chargesCooldown, boolean recordsBack) {
        this.warmsUp = warmsUp;
        this.chargesCooldown = chargesCooldown;
        this.recordsBack = recordsBack;
    }

    /** Ключ причины для кулдаунов и ключей перевода: имя в нижнем регистре. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Ждёт ли перенос тёплую задержку. */
    public boolean warmsUp() {
        return warmsUp;
    }

    /** Списывается ли кулдаун причины после фактического переноса. */
    public boolean chargesCooldown() {
        return chargesCooldown;
    }

    /** Пишется ли точка отправления в стек возврата. */
    public boolean recordsBack() {
        return recordsBack;
    }
}
