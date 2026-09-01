package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;

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

        EngineRules rules = rules(settings, section);

        assertEquals(11, rules.cooldownSeconds(TeleportCause.HOME));
        assertEquals(12, rules.cooldownSeconds(TeleportCause.SPAWN));
        assertEquals(13, rules.cooldownSeconds(TeleportCause.WARP));
        assertEquals(14, rules.cooldownSeconds(TeleportCause.BACK));
        assertEquals(15, rules.cooldownSeconds(TeleportCause.TPA));
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

    private static EngineRules rules(EssentialsSettings settings, EssentialsSection section) {
        EssentialsLimits ceilings = settings.ceilings();
        return EssentialsRules.of(settings, SharedFixtures.of(section), ceilings);
    }
}
