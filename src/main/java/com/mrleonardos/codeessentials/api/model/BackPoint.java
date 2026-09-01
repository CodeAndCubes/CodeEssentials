package com.mrleonardos.codeessentials.api.model;

import java.util.Objects;

/**
 * Запись стека возврата: откуда игрока унесло и почему.
 *
 * <p>
 * Точка пишется в момент фактического переноса, а не подачи команды, поэтому она показывает место,
 * где игрок стоял перед самым исчезновением. Место смерти помечено отдельно: возврат к нему просит
 * свою ноду, иначе смерть в чужом бою превращается в бесплатный билет обратно.
 */
public final class BackPoint {

    /** Что унесло игрока с точки. */
    public enum Origin {

        /** Перенос через движок: команда, tpa или чужой мод. */
        TELEPORT,

        /** Смерть, точка это место гибели. */
        DEATH
    }

    private final Point point;
    private final Origin origin;
    private final long recordedAt;

    private BackPoint(Point point, Origin origin, long recordedAt) {
        this.point = point;
        this.origin = origin;
        this.recordedAt = recordedAt;
    }

    /**
     * Собрать запись.
     *
     * @param recordedAt время записи в epoch millis, ноль означает «неизвестно»
     * @throws IllegalArgumentException если время отрицательное
     */
    public static BackPoint of(Point point, Origin origin, long recordedAt) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(origin, "origin");
        if (recordedAt < 0L) {
            throw new IllegalArgumentException("Back point time must not be negative: " + recordedAt);
        }
        return new BackPoint(point, origin, recordedAt);
    }

    /** Точка возврата. */
    public Point point() {
        return point;
    }

    /** Что унесло игрока с точки. */
    public Origin origin() {
        return origin;
    }

    /** Время записи в epoch millis, ноль означает «неизвестно». */
    public long recordedAt() {
        return recordedAt;
    }

    /** Правда ли это место смерти: возврат к нему просит ноду {@code codeessentials.back.death}. */
    public boolean fromDeath() {
        return origin == Origin.DEATH;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BackPoint)) {
            return false;
        }
        BackPoint that = (BackPoint) other;
        return recordedAt == that.recordedAt && origin == that.origin && point.equals(that.point);
    }

    @Override
    public int hashCode() {
        return (point.hashCode() * 31 + origin.hashCode()) * 31 + Long.hashCode(recordedAt);
    }

    @Override
    public String toString() {
        return origin + " " + point.print();
    }
}
