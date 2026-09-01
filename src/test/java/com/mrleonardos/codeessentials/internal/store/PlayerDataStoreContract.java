package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;

abstract class PlayerDataStoreContract {

    static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    final AtomicLong clock = new AtomicLong(10_000L);

    private PlayerDataStore store;

    protected abstract PlayerDataStore open();

    @BeforeEach
    void openStore() {
        store = open();
    }

    @Test
    void freshMediumHoldsNobody() {
        assertTrue(
            store.loadPlayers()
                .isEmpty());
        assertTrue(
            store.loadCooldowns()
                .isEmpty());
    }

    @Test
    void appliedPlayerSurvivesAFlushAndAReopen() {
        store.loadPlayers();
        store.apply(
            ChangeBatch.builder("test")
                .upsert(steve())
                .build());
        flush(store);

        PlayerDataStore reopened = open();
        Map<UUID, PlayerRecord> players = reopened.loadPlayers();

        assertEquals(steve(), players.get(STEVE));
    }

    @Test
    void appliedChangeIsNotOnTheMediumUntilFlush() {
        store.loadPlayers();
        store.apply(
            ChangeBatch.builder("test")
                .upsert(steve())
                .build());

        assertTrue(
            open().loadPlayers()
                .isEmpty(),
            "до сброса на носителе ничего быть не должно");
    }

    @Test
    void removedPlayerLeavesTheMedium() {
        store.loadPlayers();
        store.apply(
            ChangeBatch.builder("test")
                .upsert(steve())
                .build());
        flush(store);
        store.apply(
            ChangeBatch.builder("test")
                .removePlayer(STEVE)
                .build());
        flush(store);

        assertTrue(
            open().loadPlayers()
                .isEmpty());
    }

    @Test
    void cooldownsSurviveAReopenAndKeepTheirKeys() {
        store.loadCooldowns();
        store.apply(
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", clock.get() + 5_000L)
                .setCooldown(STEVE, PlayerDataStore.REQUEST_COOLDOWN_KEY, clock.get() + 7_000L)
                .build());
        flush(store);

        Map<UUID, Map<String, Long>> loaded = open().loadCooldowns();

        assertEquals(
            Long.valueOf(clock.get() + 5_000L),
            loaded.get(STEVE)
                .get("home"));
        assertEquals(
            Long.valueOf(clock.get() + 7_000L),
            loaded.get(STEVE)
                .get(PlayerDataStore.REQUEST_COOLDOWN_KEY));
    }

    @Test
    void expiredCooldownsAreGoneOnLoad() {
        store.loadCooldowns();
        store.apply(
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", clock.get() + 5_000L)
                .setCooldown(ALEX, "warp", clock.get() + 50_000L)
                .build());
        flush(store);
        clock.set(clock.get() + 6_000L);

        Map<UUID, Map<String, Long>> loaded = open().loadCooldowns();

        assertFalse(loaded.containsKey(STEVE), "протухший кулдаун обязан уйти молча");
        assertTrue(loaded.containsKey(ALEX));
    }

    @Test
    void clearedCooldownsLeaveTheMedium() {
        store.loadCooldowns();
        store.apply(
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", clock.get() + 5_000L)
                .build());
        flush(store);
        store.apply(
            ChangeBatch.builder("test")
                .clearCooldowns(STEVE)
                .build());
        flush(store);

        assertTrue(
            open().loadCooldowns()
                .isEmpty());
    }

    @Test
    void flushWithoutChangesStaysQuiet() {
        store.loadPlayers();
        store.loadCooldowns();

        assertFalse(((BufferedStore) store).unsaved());
        assertTrue(
            ((BufferedStore) store).flush()
                .successful());
    }

    @Test
    void appliedChangeMarksTheStoreUnsavedUntilItIsWritten() {
        store.loadPlayers();
        store.apply(
            ChangeBatch.builder("test")
                .upsert(steve())
                .build());

        assertTrue(((BufferedStore) store).unsaved());

        flush(store);

        assertFalse(((BufferedStore) store).unsaved());
    }

    static PlayerRecord steve() {
        return PlayerRecord.of(
            STEVE,
            "Steve",
            Arrays.asList(
                HomeRecord.of("home", Point.of(0, 10.5D, 64.0D, -20.5D, 90.0F, 12.5F), 1_700L),
                HomeRecord.of("mine", Point.of(-1, 8.0D, 31.0D, 8.0D), 1_800L)),
            Arrays.asList(BackPoint.of(Point.of(0, 1.5D, 70.0D, 2.5D, 45.0F, -10.0F), BackPoint.Origin.DEATH, 1_900L)));
    }

    private static void flush(PlayerDataStore store) {
        StoreResult written = ((BufferedStore) store).flush();
        assertTrue(written.successful(), "сброс обязан пройти: " + written);
    }
}
