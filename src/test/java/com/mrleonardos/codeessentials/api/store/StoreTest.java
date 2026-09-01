package com.mrleonardos.codeessentials.api.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.PlayerRecord;

class StoreTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void successCarriesNoFailure() {
        StoreResult plain = StoreResult.success();
        StoreResult spoken = StoreResult.success("home moved");

        assertTrue(plain.successful());
        assertFalse(
            plain.failure()
                .isPresent());
        assertFalse(
            plain.message()
                .isPresent());
        assertEquals("success", plain.toString());
        assertTrue(spoken.successful());
        assertEquals(
            "home moved",
            spoken.message()
                .get());
        assertThrows(NullPointerException.class, () -> StoreResult.success(null));
    }

    @Test
    void failureCarriesTheReason() {
        StoreResult refused = StoreResult.failure(StoreResult.Failure.LIMIT_REACHED, "3 homes already");

        assertFalse(refused.successful());
        assertEquals(
            StoreResult.Failure.LIMIT_REACHED,
            refused.failure()
                .get());
        assertEquals(
            "3 homes already",
            refused.message()
                .get());
        assertEquals("LIMIT_REACHED: 3 homes already", refused.toString());
        assertThrows(NullPointerException.class, () -> StoreResult.failure(null, "no reason"));
    }

    @Test
    void resultsCompareByReasonAndText() {
        StoreResult refused = StoreResult.failure(StoreResult.Failure.NOT_FOUND, "no such home");

        assertEquals(refused, StoreResult.failure(StoreResult.Failure.NOT_FOUND, "no such home"));
        assertEquals(
            refused.hashCode(),
            StoreResult.failure(StoreResult.Failure.NOT_FOUND, "no such home")
                .hashCode());
        assertFalse(refused.equals(StoreResult.failure(StoreResult.Failure.NOT_FOUND, "another text")));
        assertFalse(refused.equals(StoreResult.failure(StoreResult.Failure.VETOED, "no such home")));
        assertFalse(refused.equals(StoreResult.success()));
        assertFalse(refused.equals("NOT_FOUND"));
        assertEquals(StoreResult.success(), StoreResult.success());
    }

    @Test
    void everyFailureHasItsPlace() {
        assertEquals(9, StoreResult.Failure.values().length);

        for (StoreResult.Failure failure : StoreResult.Failure.values()) {
            assertEquals(failure, StoreResult.Failure.valueOf(failure.name()));
        }
    }

    @Test
    void batchKeepsChangesInOrderAndClosesTheList() {
        PlayerRecord player = PlayerRecord.empty(PLAYER, "Steve");
        ChangeBatch.Builder builder = ChangeBatch.builder("Steve")
            .upsert(player)
            .setCooldown(PLAYER, "home", 5000L);
        ChangeBatch batch = builder.build();
        builder.clearCooldowns(PLAYER);

        List<ChangeBatch.Change> changes = batch.changes();

        assertEquals("Steve", batch.author());
        assertEquals(2, changes.size());
        assertEquals(
            ChangeBatch.Kind.UPSERT_PLAYER,
            changes.get(0)
                .kind());
        assertEquals(
            player,
            changes.get(0)
                .record()
                .get());
        assertEquals(
            PLAYER,
            changes.get(0)
                .player());
        assertEquals(
            ChangeBatch.Kind.SET_COOLDOWN,
            changes.get(1)
                .kind());
        assertEquals(
            "home",
            changes.get(1)
                .cooldownKey()
                .get());
        assertEquals(
            5000L,
            changes.get(1)
                .expiresAt());
        assertThrows(UnsupportedOperationException.class, () -> changes.clear());
        assertEquals("Steve: 2 change(s)", batch.toString());
    }

    @Test
    void removalsCarryOnlyThePlayer() {
        ChangeBatch batch = ChangeBatch.builder("console")
            .removePlayer(PLAYER)
            .clearCooldowns(PLAYER)
            .build();

        assertEquals(
            ChangeBatch.Kind.REMOVE_PLAYER,
            batch.changes()
                .get(0)
                .kind());
        assertFalse(
            batch.changes()
                .get(0)
                .record()
                .isPresent());
        assertEquals(
            ChangeBatch.Kind.CLEAR_COOLDOWNS,
            batch.changes()
                .get(1)
                .kind());
        assertFalse(
            batch.changes()
                .get(1)
                .cooldownKey()
                .isPresent());
        assertEquals(
            0L,
            batch.changes()
                .get(1)
                .expiresAt());
    }

    @Test
    void batchChecksWhatItIsGiven() {
        assertThrows(NullPointerException.class, () -> ChangeBatch.builder(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> ChangeBatch.builder("console")
                .build());
        assertThrows(NullPointerException.class, () -> ChangeBatch.Change.upsertPlayer(null));
        assertThrows(NullPointerException.class, () -> ChangeBatch.Change.removePlayer(null));
        assertThrows(NullPointerException.class, () -> ChangeBatch.Change.setCooldown(PLAYER, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> ChangeBatch.Change.setCooldown(PLAYER, "home", -1L));
        assertEquals(4, ChangeBatch.Kind.values().length);
    }

    @Test
    void batchesCompareByAuthorAndChanges() {
        ChangeBatch batch = ChangeBatch.builder("console")
            .removePlayer(PLAYER)
            .build();

        assertEquals(
            batch,
            ChangeBatch.builder("console")
                .removePlayer(PLAYER)
                .build());
        assertEquals(
            batch.hashCode(),
            ChangeBatch.builder("console")
                .removePlayer(PLAYER)
                .build()
                .hashCode());
        assertFalse(
            batch.equals(
                ChangeBatch.builder("Steve")
                    .removePlayer(PLAYER)
                    .build()));
        assertFalse(batch.equals("console"));
        assertEquals(
            ChangeBatch.Change.removePlayer(PLAYER),
            batch.changes()
                .get(0));
        assertFalse(
            batch.changes()
                .get(0)
                .equals(ChangeBatch.Change.clearCooldowns(PLAYER)));
        assertEquals(
            "REMOVE_PLAYER " + PLAYER,
            batch.changes()
                .get(0)
                .toString());
    }
}
