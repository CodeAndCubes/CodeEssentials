package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.RandomRules;

class EssentialsRulesTest {

    @Test
    void theWarmupOfTheMainFileReachesTheEngine() {
        EssentialsSettings settings = new EssentialsSettings();
        EssentialsSection section = new EssentialsSection();
        section.warmupSeconds = 7;

        EngineRules rules = rules(settings, section);

        assertEquals(7, rules.warmupSeconds());
    }

    @Test
    void everyCooldownOfTheMainFileReachesTheEngine() {
        EssentialsSettings settings = new EssentialsSettings();
        EssentialsSection section = new EssentialsSection();
        section.cooldowns.home = 11;
        section.cooldowns.spawn = 12;
        section.cooldowns.warp = 13;
        section.cooldowns.back = 14;
        section.cooldowns.tpa = 15;
        section.cooldowns.random = 16;

        EngineRules rules = rules(settings, section);

        assertEquals(11, rules.cooldownSeconds(TeleportCause.HOME));
        assertEquals(12, rules.cooldownSeconds(TeleportCause.SPAWN));
        assertEquals(13, rules.cooldownSeconds(TeleportCause.WARP));
        assertEquals(14, rules.cooldownSeconds(TeleportCause.BACK));
        assertEquals(15, rules.cooldownSeconds(TeleportCause.TPA));
        assertEquals(16, rules.cooldownSeconds(TeleportCause.RANDOM));
        assertEquals(0, rules.cooldownSeconds(TeleportCause.ADMIN), "административный перенос бесплатен");
        assertEquals(0, rules.cooldownSeconds(TeleportCause.RESPAWN));
    }

    @Test
    void theCeilingOfTheModFileCutsTheWarmupOfTheMainFile() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.warmupSeconds = 4;
        EssentialsSection section = new EssentialsSection();
        section.warmupSeconds = 60;

        assertEquals(4, rules(settings, section).warmupSeconds());
    }

    @Test
    void theRareSettingsOfTheModFileReachTheEngineToo() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.teleport.warmupMoveRadius = 3.0D;
        settings.teleport.warmupCancelOnDamage = false;
        settings.requests.rateSeconds = 21;
        settings.requests.timeoutSeconds = 22;
        settings.requests.maxPending = 4;
        settings.safeSpot.radius = 2;

        EngineRules rules = rules(settings, new EssentialsSection());

        assertEquals(3.0D, rules.moveRadius(), 0.0D);
        assertEquals(1.5D, rules.verticalMoveRadius(), 0.0D);
        assertFalse(rules.cancelOnDamage());
        assertEquals(21, rules.requestRateSeconds());
        assertEquals(22, rules.requestTimeoutSeconds());
        assertEquals(4, rules.maxPending());
        assertEquals(
            2,
            rules.safeSpot()
                .radius());
    }

    @Test
    void everyKeyOfTheRandomSectionReachesTheEngine() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.rtp.enabled = false;
        settings.rtp.minRadius = 100;
        settings.rtp.maxRadius = 900;
        settings.rtp.center = EssentialsSettings.CENTER_POINT;
        settings.rtp.centerX = 64;
        settings.rtp.centerZ = -128;
        settings.rtp.worlds = new ArrayList<>(Arrays.asList(Integer.valueOf(-1), Integer.valueOf(7)));
        settings.rtp.blockedBiomes = new ArrayList<>(Arrays.asList("Deep Ocean"));
        settings.rtp.attempts = 12;
        settings.rtp.onFailure = EssentialsSettings.FAILURE_SPAWN;

        RandomRules random = rules(settings, new EssentialsSection()).random();

        assertFalse(random.enabled());
        assertEquals(100, random.minRadius());
        assertEquals(900, random.maxRadius());
        assertFalse(random.centerAtSpawn());
        assertEquals(64, random.centerX());
        assertEquals(-128, random.centerZ());
        assertFalse(random.allows(0));
        assertTrue(random.allows(-1));
        assertTrue(random.allows(7));
        assertTrue(random.blocked("deepocean"), "имя биома сравнивается без пробелов и регистра");
        assertEquals(12, random.attempts());
        assertTrue(random.fallbackToSpawn());
    }

    @Test
    void theCeilingOfTheModFileCutsTheNumberOfTries() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.randomAttempts = 4;
        settings.rtp.attempts = 10000;

        assertEquals(
            4,
            rules(settings, new EssentialsSection()).random()
                .attempts());
    }

    @Test
    void anUnreadableWordOfTheRandomSectionFallsBackWithoutBreakingTheRules() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.rtp.center = "середина";
        settings.rtp.onFailure = "куда-нибудь";
        settings.rtp.minRadius = -10;
        settings.rtp.maxRadius = 5;
        settings.rtp.worlds = new ArrayList<>();

        RandomRules random = rules(settings, new EssentialsSection()).random();

        assertTrue(random.centerAtSpawn(), "незнакомое слово центра читается как spawn");
        assertFalse(random.fallbackToSpawn(), "незнакомое слово исхода читается как refuse");
        assertEquals(0, random.minRadius());
        assertEquals(5, random.maxRadius());
        assertTrue(random.allows(0), "пустой список миров разрешает любое измерение");
        assertTrue(random.allows(-1));
    }

    @Test
    void theFarEdgeOfTheRingIsNeverCloserThanTheNearOne() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.rtp.minRadius = 800;
        settings.rtp.maxRadius = 100;

        RandomRules random = rules(settings, new EssentialsSection()).random();

        assertEquals(800, random.minRadius());
        assertEquals(800, random.maxRadius());
    }

    private static EngineRules rules(EssentialsSettings settings, EssentialsSection section) {
        EssentialsLimits ceilings = settings.ceilings();
        return EssentialsRules.of(settings, SharedFixtures.of(section), ceilings);
    }
}
