package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.store.StubConfigService;

class SharedSettingsTest {

    @Test
    void theFreshMainFileCarriesTheValuesFromTheDesign() {
        SharedSettings shared = SharedSettings.defaults();

        assertEquals("json", shared.provider());
        assertEquals(30, shared.autosaveSeconds());
        assertEquals(600, shared.autosaveTicks());
        assertEquals(3, shared.defaultHomes(EssentialsLimits.defaults()));
        assertEquals(3, shared.warmupSeconds(EssentialsLimits.defaults()));
        assertTrue(shared.logChanges());
        assertFalse(shared.logChecks());
    }

    @Test
    void everyCauseStartsWithoutACooldown() {
        SharedSettings shared = SharedSettings.defaults();

        for (TeleportCause cause : TeleportCause.values()) {
            assertEquals(0, shared.cooldownSeconds(cause), cause.name());
        }
    }

    @Test
    void aCooldownFromTheMainFileIsReadByItsCause() {
        EssentialsSection section = new EssentialsSection();
        section.cooldowns.home = 45;
        section.cooldowns.warp = -5;
        SharedSettings shared = SharedFixtures.of(section);

        assertEquals(45, shared.cooldownSeconds(TeleportCause.HOME));
        assertEquals(0, shared.cooldownSeconds(TeleportCause.WARP), "минус не превращается в бесконечность");
        assertEquals(0, shared.cooldownSeconds(TeleportCause.SPAWN));
    }

    @Test
    void theNumberOfHomesAndTheCeilingOfHomesAreDifferentThings() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.homesPerPlayer = 10;
        EssentialsLimits ceilings = settings.ceilings();

        assertEquals(
            3,
            SharedFixtures.homes(3)
                .defaultHomes(ceilings),
            "в главном файле стоит, сколько домов у игрока");
        assertEquals(
            10,
            SharedFixtures.homes(50)
                .defaultHomes(ceilings),
            "потолок мода не даёт поднять число домов ни настройкой, ни метой");
        assertEquals(10, ceilings.homesPerPlayer());
    }

    @Test
    void theWarmupAndTheCeilingOfTheWarmupAreDifferentThings() {
        EssentialsSettings settings = new EssentialsSettings();
        settings.limits.warmupSeconds = 12;
        EssentialsLimits ceilings = settings.ceilings();
        EssentialsSection section = new EssentialsSection();

        section.warmupSeconds = 5;
        assertEquals(
            5,
            SharedFixtures.of(section)
                .warmupSeconds(ceilings));

        section.warmupSeconds = 90;
        assertEquals(
            12,
            SharedFixtures.of(section)
                .warmupSeconds(ceilings),
            "прогрев дольше потолка мода не бывает");
    }

    @Test
    void theRoleAsksForItsOwnStorageAndAudit(@TempDir Path root) {
        StubConfigService configs = new StubConfigService(root);
        JsonObject storage = new JsonObject();
        storage.addProperty("provider", "sql");
        storage.addProperty("autosaveSeconds", Integer.valueOf(120));
        JsonObject own = new JsonObject();
        own.addProperty("autosaveSeconds", Integer.valueOf(5));
        storage.add("essentials", own);
        JsonObject audit = new JsonObject();
        audit.addProperty("logChanges", Boolean.FALSE);
        configs.mainJson()
            .add("storage", storage);
        configs.mainJson()
            .add("audit", audit);

        SharedSettings shared = SharedSettings.of(configs, new EssentialsSection());

        assertEquals("sql", shared.provider(), "общее значение берётся из [storage]");
        assertEquals(5, shared.autosaveSeconds(), "перекрытие [storage.essentials] сильнее общего");
        assertFalse(shared.logChanges());
        assertFalse(shared.logChecks());
    }

    @Test
    void anAutosaveBelowASecondIsReadAsASecond() {
        assertEquals(
            1,
            SharedFixtures.storage("json", 0)
                .autosaveSeconds());
        assertEquals(
            20,
            SharedFixtures.storage("json", 0)
                .autosaveTicks());
    }
}
