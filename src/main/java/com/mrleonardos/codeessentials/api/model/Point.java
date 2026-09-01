package com.mrleonardos.codeessentials.api.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Точка мира: измерение, координаты и поворот камеры.
 *
 * <p>
 * Печатается и читается одной строкой {@code dim@x,y,z,yaw,pitch} без округления, поэтому запись в
 * файл и чтение обратно дают ту же точку. Разбор отвечает пустым ответом, а не исключением: строку
 * правит человек, и опечатка в одном варпе не должна ронять чтение файла целиком.
 *
 * <p>
 * NaN и бесконечность не проходят ни в {@link #of}, ни в разборе: такая координата уносит игрока за
 * пределы мира, и лучше отбросить запись, чем потом искать причину падения.
 */
public final class Point {

    /** Разделитель измерения и координат в печатной форме. */
    public static final char DIMENSION_MARK = '@';

    /** Разделитель координат в печатной форме. */
    public static final char SEPARATOR = ',';

    private final int dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private Point(int dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Собрать точку.
     *
     * @throws IllegalArgumentException если координата или поворот не конечное число
     */
    public static Point of(int dimension, double x, double y, double z, float yaw, float pitch) {
        checkFinite(x, "x");
        checkFinite(y, "y");
        checkFinite(z, "z");
        checkFinite(yaw, "yaw");
        checkFinite(pitch, "pitch");
        return new Point(dimension, x, y, z, yaw, pitch);
    }

    /** Точка без поворота камеры. */
    public static Point of(int dimension, double x, double y, double z) {
        return of(dimension, x, y, z, 0.0F, 0.0F);
    }

    /**
     * Прочитать точку из печатной формы.
     *
     * @return точка или пустой ответ, если строка не разбирается
     */
    public static Optional<Point> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int mark = text.indexOf(DIMENSION_MARK);
        if (mark < 1 || mark == text.length() - 1) {
            return Optional.empty();
        }
        String[] parts = text.substring(mark + 1)
            .split(String.valueOf(SEPARATOR), -1);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                of(
                    Integer.parseInt(
                        text.substring(0, mark)
                            .trim()),
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Float.parseFloat(parts[3]),
                    Float.parseFloat(parts[4])));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    /** Идентификатор измерения. */
    public int dimension() {
        return dimension;
    }

    /** Координата x. */
    public double x() {
        return x;
    }

    /** Координата y. */
    public double y() {
        return y;
    }

    /** Координата z. */
    public double z() {
        return z;
    }

    /** Поворот камеры по горизонтали. */
    public float yaw() {
        return yaw;
    }

    /** Наклон камеры. */
    public float pitch() {
        return pitch;
    }

    /** Координата блока по x. */
    public int blockX() {
        return (int) Math.floor(x);
    }

    /** Координата блока по y. */
    public int blockY() {
        return (int) Math.floor(y);
    }

    /** Координата блока по z. */
    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** Одно ли измерение у двух точек. */
    public boolean sameDimension(Point other) {
        Objects.requireNonNull(other, "other");
        return dimension == other.dimension;
    }

    /**
     * Расстояние по горизонтали.
     *
     * @return бесконечность для точки в другом измерении: игрок ушёл дальше любого радиуса
     */
    public double horizontalDistanceTo(Point other) {
        if (!sameDimension(other)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Расстояние по высоте.
     *
     * @return бесконечность для точки в другом измерении
     */
    public double verticalDistanceTo(Point other) {
        if (!sameDimension(other)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(y - other.y);
    }

    /** Та же точка с другими координатами: измерение и поворот камеры сохраняются. */
    public Point withPosition(double newX, double newY, double newZ) {
        return of(dimension, newX, newY, newZ, yaw, pitch);
    }

    /** Та же точка, сдвинутая в середину указанного блока: так приземляют скорректированный слот. */
    public Point withBlock(int blockX, int blockY, int blockZ) {
        return withPosition(blockX + 0.5D, blockY, blockZ + 0.5D);
    }

    /** Печатная форма {@code dim@x,y,z,yaw,pitch}. */
    public String print() {
        return dimension + String
            .valueOf(DIMENSION_MARK) + x + SEPARATOR + y + SEPARATOR + z + SEPARATOR + yaw + SEPARATOR + pitch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Point)) {
            return false;
        }
        Point that = (Point) other;
        return dimension == that.dimension && Double.compare(x, that.x) == 0
            && Double.compare(y, that.y) == 0
            && Double.compare(z, that.z) == 0
            && Float.compare(yaw, that.yaw) == 0
            && Float.compare(pitch, that.pitch) == 0;
    }

    @Override
    public int hashCode() {
        return ((((dimension * 31 + Double.hashCode(x)) * 31 + Double.hashCode(y)) * 31 + Double.hashCode(z)) * 31
            + Float.hashCode(yaw)) * 31 + Float.hashCode(pitch);
    }

    @Override
    public String toString() {
        return print();
    }

    private static void checkFinite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number: " + value);
        }
    }
}
