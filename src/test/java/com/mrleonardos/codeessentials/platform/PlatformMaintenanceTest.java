package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSection;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.service.KitsFile;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;

class PlatformMaintenanceTest {

    @Test
    void reloadReadsTheEditedSettingsFromDisk(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        AtomicInteger refreshed = new AtomicInteger();
        PlatformMaintenance maintenance = maintenance(configs, settings, refreshed);
        settings.get().requests.timeoutSeconds = 99;
        timeoutOnDisk(settings.path(), 7);

        StoreResult reloaded = maintenance.reloadSettings();

        assertTrue(reloaded.successful(), () -> reloaded.toString());
        assertEquals(7, settings.get().requests.timeoutSeconds);
        assertEquals(1, refreshed.get());
    }

    @Test
    void reloadReadsTheSectionOfTheMainFile(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        ConfigFile<EssentialsSection> section = configs.section(EssentialsSection.spec());
        PlatformMaintenance maintenance = maintenance(configs, settings, section, new AtomicInteger());
        TestConfigs.writeMain(root, "schemaVersion = 1", "", "[essentials]", "homes = 9", "warmupSeconds = 11");

        assertTrue(
            maintenance.reloadSettings()
                .successful());

        assertEquals(9, section.get().homes, "число домов живёт в главном файле и перечитывается вместе с ним");
        assertEquals(11, section.get().warmupSeconds);
    }

    @Test
    void theAnswerCountsWhatTheAdministratorEdits(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        ConfigFile<WarpsFile> warps = configs.open(WarpsFile.spec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());
        warps.get().warps.put("shop", new WarpsFile.Warp("0,10.5,64.0,10.5", ""));
        warps.save();

        StoreResult reloaded = maintenance.reloadSettings();

        assertEquals(
            "warmup 3s, 1 warp(s), 0 kit(s), 0 spawn point(s), " + PlatformMaintenance.ROOTS_NEED_RESTART,
            reloaded.message()
                .orElse(""),
            "правки корней команд ждут перезапуска, и ответ обязан об этом сказать");
    }

    @Test
    void aChangedRoleOwnerIsNamedAsWaitingForTheRestart(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());
        TestConfigs.writeMain(root, "schemaVersion = 1", "", "[owners]", "essentials = \"off\"");

        StoreResult reloaded = maintenance.reloadSettings();

        assertTrue(
            reloaded.message()
                .orElse("")
                .endsWith(PlatformMaintenance.OWNER_NEEDS_RESTART),
            () -> "смена владельца роли ждёт рестарта, и ответ обязан это сказать: " + reloaded);
    }

    @Test
    void anUntouchedRoleOwnerIsNotMentioned(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());

        StoreResult reloaded = maintenance.reloadSettings();

        assertTrue(
            reloaded.message()
                .orElse("")
                .endsWith(PlatformMaintenance.ROOTS_NEED_RESTART),
            () -> reloaded.toString());
    }

    @Test
    void worldStateIsNotReread(@TempDir Path root) {
        ConfigService configs = TestConfigs.of(root);
        TestConfigs.attachWorld(configs, root.resolve(TestConfigs.WORLD));
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        ConfigFile<JsonObject> players = configs.open(JsonPlayerDataStore.playersSpec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());
        JsonObject before = players.get();

        maintenance.reloadSettings();

        assertSame(before, players.get());
    }

    private static PlatformMaintenance maintenance(ConfigService configs, ConfigFile<EssentialsSettings> settings,
        AtomicInteger refreshed) {
        return maintenance(configs, settings, configs.section(EssentialsSection.spec()), refreshed);
    }

    private static PlatformMaintenance maintenance(ConfigService configs, ConfigFile<EssentialsSettings> settings,
        ConfigFile<EssentialsSection> section, AtomicInteger refreshed) {
        ConfigFile<CommandRoots> roots = configs.open(CommandRoots.spec());
        ConfigFile<WarpsFile> warps = configs.open(WarpsFile.spec());
        ConfigFile<SpawnFile> spawn = configs.open(SpawnFile.spec());
        ConfigFile<KitsFile> kits = configs.open(KitsFile.spec());
        return new PlatformMaintenance(
            configs,
            settings,
            section,
            roots,
            warps,
            spawn,
            kits,
            EngineRules::defaults,
            refreshed::incrementAndGet,
            LogManager.getLogger("codeessentials-test"));
    }

    private static void timeoutOnDisk(Path path, int seconds) {
        String text = TestConfigs.read(path);
        StringBuilder edited = new StringBuilder();
        for (String line : text.split("\\R")) {
            edited.append(
                line.trim()
                    .startsWith("timeoutSeconds") ? "timeoutSeconds = " + seconds : line)
                .append('\n');
        }
        TestConfigs.write(path, edited.toString());
    }
}
