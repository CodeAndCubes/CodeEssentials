package com.mrleonardos.codeessentials.api.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;

class TeleportJobTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final Point TARGET = Point.of(0, 10.5D, 64.0D, -20.5D, 90.0F, 0.0F);

    @Test
    void freshJobWaitsAndPromisesNothing() {
        TeleportJob job = TeleportJob.starting(1L, request(TeleportCause.HOME));

        assertEquals(1L, job.id());
        assertEquals(TeleportJob.State.WARMUP, job.state());
        assertEquals(PLAYER, job.player());
        assertEquals(TeleportCause.HOME, job.cause());
        assertTrue(job.active());
        assertFalse(job.finished());
        assertFalse(job.applied());
        assertFalse(job.corrected());
        assertFalse(
            job.reason()
                .isPresent());
        assertFalse(
            job.landing()
                .isPresent());
    }

    @Test
    void landingCarriesTheSpotAndTheCorrection() {
        TeleportJob exact = TeleportJob.starting(1L, request(TeleportCause.HOME))
            .moving()
            .done(TARGET);
        TeleportJob aside = TeleportJob.starting(2L, request(TeleportCause.HOME))
            .moving()
            .done(TARGET.withBlock(12, 65, -20));

        assertTrue(exact.applied());
        assertTrue(exact.finished());
        assertFalse(exact.corrected());
        assertEquals(
            TARGET,
            exact.landing()
                .get());
        assertTrue(aside.corrected());
        assertEquals(TeleportJob.State.DONE, aside.state());
    }

    @Test
    void cancelReasonDecidesTheEndingState() {
        TeleportJob moved = TeleportJob.starting(1L, request(TeleportCause.HOME))
            .stopped(CancelReason.MOVED);
        TeleportJob unsafe = TeleportJob.starting(2L, request(TeleportCause.HOME))
            .moving()
            .stopped(CancelReason.UNSAFE);

        assertEquals(TeleportJob.State.CANCELLED, moved.state());
        assertEquals(TeleportJob.State.FAILED, unsafe.state());
        assertEquals(
            CancelReason.MOVED,
            moved.reason()
                .get());
        assertFalse(moved.applied());
        assertFalse(unsafe.applied());
        assertFalse(
            unsafe.landing()
                .isPresent());
        assertTrue(moved.finished());
    }

    @Test
    void impossibleTransitionsAreErrorsInTheEngine() {
        TeleportJob warmup = TeleportJob.starting(1L, request(TeleportCause.HOME));
        TeleportJob moving = warmup.moving();
        TeleportJob done = moving.done(TARGET);

        assertThrows(IllegalStateException.class, () -> warmup.done(TARGET));
        assertThrows(IllegalStateException.class, () -> moving.moving());
        assertThrows(IllegalStateException.class, () -> done.moving());
        assertThrows(IllegalStateException.class, () -> done.stopped(CancelReason.MOVED));
        assertThrows(
            IllegalStateException.class,
            () -> warmup.stopped(CancelReason.DEAD)
                .stopped(CancelReason.MOVED));
    }

    @Test
    void everyStepGivesANewJob() {
        TeleportJob warmup = TeleportJob.starting(1L, request(TeleportCause.HOME));
        TeleportJob moving = warmup.moving();

        assertEquals(TeleportJob.State.WARMUP, warmup.state());
        assertFalse(warmup.equals(moving));
        assertEquals(warmup, TeleportJob.starting(1L, request(TeleportCause.HOME)));
        assertEquals(
            warmup.hashCode(),
            TeleportJob.starting(1L, request(TeleportCause.HOME))
                .hashCode());
        assertFalse(warmup.equals(TeleportJob.starting(2L, request(TeleportCause.HOME))));
        assertFalse(warmup.equals("#1"));
        assertTrue(
            moving.stopped(CancelReason.TIMEOUT)
                .toString()
                .contains("TIMEOUT"));
    }

    @Test
    void causeAnswersForWarmupCooldownAndBackStack() {
        assertEquals(9, TeleportCause.values().length);

        for (TeleportCause cause : TeleportCause.values()) {
            assertEquals(
                cause.name()
                    .toLowerCase(Locale.ROOT),
                cause.key());
        }

        assertTrue(TeleportCause.HOME.warmsUp());
        assertTrue(TeleportCause.HOME.chargesCooldown());
        assertTrue(TeleportCause.HOME.recordsBack());

        assertTrue(TeleportCause.API.warmsUp());
        assertTrue(TeleportCause.API.chargesCooldown());

        assertFalse(TeleportCause.BACK.recordsBack());
        assertTrue(TeleportCause.BACK.chargesCooldown());

        assertTrue(TeleportCause.RANDOM.warmsUp());
        assertTrue(TeleportCause.RANDOM.chargesCooldown());
        assertTrue(TeleportCause.RANDOM.recordsBack(), "после /rtp игрок вправе вернуться");

        assertFalse(TeleportCause.ADMIN.warmsUp());
        assertFalse(TeleportCause.ADMIN.chargesCooldown());
        assertTrue(TeleportCause.ADMIN.recordsBack());

        assertFalse(TeleportCause.RESPAWN.warmsUp());
        assertFalse(TeleportCause.RESPAWN.chargesCooldown());
        assertFalse(TeleportCause.RESPAWN.recordsBack());
    }

    @Test
    void reasonsSplitIntoCancelledAndFailed() {
        assertEquals(12, CancelReason.values().length);

        for (CancelReason reason : CancelReason.values()) {
            assertTrue(reason.state() == TeleportJob.State.CANCELLED || reason.state() == TeleportJob.State.FAILED);
            assertEquals(reason.state() == TeleportJob.State.FAILED, reason.failure());
            assertEquals(
                reason.name()
                    .toLowerCase(Locale.ROOT),
                reason.key());
        }

        assertFalse(CancelReason.SUPERSEDED.failure());
        assertFalse(CancelReason.VETOED.failure());
        assertFalse(CancelReason.BY_COMMAND.failure());
        assertFalse(CancelReason.COOLDOWN.failure(), "кулдаун это правило мода, а не помеха от мира");
        assertTrue(CancelReason.UNSAFE.failure());
        assertTrue(CancelReason.CHUNK_MISSING.failure());
        assertTrue(CancelReason.DIMENSION_MISSING.failure());
        assertTrue(CancelReason.TIMEOUT.failure());
    }

    @Test
    void requestKeepsItsDefaults() {
        TeleportRequest request = request(TeleportCause.HOME);

        assertEquals(PLAYER, request.player());
        assertEquals(TARGET, request.destination());
        assertEquals(TeleportCause.HOME, request.cause());
        assertEquals(TeleportRequest.WARMUP_FROM_SETTINGS, request.warmupSeconds());
        assertTrue(request.safeSpot());
        assertEquals(PLAYER.toString(), request.actor());
    }

    @Test
    void requestTakesItsOwnWarmupAndAuthor() {
        TeleportRequest request = TeleportRequest.builder(PLAYER, TARGET, TeleportCause.ADMIN)
            .warmupSeconds(0)
            .safeSpot(false)
            .actor("console")
            .build();

        assertEquals(0, request.warmupSeconds());
        assertFalse(request.safeSpot());
        assertEquals("console", request.actor());
        assertTrue(
            request.toString()
                .contains("console"));
    }

    @Test
    void requestChecksWhatItIsGiven() {
        assertThrows(NullPointerException.class, () -> TeleportRequest.builder(null, TARGET, TeleportCause.HOME));
        assertThrows(NullPointerException.class, () -> TeleportRequest.builder(PLAYER, null, TeleportCause.HOME));
        assertThrows(NullPointerException.class, () -> TeleportRequest.builder(PLAYER, TARGET, null));
        assertThrows(
            NullPointerException.class,
            () -> TeleportRequest.builder(PLAYER, TARGET, TeleportCause.HOME)
                .actor(null));
        assertThrows(
            IllegalArgumentException.class,
            () -> TeleportRequest.builder(PLAYER, TARGET, TeleportCause.HOME)
                .warmupSeconds(-2));
        assertThrows(
            IllegalArgumentException.class,
            () -> TeleportRequest.builder(PLAYER, TARGET, TeleportCause.HOME)
                .warmupSeconds(301));
        assertEquals(
            300,
            TeleportRequest.builder(PLAYER, TARGET, TeleportCause.HOME)
                .warmupSeconds(300)
                .build()
                .warmupSeconds());
    }

    @Test
    void requestsCompareByEveryField() {
        TeleportRequest request = request(TeleportCause.HOME);

        assertEquals(request, request(TeleportCause.HOME));
        assertEquals(request.hashCode(), request(TeleportCause.HOME).hashCode());
        assertFalse(request.equals(request(TeleportCause.SPAWN)));
        assertFalse(request.equals("home"));
    }

    private static TeleportRequest request(TeleportCause cause) {
        return TeleportRequest.builder(PLAYER, TARGET, cause)
            .build();
    }
}
