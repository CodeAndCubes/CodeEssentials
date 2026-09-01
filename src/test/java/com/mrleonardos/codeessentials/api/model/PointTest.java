package com.mrleonardos.codeessentials.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PointTest {

    @Test
    void keepsWhatItWasGiven() {
        Point point = Point.of(-1, 12.5D, 64.0D, -8.5D, 90.0F, -12.0F);

        assertEquals(-1, point.dimension());
        assertEquals(12.5D, point.x());
        assertEquals(64.0D, point.y());
        assertEquals(-8.5D, point.z());
        assertEquals(90.0F, point.yaw());
        assertEquals(-12.0F, point.pitch());
    }

    @Test
    void withoutRotationLooksStraight() {
        Point point = Point.of(0, 1.0D, 2.0D, 3.0D);

        assertEquals(0.0F, point.yaw());
        assertEquals(0.0F, point.pitch());
    }

    @Test
    void blockCoordinatesRoundDownAlsoBelowZero() {
        Point point = Point.of(0, -0.5D, 64.9D, -8.5D);

        assertEquals(-1, point.blockX());
        assertEquals(64, point.blockY());
        assertEquals(-9, point.blockZ());
    }

    @Test
    void printAndParseGiveTheSamePoint() {
        Point point = Point.of(-1, 12.5D, 64.0D, -8.25D, 90.0F, -12.5F);

        assertEquals("-1@12.5,64.0,-8.25,90.0,-12.5", point.print());
        assertEquals(
            point,
            Point.parse(point.print())
                .get());
        assertEquals(
            point.hashCode(),
            Point.parse(point.print())
                .get()
                .hashCode());
    }

    @Test
    void brokenTextGivesNoPoint() {
        assertFalse(
            Point.parse(null)
                .isPresent());
        assertFalse(
            Point.parse("")
                .isPresent());
        assertFalse(
            Point.parse("home")
                .isPresent());
        assertFalse(
            Point.parse("@1.0,2.0,3.0,4.0,5.0")
                .isPresent());
        assertFalse(
            Point.parse("0@")
                .isPresent());
        assertFalse(
            Point.parse("0@1.0,2.0,3.0")
                .isPresent());
        assertFalse(
            Point.parse("0@1.0,2.0,3.0,4.0,5.0,6.0")
                .isPresent());
        assertFalse(
            Point.parse("nether@1.0,2.0,3.0,4.0,5.0")
                .isPresent());
        assertFalse(
            Point.parse("0@1.0,2.0,three,4.0,5.0")
                .isPresent());
    }

    @Test
    void endlessCoordinateGivesNoPoint() {
        assertFalse(
            Point.parse("0@1.0,NaN,3.0,4.0,5.0")
                .isPresent());
        assertFalse(
            Point.parse("0@1.0,Infinity,3.0,4.0,5.0")
                .isPresent());
        assertThrows(IllegalArgumentException.class, () -> Point.of(0, Double.NaN, 2.0D, 3.0D));
        assertThrows(IllegalArgumentException.class, () -> Point.of(0, 1.0D, Double.POSITIVE_INFINITY, 3.0D));
        assertThrows(IllegalArgumentException.class, () -> Point.of(0, 1.0D, 2.0D, 3.0D, Float.NaN, 0.0F));
    }

    @Test
    void distanceSplitsHorizontalAndVertical() {
        Point start = Point.of(0, 0.0D, 64.0D, 0.0D);
        Point aside = Point.of(0, 3.0D, 64.0D, 4.0D);
        Point above = Point.of(0, 0.0D, 66.5D, 0.0D);

        assertEquals(5.0D, start.horizontalDistanceTo(aside));
        assertEquals(0.0D, start.verticalDistanceTo(aside));
        assertEquals(0.0D, start.horizontalDistanceTo(above));
        assertEquals(2.5D, start.verticalDistanceTo(above));
    }

    @Test
    void anotherDimensionIsFartherThanAnyRadius() {
        Point overworld = Point.of(0, 0.0D, 64.0D, 0.0D);
        Point nether = Point.of(-1, 0.0D, 64.0D, 0.0D);

        assertFalse(overworld.sameDimension(nether));
        assertTrue(overworld.sameDimension(Point.of(0, 100.0D, 1.0D, 100.0D)));
        assertEquals(Double.POSITIVE_INFINITY, overworld.horizontalDistanceTo(nether));
        assertEquals(Double.POSITIVE_INFINITY, overworld.verticalDistanceTo(nether));
    }

    @Test
    void movingKeepsDimensionAndCamera() {
        Point point = Point.of(-1, 12.5D, 64.0D, -8.5D, 90.0F, -12.0F);
        Point moved = point.withPosition(1.0D, 2.0D, 3.0D);
        Point block = point.withBlock(10, 70, -20);

        assertEquals(-1, moved.dimension());
        assertEquals(90.0F, moved.yaw());
        assertEquals(-12.0F, moved.pitch());
        assertEquals(1.0D, moved.x());
        assertEquals(10.5D, block.x());
        assertEquals(70.0D, block.y());
        assertEquals(-19.5D, block.z());
    }

    @Test
    void pointsCompareByEveryField() {
        Point point = Point.of(0, 1.0D, 2.0D, 3.0D, 4.0F, 5.0F);

        assertEquals(point, Point.of(0, 1.0D, 2.0D, 3.0D, 4.0F, 5.0F));
        assertFalse(point.equals(Point.of(1, 1.0D, 2.0D, 3.0D, 4.0F, 5.0F)));
        assertFalse(point.equals(Point.of(0, 1.0D, 2.0D, 3.0D, 4.0F, 6.0F)));
        assertFalse(point.equals("0@1.0,2.0,3.0,4.0,5.0"));
        assertEquals(point.print(), point.toString());
    }
}
