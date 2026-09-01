package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.config.ConfigFormat;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;

class EssentialsSettingsTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");

    @Test
    void theFreshFileCarriesTheValuesFromTheDesign() {
        EssentialsSettings settings = new EssentialsSettings();

        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());
        assertEquals(2.0D, settings.warmupMoveRadius(), 0.0D);
        assertEquals(1.0D, settings.warmupMoveHeight(), 0.0D);
        assertTrue(settings.warmupCancelOnDamage());
        assertFalse(settings.generateChunks());
        assertEquals(EssentialsSettings.BACK_BOTH, settings.backMode());
        assertEquals(60, settings.requestTimeoutSeconds(settings.ceilings()));
        assertEquals(8, settings.maxPending(settings.ceilings()));
        assertEquals(10, settings.requestRateSeconds());
        assertEquals(SafeSpotLimits.defaults(), settings.spotLimits(settings.ceilings()));
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
    void anEmptyPolicyNameFallsBackToTheBuiltinOne() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.safeSpot.policy = "   ";

        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());

        settings.safeSpot.policy = null;
        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());
    }

    @Test
    void theSpecPointsAtTheOwnFileOfTheRole() {
        ConfigSpec<EssentialsSettings> spec = EssentialsSettings.spec();

        assertEquals(EssentialsSettings.MODID, spec.modid());
        assertEquals(ConfigSpec.OWN_NAME, spec.name(), "essentials.toml принадлежит владельцу целиком");
        assertEquals(ConfigRoles.ESSENTIALS, spec.role());
        assertEquals(ConfigScope.SETTINGS, spec.scope());
        assertEquals(ConfigFormat.TOML, spec.format());
        assertEquals(EssentialsSettings.SETTINGS_VERSION, spec.schemaVersion());
    }

    @Test
    void aFileEditedIntoNullSectionsIsHealedBeforeUse() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.teleport = null;
        settings.safeSpot = null;
        settings.back = null;
        settings.requests = null;
        settings.limits = null;

        EssentialsSettings.spec()
            .validator()
            .accept(settings);

        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());
        assertEquals(EssentialsSettings.BACK_BOTH, settings.backMode());
        assertEquals(10, settings.requestRateSeconds());
    }

    @Test
    void theDefaultsSupplierMakesAFreshObjectEveryTime() {
        EssentialsSettings first = EssentialsSettings.spec()
            .defaults()
            .get();
        EssentialsSettings second = EssentialsSettings.spec()
            .defaults()
            .get();

        first.limits.homesPerPlayer = 9;

        assertEquals(EssentialsLimits.DEFAULT_HOMES_PER_PLAYER, second.limits.homesPerPlayer);
        assertEquals(EssentialsLimits.DEFAULT_HOMES_PER_PLAYER, EssentialsSettings.defaults().limits.homesPerPlayer);
    }
}
