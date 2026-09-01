package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.store.MemoryPlayerDataStore;
import com.mrleonardos.codeessentials.internal.store.SingleWriterImpl;

class CooldownsTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final EngineFixtures.FakeRights rights = new EngineFixtures.FakeRights();
    private final MemoryPlayerDataStore.Medium medium = new MemoryPlayerDataStore.Medium();

    @Test
    void causeCooldownIsWrittenOnlyForCausesThatChargeIt() {
        Cooldowns cooldowns = cooldowns(rules(30, 10));

        assertTrue(
            cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME)
                .successful());
        assertTrue(
            cooldowns.charge(EngineFixtures.STEVE, TeleportCause.ADMIN)
                .successful());
        assertTrue(
            cooldowns.charge(EngineFixtures.STEVE, TeleportCause.RESPAWN)
                .successful());
        assertEquals(30_000L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.ADMIN), "админский перенос бесплатен");
    }

    @Test
    void causeWithoutASettingCostsNothing() {
        Cooldowns cooldowns = cooldowns(rules(0, 10));

        assertTrue(
            cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME)
                .successful());
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void twoClassesOfCooldownDoNotMixUp() {
        Cooldowns cooldowns = cooldowns(rules(30, 10));

        cooldowns.chargeRequest(EngineFixtures.STEVE);

        assertEquals(10_000L, cooldowns.remainingRequest(EngineFixtures.STEVE));
        assertEquals(
            0L,
            cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.TPA),
            "срок между просьбами не трогает кулдаун переноса");

        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.TPA);

        assertEquals(30_000L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.TPA));
        assertEquals(10_000L, cooldowns.remainingRequest(EngineFixtures.STEVE));
    }

    @Test
    void bypassNodeTakesBothClassesOff() {
        Cooldowns cooldowns = cooldowns(rules(30, 10));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);
        rights.allow(EngineFixtures.STEVE, Cooldowns.BYPASS_NODE);

        assertTrue(cooldowns.bypasses(EngineFixtures.STEVE));
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertEquals(0L, cooldowns.remainingRequest(EngineFixtures.STEVE));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);
        cooldowns.chargeRequest(EngineFixtures.STEVE);
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertEquals(0L, cooldowns.remainingRequest(EngineFixtures.STEVE));
    }

    @Test
    void cooldownRunsDownWithTheClockAndEndsAtZero() {
        Cooldowns cooldowns = cooldowns(rules(30, 10));
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);

        clock.set(clock.get() + 29_000L);
        assertEquals(1_000L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));

        clock.set(clock.get() + 5_000L);
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void cooldownLivesThroughARestartOnItsAbsoluteStamp() {
        SingleWriterImpl before = EngineFixtures.writer(medium, clock::get);
        new Cooldowns(before, rights, () -> rules(30, 10), clock::get).charge(EngineFixtures.STEVE, TeleportCause.HOME);
        before.stop();

        clock.set(clock.get() + 10_000L);
        Cooldowns after = new Cooldowns(
            EngineFixtures.writer(medium, clock::get),
            rights,
            () -> rules(30, 10),
            clock::get);

        assertEquals(20_000L, after.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void expiredStampDoesNotComeBackWithTheWorld() {
        SingleWriterImpl before = EngineFixtures.writer(medium, clock::get);
        new Cooldowns(before, rights, () -> rules(30, 10), clock::get).charge(EngineFixtures.STEVE, TeleportCause.HOME);
        before.stop();

        clock.set(clock.get() + 31_000L);
        Cooldowns after = new Cooldowns(
            EngineFixtures.writer(medium, clock::get),
            rights,
            () -> rules(30, 10),
            clock::get);

        assertEquals(0L, after.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    @Test
    void hugeSettingCountsInLongAndDoesNotWrapAround() {
        Cooldowns cooldowns = cooldowns(rules(Integer.MAX_VALUE, 10));

        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);
        long left = cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME);

        assertEquals(Integer.MAX_VALUE * 1000L, left, "секунды обязаны считаться в long, иначе срок схлопывается");
        assertTrue(left > 0L);
    }

    @Test
    void clearWipesEverythingAndAnswersHonestlyOnAnEmptyPlayer() {
        Cooldowns cooldowns = cooldowns(rules(30, 10));

        assertEquals(
            StoreResult.Failure.NOT_FOUND,
            cooldowns.clear(EngineFixtures.STEVE)
                .failure()
                .get(),
            "снимать было нечего");

        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);
        cooldowns.chargeRequest(EngineFixtures.STEVE);

        assertTrue(
            cooldowns.clear(EngineFixtures.STEVE)
                .successful());
        assertEquals(0L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
        assertEquals(0L, cooldowns.remainingRequest(EngineFixtures.STEVE));
    }

    @Test
    void aRefusedWriteIsNotTheSameAnswerAsAnEmptyPlayer() {
        EngineFixtures.MemoryWriter writer = new EngineFixtures.MemoryWriter();
        Cooldowns cooldowns = new Cooldowns(writer, rights, () -> rules(30, 10), clock::get);
        cooldowns.charge(EngineFixtures.STEVE, TeleportCause.HOME);
        writer.answer = StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, "disk is full");

        StoreResult refused = cooldowns.clear(EngineFixtures.STEVE);

        assertEquals(
            StoreResult.Failure.PROVIDER_FAILED,
            refused.failure()
                .get(),
            "отказ записи нельзя выдавать за «кулдаунов нет»");
        assertEquals(30_000L, cooldowns.remaining(EngineFixtures.STEVE, TeleportCause.HOME));
    }

    private Cooldowns cooldowns(EngineRules rules) {
        return new Cooldowns(EngineFixtures.writer(medium, clock::get), rights, () -> rules, clock::get);
    }

    private static EngineRules rules(int causeSeconds, int requestSeconds) {
        return EngineRules.builder()
            .cooldown(TeleportCause.HOME, causeSeconds)
            .cooldown(TeleportCause.TPA, causeSeconds)
            .requestRateSeconds(requestSeconds)
            .build();
    }
}
