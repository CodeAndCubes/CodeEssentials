package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigFormat;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.internal.engine.RandomRules;

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

        RandomRules random = settings.randomRules(settings.ceilings());
        assertTrue(random.enabled());
        assertEquals(RandomRules.DEFAULT_MIN_RADIUS, random.minRadius());
        assertEquals(RandomRules.DEFAULT_MAX_RADIUS, random.maxRadius());
        assertTrue(random.centerAtSpawn());
        assertEquals(RandomRules.DEFAULT_ATTEMPTS, random.attempts());
        assertFalse(random.fallbackToSpawn());
        assertTrue(random.allows(0));
        assertFalse(random.allows(-1), "заводски случайный перенос работает только в обычном мире");
        assertTrue(random.blocked("Deep Ocean"), "океан заводски в чёрном списке");
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
        settings.rtp = null;
        settings.limits = null;

        EssentialsSettings.spec()
            .validator()
            .accept(settings);

        assertEquals(EssentialsSettings.DEFAULT_POLICY, settings.policy());
        assertEquals(EssentialsSettings.BACK_BOTH, settings.backMode());
        assertEquals(10, settings.requestRateSeconds());
        assertEquals(EssentialsSettings.CENTER_SPAWN, settings.centerMode());
        assertEquals(EssentialsSettings.FAILURE_REFUSE, settings.failureMode());
    }

    @Test
    void anUnknownWordOfTheRandomSectionIsSaidOutLoud() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.rtp.center = "посередине";
        settings.rtp.onFailure = "как-нибудь";

        assertEquals(EssentialsSettings.CENTER_SPAWN, settings.centerMode(LOG));
        assertEquals(EssentialsSettings.FAILURE_REFUSE, settings.failureMode(LOG));

        settings.rtp.center = "POINT";
        settings.rtp.onFailure = " Spawn ";

        assertEquals(EssentialsSettings.CENTER_POINT, settings.centerMode(LOG), "регистр слова не важен");
        assertEquals(EssentialsSettings.FAILURE_SPAWN, settings.failureMode(LOG));
    }

    @Test
    void aRandomSectionWithoutItsListsIsHealedBeforeUse() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.rtp.worlds = null;
        settings.rtp.blockedBiomes = null;

        EssentialsSettings.spec()
            .validator()
            .accept(settings);

        RandomRules random = settings.randomRules(settings.ceilings());

        assertTrue(random.allows(0));
        assertFalse(random.allows(1), "пропавший список миров возвращается заводским, а не пустым");
        assertTrue(random.blocked("Ocean"));
    }

    @Test
    void theRandomSectionSurvivesTheTripThroughTheFile(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> created = configs.open(EssentialsSettings.spec());
        assertTrue(
            TestConfigs.read(created.path())
                .contains("[rtp]"),
            "секция случайного переноса обязана появиться в заводском файле");
        TestConfigs.write(
            created.path(),
            "schemaVersion = " + EssentialsSettings.SETTINGS_VERSION,
            "",
            "[rtp]",
            "enabled = true",
            "minRadius = 250",
            "maxRadius = 750",
            "center = \"point\"",
            "centerX = 12",
            "centerZ = -34",
            "worlds = [-1, 0]",
            "blockedBiomes = [\"Deep Ocean\", \"Desert\"]",
            "attempts = 6",
            "onFailure = \"spawn\"");

        EssentialsSettings read = TestConfigs.of(root)
            .open(EssentialsSettings.spec())
            .get();
        RandomRules random = read.randomRules(read.ceilings());

        assertEquals(250, random.minRadius());
        assertEquals(750, random.maxRadius());
        assertFalse(random.centerAtSpawn());
        assertEquals(12, random.centerX());
        assertEquals(-34, random.centerZ());
        assertTrue(random.allows(-1), "список измерений читается числами, а не строками");
        assertTrue(random.allows(0));
        assertFalse(random.allows(1));
        assertTrue(random.blocked("deep ocean"));
        assertTrue(random.blocked("DESERT"));
        assertEquals(6, random.attempts());
        assertTrue(random.fallbackToSpawn());
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
