package com.mrleonardos.codeessentials.api.model;

import java.util.Objects;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Варп сервера: имя, точка и пояснение для списка.
 *
 * <p>
 * Файл варпов правит человек, поэтому имя проверяется тем же шаблоном, что и дом. Шаблон держит
 * ноду доступа целой: точка или звёздочка из имени разъехались бы с деревом прав, и варп
 * {@code shop.vip} открывался бы нодой на все варпы {@code shop}.
 */
public final class WarpRecord {

    private final String name;
    private final Point point;
    private final String description;

    private WarpRecord(String name, Point point, String description) {
        this.name = name;
        this.point = point;
        this.description = description;
    }

    /**
     * Собрать варп.
     *
     * @param name        имя в нижнем регистре по шаблону {@link EssentialsLimits#NAME_PATTERN}
     * @param description пояснение для списка, пустое значение допустимо
     * @throws IllegalArgumentException если имя не проходит шаблон
     */
    public static WarpRecord of(String name, Point point, String description) {
        Objects.requireNonNull(point, "point");
        return new WarpRecord(
            EssentialsLimits.checkedName(name, "warp name"),
            point,
            description == null ? "" : description);
    }

    /** Имя в нижнем регистре. */
    public String name() {
        return name;
    }

    /** Точка варпа. */
    public Point point() {
        return point;
    }

    /** Пояснение для списка, пустая строка вместо отсутствующего. */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarpRecord)) {
            return false;
        }
        WarpRecord that = (WarpRecord) other;
        return name.equals(that.name) && point.equals(that.point) && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + point.hashCode()) * 31 + description.hashCode();
    }

    @Override
    public String toString() {
        return name + " " + point.print();
    }
}
