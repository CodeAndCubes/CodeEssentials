package com.mrleonardos.codeessentials.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EssentialsLimitsTest {

    @Test
    void factoryValuesMatchTheSpec() {
        EssentialsLimits limits = EssentialsLimits.defaults();

        assertEquals(32, limits.nameLength());
        assertEquals(128, limits.homesPerPlayer());
        assertEquals(512, limits.warps());
        assertEquals(8, limits.pendingRequests());
        assertEquals(3600, limits.requestTimeoutSeconds());
        assertEquals(300, limits.warmupSeconds());
        assertEquals(8, limits.safeSpotRadius());
        assertEquals(10, limits.backDepth());
        assertEquals(32, limits.randomAttempts());
    }

    @Test
    void nameFollowsThePattern() {
        EssentialsLimits limits = EssentialsLimits.defaults();

        assertTrue(limits.acceptsName("home"));
        assertTrue(limits.acceptsName("my-second_home2"));
        assertFalse(limits.acceptsName(null));
        assertFalse(limits.acceptsName(""));
        assertFalse(limits.acceptsName("Home"));
        assertFalse(limits.acceptsName("my.home"));
        assertFalse(limits.acceptsName("my home"));
        assertFalse(limits.acceptsName("home*"));
        assertFalse(limits.acceptsName("дом"));
        assertFalse(limits.acceptsName(times('h', 33)));
        assertTrue(limits.acceptsName(times('h', 32)));
    }

    @Test
    void shorterNameCeilingCutsLongNames() {
        EssentialsLimits limits = EssentialsLimits.defaults()
            .loweredTo(
                EssentialsLimits.builder()
                    .nameLength(4)
                    .build());

        assertTrue(limits.acceptsName("home"));
        assertFalse(limits.acceptsName("homes"));
    }

    @Test
    void checkedNameAnswersWithTheNameOrAnError() {
        assertEquals("home", EssentialsLimits.checkedName("home", "home name"));
        assertThrows(NullPointerException.class, () -> EssentialsLimits.checkedName(null, "home name"));

        IllegalArgumentException broken = assertThrows(
            IllegalArgumentException.class,
            () -> EssentialsLimits.checkedName("Home", "home name"));
        assertTrue(
            broken.getMessage()
                .startsWith("home name must match " + EssentialsLimits.NAME_PATTERN));
    }

    @Test
    void loweringTakesTheMinimum() {
        EssentialsLimits lowered = EssentialsLimits.defaults()
            .loweredTo(
                EssentialsLimits.builder()
                    .nameLength(16)
                    .homesPerPlayer(5)
                    .warps(64)
                    .pendingRequests(3)
                    .requestTimeoutSeconds(60)
                    .warmupSeconds(10)
                    .safeSpotRadius(2)
                    .backDepth(3)
                    .randomAttempts(4)
                    .build());

        assertEquals(16, lowered.nameLength());
        assertEquals(5, lowered.homesPerPlayer());
        assertEquals(64, lowered.warps());
        assertEquals(3, lowered.pendingRequests());
        assertEquals(60, lowered.requestTimeoutSeconds());
        assertEquals(10, lowered.warmupSeconds());
        assertEquals(2, lowered.safeSpotRadius());
        assertEquals(3, lowered.backDepth());
        assertEquals(4, lowered.randomAttempts());
    }

    @Test
    void raisingAboveFactoryValueChangesNothing() {
        EssentialsLimits limits = EssentialsLimits.defaults();
        EssentialsLimits greedy = limits.loweredTo(
            EssentialsLimits.builder()
                .homesPerPlayer(9999)
                .warmupSeconds(9999)
                .safeSpotRadius(9999)
                .backDepth(9999)
                .build());

        assertEquals(limits, greedy);
        assertEquals(limits.hashCode(), greedy.hashCode());
    }

    @Test
    void zeroAndNegativeAreTreatedAsUnsetAndReported() {
        EssentialsLimits.Builder builder = EssentialsLimits.builder()
            .homesPerPlayer(0)
            .backDepth(-1)
            .warps(64)
            .randomAttempts(0);
        EssentialsLimits limits = builder.build();

        assertEquals(EssentialsLimits.DEFAULT_HOMES_PER_PLAYER, limits.homesPerPlayer());
        assertEquals(EssentialsLimits.DEFAULT_BACK_DEPTH, limits.backDepth());
        assertEquals(64, limits.warps());
        assertEquals(EssentialsLimits.DEFAULT_RANDOM_ATTEMPTS, limits.randomAttempts());
        assertEquals(
            3,
            builder.remarks()
                .size());
        assertTrue(
            builder.remarks()
                .get(0)
                .startsWith("limits.homesPerPlayer = 0"));
        assertTrue(
            builder.remarks()
                .get(1)
                .startsWith("limits.backDepth = -1"));
    }

    @Test
    void goodValuesLeaveNoRemarks() {
        EssentialsLimits.Builder builder = EssentialsLimits.builder()
            .homesPerPlayer(5)
            .warmupSeconds(3);
        builder.build();

        assertTrue(
            builder.remarks()
                .isEmpty());
    }

    @Test
    void clampsHoldBothEnds() {
        EssentialsLimits limits = EssentialsLimits.defaults();

        assertEquals(0, limits.clampHomes(-5));
        assertEquals(128, limits.clampHomes(9999));
        assertEquals(3, limits.clampHomes(3));

        assertEquals(1, limits.clampPendingRequests(0));
        assertEquals(8, limits.clampPendingRequests(99));

        assertEquals(5, limits.clampRequestTimeoutSeconds(1));
        assertEquals(3600, limits.clampRequestTimeoutSeconds(99999));

        assertEquals(0, limits.clampWarmupSeconds(-1));
        assertEquals(300, limits.clampWarmupSeconds(9999));

        assertEquals(0, limits.clampSafeSpotRadius(-1));
        assertEquals(8, limits.clampSafeSpotRadius(99));

        assertEquals(1, limits.clampBackDepth(0));
        assertEquals(10, limits.clampBackDepth(99));

        assertEquals(1, limits.clampRandomAttempts(0));
        assertEquals(32, limits.clampRandomAttempts(10000));
        assertEquals(8, limits.clampRandomAttempts(8));
    }

    @Test
    void clampsFollowTheLoweredCeiling() {
        EssentialsLimits limits = EssentialsLimits.defaults()
            .loweredTo(
                EssentialsLimits.builder()
                    .backDepth(2)
                    .warmupSeconds(5)
                    .build());

        assertEquals(2, limits.clampBackDepth(99));
        assertEquals(5, limits.clampWarmupSeconds(99));
    }

    @Test
    void limitsCompareByEveryField() {
        EssentialsLimits limits = EssentialsLimits.defaults();
        EssentialsLimits other = limits.loweredTo(
            EssentialsLimits.builder()
                .warps(1)
                .build());

        assertFalse(limits.equals(other));
        assertFalse(limits.equals("limits"));
        assertTrue(
            limits.toString()
                .contains("homes 128"));
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
