package com.mrleonardos.codeessentials.api.teleport;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Границы поиска безопасной точки: насколько далеко от просьбы разрешено отойти.
 *
 * <p>
 * Вниз разрешено дальше, чем вверх: подъём на блок игрок замечает и прощает, а падение в пещеру под
 * домом дезориентирует. Кольца по горизонтали двигают именно то место, куда игрок просил, поэтому
 * их радиус самый маленький.
 *
 * <p>
 * {@link #maxProbes()} считает потолок работы: столько клеток поиск проверит в худшем случае. При
 * заводских границах это 1225 проб по уже загруженным чанкам.
 */
public final class SafeSpotLimits {

    /** Сколько блоков вверх разрешено поднять игрока. */
    public static final int DEFAULT_MAX_UP = 8;

    /** Сколько блоков вниз разрешено опустить игрока. */
    public static final int DEFAULT_MAX_DOWN = 16;

    /** Радиус колец по горизонтали в блоках. */
    public static final int DEFAULT_RADIUS = 3;

    /** Дальше высоты мира искать нечего. */
    public static final int MAX_VERTICAL = 256;

    private static final SafeSpotLimits DEFAULTS = new SafeSpotLimits(
        DEFAULT_MAX_UP,
        DEFAULT_MAX_DOWN,
        DEFAULT_RADIUS,
        false);

    private final int maxUp;
    private final int maxDown;
    private final int radius;
    private final boolean liquidOk;

    private SafeSpotLimits(int maxUp, int maxDown, int radius, boolean liquidOk) {
        this.maxUp = maxUp;
        this.maxDown = maxDown;
        this.radius = radius;
        this.liquidOk = liquidOk;
    }

    /** Заводские границы: восемь вверх, шестнадцать вниз, три в стороны, без приземления в жидкость. */
    public static SafeSpotLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Собрать границы из значений конфига. Значения вне разрешённого зажимаются: настройка не должна
     * останавливать сервер поиском в радиусе тысячи блоков.
     */
    public static SafeSpotLimits of(int maxUp, int maxDown, int radius, boolean liquidOk) {
        return new SafeSpotLimits(
            clamp(maxUp),
            clamp(maxDown),
            EssentialsLimits.defaults()
                .clampSafeSpotRadius(radius),
            liquidOk);
    }

    /** Сколько блоков вверх разрешено поднять игрока. */
    public int maxUp() {
        return maxUp;
    }

    /** Сколько блоков вниз разрешено опустить игрока. */
    public int maxDown() {
        return maxDown;
    }

    /** Радиус колец по горизонтали в блоках. */
    public int radius() {
        return radius;
    }

    /** Разрешено ли приземляться ногами в жидкость. */
    public boolean liquidOk() {
        return liquidOk;
    }

    /** Сколько клеток поиск проверит в худшем случае. */
    public int maxProbes() {
        int columns = 2 * radius + 1;
        return (1 + maxUp + maxDown) * columns * columns;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SafeSpotLimits)) {
            return false;
        }
        SafeSpotLimits that = (SafeSpotLimits) other;
        return maxUp == that.maxUp && maxDown == that.maxDown && radius == that.radius && liquidOk == that.liquidOk;
    }

    @Override
    public int hashCode() {
        return ((maxUp * 31 + maxDown) * 31 + radius) * 31 + Boolean.hashCode(liquidOk);
    }

    @Override
    public String toString() {
        return "up " + maxUp + ", down " + maxDown + ", radius " + radius + (liquidOk ? ", liquid ok" : "");
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(value, MAX_VERTICAL));
    }
}
