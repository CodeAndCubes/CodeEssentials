package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;

class EssentialsSettingsTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");

    @Test
    void theFreshFileCarriesTheValuesFromTheDesign() {
        EssentialsSettings settings = new EssentialsSettings();

        assertEquals(ServicePriority.ADDON, settings.priority(LOG));
        assertEquals("json", settings.provider());
        assertEquals(30, settings.autosaveSeconds());
        assertEquals(600, settings.autosaveTicks());
        assertEquals(3, settings.warmupSeconds(settings.ceilings()));
        assertEquals(2.0D, settings.warmupMoveRadius(), 0.0D);
        assertEquals(1.0D, settings.warmupMoveHeight(), 0.0D);
        assertTrue(settings.warmupCancelOnDamage());
        assertFalse(settings.generateChunks());
        assertEquals(3, settings.defaultHomes(settings.ceilings()));
        assertEquals(EssentialsSettings.BACK_BOTH, settings.backMode());
        assertEquals(60, settings.requestTimeoutSeconds(settings.ceilings()));
        assertEquals(8, settings.maxPending(settings.ceilings()));
        assertEquals(10, settings.requestRateSeconds());
        assertTrue(settings.logChanges());
        assertFalse(settings.logChecks());
        assertEquals(SafeSpotLimits.defaults(), settings.spotLimits(settings.ceilings()));
    }

    @Test
    void everyCauseStartsWithoutACooldown() {
        EssentialsSettings settings = new EssentialsSettings();

        for (TeleportCause cause : TeleportCause.values()) {
            assertEquals(0, settings.cooldownSeconds(cause), cause.name());
        }
    }

    @Test
    void aCooldownFromTheFileIsReadByTheCauseKey() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.teleport.cooldowns.put(TeleportCause.HOME.key(), Integer.valueOf(45));
        settings.teleport.cooldowns.put(TeleportCause.WARP.key(), Integer.valueOf(-5));

        assertEquals(45, settings.cooldownSeconds(TeleportCause.HOME));
        assertEquals(0, settings.cooldownSeconds(TeleportCause.WARP), "минус не превращается в бесконечность");
    }

    @Test
    void theConfigLowersCeilingsAndNeverRaisesThem() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.homesPerPlayer = 5;
        settings.limits.warps = 100_000;
        settings.limits.backDepth = 2;

        EssentialsLimits ceilings = settings.ceilings();

        assertEquals(5, ceilings.homesPerPlayer());
        assertEquals(EssentialsLimits.DEFAULT_WARPS, ceilings.warps(), "выше заводского потолок не поднимается");
        assertEquals(2, ceilings.backDepth());
    }

    @Test
    void aCeilingBelowOneIsTreatedAsUnsetAndLeavesARemark() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.homesPerPlayer = 0;
        settings.limits.nameLength = -3;

        EssentialsLimits.Builder builder = EssentialsLimits.builder()
            .homesPerPlayer(0)
            .nameLength(-3);

        assertEquals(
            EssentialsLimits.DEFAULT_HOMES_PER_PLAYER,
            settings.ceilings()
                .homesPerPlayer());
        assertEquals(
            EssentialsLimits.DEFAULT_NAME_LENGTH,
            settings.ceilings()
                .nameLength());
        builder.build();
        assertEquals(
            2,
            builder.remarks()
                .size());
    }

    @Test
    void anUnknownPriorityFallsBackToAddon() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.servicePriority = "boss";

        assertEquals(ServicePriority.ADDON, settings.priority(LOG));

        settings.servicePriority = "override";
        assertEquals(ServicePriority.OVERRIDE, settings.priority(LOG));
    }

    @Test
    void backOnAcceptsFourWordsAndFallsBackToBoth() {
        EssentialsSettings settings = new EssentialsSettings();

        settings.back.on = "death";
        assertEquals(EssentialsSettings.BACK_DEATH, settings.backMode(LOG));
        assertFalse(settings.backRecordsTeleports());
        assertTrue(settings.backRecordsDeaths());

        settings.back.on = "TELEPORT";
        assertTrue(settings.backRecordsTeleports());
        assertFalse(settings.backRecordsDeaths());

        settings.back.on = "NONE";
        assertFalse(settings.backRecordsTeleports());
        assertFalse(settings.backRecordsDeaths());

        settings.back.on = "иногда";
        assertEquals(EssentialsSettings.BACK_BOTH, settings.backMode(LOG));
    }

    @Test
    void anEmptyProviderNameFallsBackToJson() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.storage.playerProvider = "   ";
        settings.safeSpot.policy = null;

        assertEquals(EssentialsSettings.DEFAULT_PROVIDER, settings.provider());
        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());
    }

    @Test
    void theSpecPointsAtTheSettingsFile() {
        assertEquals(
            EssentialsSettings.MODID,
            EssentialsSettings.spec()
                .modid());
        assertEquals(
            EssentialsSettings.SETTINGS_FILE,
            EssentialsSettings.spec()
                .name());
        assertEquals(
            ConfigScope.SETTINGS,
            EssentialsSettings.spec()
                .scope());
        assertEquals(
            EssentialsSettings.SETTINGS_VERSION,
            EssentialsSettings.spec()
                .schemaVersion());
    }

    @Test
    void aFileEditedIntoNullSectionsIsHealedBeforeUse() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.storage = null;
        settings.teleport = null;
        settings.safeSpot = null;
        settings.homes = null;
        settings.back = null;
        settings.requests = null;
        settings.limits = null;
        settings.audit = null;

        EssentialsSettings.spec()
            .validator()
            .accept(settings);

        assertEquals("json", settings.provider());
        assertEquals(3, settings.defaultHomes(settings.ceilings()));
        assertEquals(0, settings.cooldownSeconds(TeleportCause.HOME));
    }

    @Test
    void theDefaultsSupplierMakesAFreshObjectEveryTime() {
        EssentialsSettings first = EssentialsSettings.spec()
            .defaults()
            .get();
        EssentialsSettings second = EssentialsSettings.spec()
            .defaults()
            .get();

        first.homes.defaultMax = 9;

        assertEquals(3, second.homes.defaultMax);
        assertEquals(3, EssentialsSettings.defaults().homes.defaultMax);
    }
}
