package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;

class EssentialsStateTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void unknownPlayerAnswersWithAnEmptyRecord() {
        PlayerRecord empty = EssentialsState.empty()
            .player(STEVE);

        assertEquals(STEVE, empty.uuid());
        assertTrue(
            empty.homes()
                .isEmpty());
        assertTrue(
            empty.back()
                .isEmpty());
    }

    @Test
    void everyChangeMovesTheRevision() {
        EssentialsState first = EssentialsState.empty()
            .withPlayer(PlayerRecord.empty(STEVE, "Steve"));
        EssentialsState second = first.withCooldown(STEVE, "home", 5_000L);

        assertEquals(1L, first.revision());
        assertEquals(2L, second.revision());
        assertEquals(5_000L, second.cooldown(STEVE, "home"));
        assertEquals(0L, second.cooldown(ALEX, "home"));
        assertEquals(0L, second.cooldown(STEVE, "warp"));
    }

    @Test
    void changeThatChangesNothingKeepsTheSameSnapshot() {
        EssentialsState state = EssentialsState.empty()
            .withPlayer(PlayerRecord.empty(STEVE, "Steve"))
            .withCooldown(STEVE, "home", 5_000L);

        assertSame(state, state.withPlayer(PlayerRecord.empty(STEVE, "Steve")));
        assertSame(state, state.withCooldown(STEVE, "home", 5_000L));
        assertSame(state, state.withoutPlayer(ALEX));
        assertSame(state, state.withoutCooldowns(ALEX));
        assertSame(state, state.prunedCooldowns(1_000L));
    }

    @Test
    void pruningDropsOnlyWhatHasExpired() {
        EssentialsState state = EssentialsState.empty()
            .withCooldown(STEVE, "home", 5_000L)
            .withCooldown(STEVE, "warp", 9_000L)
            .withCooldown(ALEX, "home", 4_000L);

        EssentialsState pruned = state.prunedCooldowns(5_000L);

        assertEquals(0L, pruned.cooldown(STEVE, "home"));
        assertEquals(9_000L, pruned.cooldown(STEVE, "warp"));
        assertTrue(
            !pruned.cooldowns()
                .containsKey(ALEX));
        assertEquals(state.revision() + 1L, pruned.revision());
    }

    @Test
    void snapshotIsNotEditableFromOutside() {
        Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
        players.put(STEVE, PlayerRecord.empty(STEVE, "Steve"));
        Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();
        Map<String, Long> stamps = new TreeMap<>();
        stamps.put("home", Long.valueOf(5_000L));
        cooldowns.put(STEVE, stamps);
        EssentialsState state = EssentialsState.of(7L, players, cooldowns);

        players.remove(STEVE);
        stamps.clear();

        assertEquals(7L, state.revision());
        assertEquals(
            1,
            state.players()
                .size());
        assertEquals(5_000L, state.cooldown(STEVE, "home"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> state.players()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> state.cooldowns()
                .clear());
    }

    @Test
    void emptyStampsDoNotCreateAnEntry() {
        EssentialsState state = EssentialsState.of(
            1L,
            Collections.<UUID, PlayerRecord>emptyMap(),
            Collections.singletonMap(STEVE, Collections.<String, Long>emptyMap()));

        assertTrue(
            state.cooldowns()
                .isEmpty());
    }

    @Test
    void negativeDeadlineIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> EssentialsState.empty()
                .withCooldown(STEVE, "home", -1L));
    }
}
