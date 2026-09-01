package com.mrleonardos.codeessentials.api.model;

import java.util.Objects;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Дом игрока: имя, точка и время установки.
 *
 * <p>
 * Имя приходит уже приведённым к нижнему регистру: его приводит разбор аргумента команды, а запись
 * только проверяет. Одно место приведения важнее удобства, потому что из имени строятся ключи
 * хранилища и подсказки, и {@code Home} рядом с {@code home} завёл бы второй дом на то же место.
 */
public final class HomeRecord {

    private final String name;
    private final Point point;
    private final long createdAt;

    private HomeRecord(String name, Point point, long createdAt) {
        this.name = name;
        this.point = point;
        this.createdAt = createdAt;
    }

    /**
     * Собрать дом.
     *
     * @param name      имя в нижнем регистре по шаблону {@link EssentialsLimits#NAME_PATTERN}
     * @param createdAt время установки в epoch millis, ноль означает «неизвестно»
     * @throws IllegalArgumentException если имя не проходит шаблон или время отрицательное
     */
    public static HomeRecord of(String name, Point point, long createdAt) {
        Objects.requireNonNull(point, "point");
        if (createdAt < 0L) {
            throw new IllegalArgumentException("Home creation time must not be negative: " + createdAt);
        }
        return new HomeRecord(EssentialsLimits.checkedName(name, "home name"), point, createdAt);
    }

    /** Имя в нижнем регистре. */
    public String name() {
        return name;
    }

    /** Точка дома. */
    public Point point() {
        return point;
    }

    /** Время установки в epoch millis, ноль означает «неизвестно». */
    public long createdAt() {
        return createdAt;
    }

    /** Тот же дом на другой точке: так работает перезапись существующего имени. */
    public HomeRecord movedTo(Point newPoint, long movedAt) {
        return of(name, newPoint, movedAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HomeRecord)) {
            return false;
        }
        HomeRecord that = (HomeRecord) other;
        return createdAt == that.createdAt && name.equals(that.name) && point.equals(that.point);
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + point.hashCode()) * 31 + Long.hashCode(createdAt);
    }

    @Override
    public String toString() {
        return name + " " + point.print();
    }
}
