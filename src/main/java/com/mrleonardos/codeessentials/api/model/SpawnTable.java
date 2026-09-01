package com.mrleonardos.codeessentials.api.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Точки спавна сервера: общая и по измерениям.
 *
 * <p>
 * Выбор идёт от частного к общему: точка текущего измерения, затем общая. Пустой ответ означает,
 * что мод в выбор не вмешивается и работает ванильная точка мира.
 */
public final class SpawnTable {

    private static final SpawnTable EMPTY = new SpawnTable(null, Collections.<Integer, Point>emptyMap());

    private final Point global;
    private final Map<Integer, Point> byDimension;

    private SpawnTable(Point global, Map<Integer, Point> byDimension) {
        this.global = global;
        this.byDimension = byDimension;
    }

    /**
     * Собрать таблицу.
     *
     * @param global      общая точка, допускается пустой ответ
     * @param byDimension точки по идентификаторам измерений
     */
    public static SpawnTable of(Point global, Map<Integer, Point> byDimension) {
        Objects.requireNonNull(byDimension, "byDimension");
        return new SpawnTable(global, Collections.unmodifiableMap(new TreeMap<>(byDimension)));
    }

    /** Таблица без единой точки: спавн отдан ванили. */
    public static SpawnTable empty() {
        return EMPTY;
    }

    /** Общая точка спавна. */
    public Optional<Point> global() {
        return Optional.ofNullable(global);
    }

    /** Точки по измерениям, порядок по идентификатору измерения. */
    public Map<Integer, Point> byDimension() {
        return byDimension;
    }

    /**
     * Точка для измерения по политике выбора.
     *
     * @return точка измерения, при её отсутствии общая, иначе пустой ответ
     */
    public Optional<Point> pointFor(int dimension) {
        Point own = byDimension.get(Integer.valueOf(dimension));
        return own == null ? global() : Optional.of(own);
    }

    /** Правда ли таблица пуста: подмена респауна работает только при заданной точке. */
    public boolean isEmpty() {
        return global == null && byDimension.isEmpty();
    }

    /** Та же таблица с новой общей точкой. */
    public SpawnTable withGlobal(Point point) {
        Objects.requireNonNull(point, "point");
        return new SpawnTable(point, byDimension);
    }

    /** Та же таблица с точкой для измерения этой точки. */
    public SpawnTable withDimension(Point point) {
        Objects.requireNonNull(point, "point");
        Map<Integer, Point> updated = new TreeMap<>(byDimension);
        updated.put(Integer.valueOf(point.dimension()), point);
        return new SpawnTable(global, Collections.unmodifiableMap(updated));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpawnTable)) {
            return false;
        }
        SpawnTable that = (SpawnTable) other;
        return Objects.equals(global, that.global) && byDimension.equals(that.byDimension);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(global) * 31 + byDimension.hashCode();
    }

    @Override
    public String toString() {
        return "spawn " + (global == null ? "none" : global.print()) + ", " + byDimension.size() + " dimension(s)";
    }
}
