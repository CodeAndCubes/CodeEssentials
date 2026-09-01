package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;
import com.mrleonardos.codeessentials.internal.store.StubConfigService;

class PlatformMaintenanceTest {

    @Test
    void reloadReadsTheEditedSettingsFromDisk(@TempDir Path root) {
        StubConfigService configs = new StubConfigService(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        AtomicInteger refreshed = new AtomicInteger();
        PlatformMaintenance maintenance = maintenance(configs, settings, refreshed);
        settings.get().teleport.warmupSeconds = 99;
        warmupOnDisk(settings.path(), 7);

        StoreResult reloaded = maintenance.reloadSettings();

        assertTrue(reloaded.successful(), () -> reloaded.toString());
        assertEquals(7, settings.get().teleport.warmupSeconds);
        assertEquals(1, refreshed.get());
    }

    @Test
    void theAnswerCountsWhatTheAdministratorEdits(@TempDir Path root) {
        StubConfigService configs = new StubConfigService(root);
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        ConfigFile<WarpsFile> warps = configs.open(WarpsFile.spec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());
        warps.get().warps.put("shop", new WarpsFile.Warp("0,10.5,64.0,10.5", ""));
        warps.save();

        StoreResult reloaded = maintenance.reloadSettings();

        assertEquals(
            "warmup 3s, 1 warp(s), 0 spawn point(s)",
            reloaded.message()
                .orElse(""));
    }

    @Test
    void worldStateIsNotReread(@TempDir Path root) {
        StubConfigService configs = new StubConfigService(root).bindWorld();
        ConfigFile<EssentialsSettings> settings = configs.open(EssentialsSettings.spec());
        ConfigFile<JsonObject> players = configs.open(JsonPlayerDataStore.playersSpec());
        PlatformMaintenance maintenance = maintenance(configs, settings, new AtomicInteger());
        JsonObject before = players.get();

        maintenance.reloadSettings();

        assertSame(before, players.get());
    }

    private static PlatformMaintenance maintenance(StubConfigService configs, ConfigFile<EssentialsSettings> settings,
        AtomicInteger refreshed) {
        ConfigFile<CommandRoots> roots = configs.open(CommandRoots.spec());
        ConfigFile<WarpsFile> warps = configs.open(WarpsFile.spec());
        ConfigFile<SpawnFile> spawn = configs.open(SpawnFile.spec());
        return new PlatformMaintenance(settings, roots, warps, spawn, refreshed::incrementAndGet);
    }

    private static void warmupOnDisk(Path path, int seconds) {
        JsonObject data = StubConfigService.Json.read(path);
        data.getAsJsonObject("teleport")
            .addProperty("warmupSeconds", Integer.valueOf(seconds));
        StubConfigService.Json.write(path, data);
    }
}
