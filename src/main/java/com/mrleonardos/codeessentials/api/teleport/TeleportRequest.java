package com.mrleonardos.codeessentials.api.teleport;

import java.util.Objects;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.model.Point;

/**
 * Просьба перенести игрока.
 *
 * <p>
 * Просьба неизменяема и говорит только о том, чего хотят: кого, куда и почему. Как это исполнится
 * (сколько ждать, куда поправить точку, брать ли кулдаун) решает движок по причине и настройкам.
 *
 * <p>
 * Автор нужен аудиту: имя игрока, {@code console}, {@code commandblock@x,y,z} или идентификатор
 * чужого мода. Без него в журнале админских переносов нет ответа на вопрос, кто двигал игрока.
 */
public final class TeleportRequest {

    /** Задержку берём из настроек и меты игрока. */
    public static final int WARMUP_FROM_SETTINGS = -1;

    private final UUID player;
    private final Point destination;
    private final TeleportCause cause;
    private final int warmupSeconds;
    private final boolean safeSpot;
    private final String actor;

    private TeleportRequest(UUID player, Point destination, TeleportCause cause, int warmupSeconds, boolean safeSpot,
        String actor) {
        this.player = player;
        this.destination = destination;
        this.cause = cause;
        this.warmupSeconds = warmupSeconds;
        this.safeSpot = safeSpot;
        this.actor = actor;
    }

    /** Начать собирать просьбу. */
    public static Builder builder(UUID player, Point destination, TeleportCause cause) {
        return new Builder(player, destination, cause);
    }

    /** Кого несём. */
    public UUID player() {
        return player;
    }

    /** Куда просят. Движок вправе поправить точку поиском безопасного места. */
    public Point destination() {
        return destination;
    }

    /** Почему несём. */
    public TeleportCause cause() {
        return cause;
    }

    /** Задержка в секундах или {@link #WARMUP_FROM_SETTINGS}, если её берут из настроек. */
    public int warmupSeconds() {
        return warmupSeconds;
    }

    /** Искать ли безопасное место. Выключается только флагом {@code --force} админских команд. */
    public boolean safeSpot() {
        return safeSpot;
    }

    /** Кто просил: имя игрока, {@code console}, {@code commandblock@x,y,z} или имя мода. */
    public String actor() {
        return actor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeleportRequest)) {
            return false;
        }
        TeleportRequest that = (TeleportRequest) other;
        return warmupSeconds == that.warmupSeconds && safeSpot == that.safeSpot
            && player.equals(that.player)
            && destination.equals(that.destination)
            && cause == that.cause
            && actor.equals(that.actor);
    }

    @Override
    public int hashCode() {
        return ((((player.hashCode() * 31 + destination.hashCode()) * 31 + cause.hashCode()) * 31 + warmupSeconds) * 31
            + Boolean.hashCode(safeSpot)) * 31 + actor.hashCode();
    }

    @Override
    public String toString() {
        return cause + " " + player + " -> " + destination.print() + " by " + actor;
    }

    /** Сборщик просьбы. */
    public static final class Builder {

        private final UUID player;
        private final Point destination;
        private final TeleportCause cause;

        private int warmupSeconds = WARMUP_FROM_SETTINGS;
        private boolean safeSpot = true;
        private String actor;

        private Builder(UUID player, Point destination, TeleportCause cause) {
            this.player = Objects.requireNonNull(player, "player");
            this.destination = Objects.requireNonNull(destination, "destination");
            this.cause = Objects.requireNonNull(cause, "cause");
            this.actor = player.toString();
        }

        /**
         * Своя задержка вместо настроек.
         *
         * @throws IllegalArgumentException если значение меньше {@link #WARMUP_FROM_SETTINGS} или выше
         *                                  заводского потолка
         */
        public Builder warmupSeconds(int value) {
            if (value < WARMUP_FROM_SETTINGS || value > EssentialsLimits.DEFAULT_WARMUP_SECONDS) {
                throw new IllegalArgumentException(
                    "Warmup must be " + WARMUP_FROM_SETTINGS
                        + " or fit 0.."
                        + EssentialsLimits.DEFAULT_WARMUP_SECONDS
                        + ": "
                        + value);
            }
            warmupSeconds = value;
            return this;
        }

        /** Искать ли безопасное место. Выключается флагом {@code --force} админских команд. */
        public Builder safeSpot(boolean value) {
            safeSpot = value;
            return this;
        }

        /** Кто просил, для аудита. */
        public Builder actor(String value) {
            actor = Objects.requireNonNull(value, "actor");
            return this;
        }

        /** Готовая просьба. */
        public TeleportRequest build() {
            return new TeleportRequest(player, destination, cause, warmupSeconds, safeSpot, actor);
        }
    }
}
