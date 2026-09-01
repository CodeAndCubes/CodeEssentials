package com.mrleonardos.codeessentials.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RecordsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private static final Point HOME_POINT = Point.of(0, 10.5D, 64.0D, -20.5D, 90.0F, 0.0F);

    @Test
    void homeKeepsNameAndPoint() {
        HomeRecord home = HomeRecord.of("home", HOME_POINT, 1000L);

        assertEquals("home", home.name());
        assertEquals(HOME_POINT, home.point());
        assertEquals(1000L, home.createdAt());
        assertEquals("home " + HOME_POINT.print(), home.toString());
    }

    @Test
    void homeNameFollowsThePattern() {
        assertEquals(
            "my-second_home2",
            HomeRecord.of("my-second_home2", HOME_POINT, 0L)
                .name());
        assertThrows(NullPointerException.class, () -> HomeRecord.of(null, HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("Home", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("my.home", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("my home", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("мойдом", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("home*", HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of(times('h', 33), HOME_POINT, 0L));
        assertThrows(IllegalArgumentException.class, () -> HomeRecord.of("home", HOME_POINT, -1L));
    }

    @Test
    void movedHomeKeepsItsName() {
        Point moved = Point.of(0, 1.0D, 70.0D, 2.0D);
        HomeRecord home = HomeRecord.of("home", HOME_POINT, 1000L)
            .movedTo(moved, 2000L);

        assertEquals("home", home.name());
        assertEquals(moved, home.point());
        assertEquals(2000L, home.createdAt());
    }

    @Test
    void warpFillsMissingDescription() {
        WarpRecord warp = WarpRecord.of("shop", HOME_POINT, null);

        assertEquals("shop", warp.name());
        assertEquals("", warp.description());
        assertEquals(
            "рынок",
            WarpRecord.of("shop", HOME_POINT, "рынок")
                .description());
        assertThrows(IllegalArgumentException.class, () -> WarpRecord.of("shop.vip", HOME_POINT, ""));
        assertThrows(IllegalArgumentException.class, () -> WarpRecord.of("shop*", HOME_POINT, ""));
    }

    @Test
    void backPointMarksDeath() {
        BackPoint death = BackPoint.of(HOME_POINT, BackPoint.Origin.DEATH, 500L);
        BackPoint teleport = BackPoint.of(HOME_POINT, BackPoint.Origin.TELEPORT, 500L);

        assertTrue(death.fromDeath());
        assertFalse(teleport.fromDeath());
        assertFalse(death.equals(teleport));
        assertThrows(IllegalArgumentException.class, () -> BackPoint.of(HOME_POINT, BackPoint.Origin.DEATH, -1L));
        assertThrows(NullPointerException.class, () -> BackPoint.of(null, BackPoint.Origin.DEATH, 0L));
    }

    @Test
    void playerHomesAreSortedAndClosed() {
        List<HomeRecord> source = new ArrayList<>(
            Arrays.asList(HomeRecord.of("mine", HOME_POINT, 1L), HomeRecord.of("base", HOME_POINT, 2L)));
        PlayerRecord player = PlayerRecord.of(PLAYER, "Steve", source, new ArrayList<BackPoint>());
        source.clear();

        assertEquals(
            2,
            player.homes()
                .size());
        assertEquals(
            Arrays.asList("base", "mine"),
            new ArrayList<>(
                player.homes()
                    .keySet()));
        assertThrows(
            UnsupportedOperationException.class,
            () -> player.homes()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> player.back()
                .add(BackPoint.of(HOME_POINT, BackPoint.Origin.DEATH, 1L)));
    }

    @Test
    void emptyPlayerHasNothing() {
        PlayerRecord player = PlayerRecord.empty(PLAYER, null);

        assertNull(player.name());
        assertTrue(
            player.homes()
                .isEmpty());
        assertFalse(
            player.topBack()
                .isPresent());
        assertEquals(PLAYER.toString() + ": 0 home(s), 0 back", player.toString());
        assertEquals(
            "Steve",
            player.withName("Steve")
                .name());
    }

    @Test
    void homesGoInAndOutWithoutTouchingTheOldRecord() {
        PlayerRecord player = PlayerRecord.empty(PLAYER, "Steve");
        PlayerRecord withHome = player.withHome(HomeRecord.of("home", HOME_POINT, 1L));
        PlayerRecord moved = withHome.withHome(HomeRecord.of("home", Point.of(0, 5.0D, 70.0D, 5.0D), 2L));

        assertTrue(
            player.homes()
                .isEmpty());
        assertEquals(
            1,
            moved.homes()
                .size());
        assertEquals(
            2L,
            moved.home("home")
                .get()
                .createdAt());
        assertTrue(
            moved.withoutHome("home")
                .homes()
                .isEmpty());
        assertSame(moved, moved.withoutHome("guest"));
    }

    @Test
    void backStackKeepsTheNewestAndTrimsTheTail() {
        PlayerRecord player = PlayerRecord.empty(PLAYER, "Steve")
            .pushBack(point(1), 3)
            .pushBack(point(2), 3)
            .pushBack(point(3), 3)
            .pushBack(point(4), 3);

        assertEquals(
            3,
            player.back()
                .size());
        assertEquals(
            4L,
            player.topBack()
                .get()
                .recordedAt());
        assertEquals(
            2L,
            player.back()
                .get(2)
                .recordedAt());
        assertEquals(
            3L,
            player.popBack()
                .topBack()
                .get()
                .recordedAt());
    }

    @Test
    void backDepthNeverLeavesItsCeiling() {
        PlayerRecord player = PlayerRecord.empty(PLAYER, "Steve");

        assertEquals(
            1,
            player.pushBack(point(1), 0)
                .pushBack(point(2), 0)
                .back()
                .size());
        assertEquals(
            1,
            player.pushBack(point(1), -5)
                .back()
                .size());

        PlayerRecord greedy = player;
        for (int index = 0; index < 20; index++) {
            greedy = greedy.pushBack(point(index), 999);
        }
        assertEquals(
            10,
            greedy.back()
                .size());
        assertSame(player, player.popBack());
    }

    @Test
    void spawnTableGoesFromDimensionToGlobal() {
        Point global = Point.of(0, 0.0D, 64.0D, 0.0D);
        Point nether = Point.of(-1, 10.0D, 32.0D, 10.0D);
        Map<Integer, Point> source = new LinkedHashMap<>();
        source.put(Integer.valueOf(-1), nether);
        SpawnTable table = SpawnTable.of(global, source);
        source.clear();

        assertEquals(
            nether,
            table.pointFor(-1)
                .get());
        assertEquals(
            global,
            table.pointFor(0)
                .get());
        assertEquals(
            global,
            table.pointFor(7)
                .get());
        assertFalse(table.isEmpty());
        assertThrows(
            UnsupportedOperationException.class,
            () -> table.byDimension()
                .clear());
    }

    @Test
    void emptySpawnTableLeavesTheChoiceToVanilla() {
        SpawnTable table = SpawnTable.empty();

        assertTrue(table.isEmpty());
        assertFalse(
            table.global()
                .isPresent());
        assertFalse(
            table.pointFor(0)
                .isPresent());

        Point end = Point.of(1, 100.0D, 50.0D, 0.0D);
        SpawnTable filled = table.withDimension(end);

        assertFalse(filled.isEmpty());
        assertEquals(
            end,
            filled.pointFor(1)
                .get());
        assertFalse(
            filled.pointFor(0)
                .isPresent());
        assertEquals(
            end,
            filled.withGlobal(end)
                .global()
                .get());
        assertTrue(table.isEmpty());
    }

    private static BackPoint point(long recordedAt) {
        return BackPoint.of(HOME_POINT, BackPoint.Origin.TELEPORT, recordedAt);
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
