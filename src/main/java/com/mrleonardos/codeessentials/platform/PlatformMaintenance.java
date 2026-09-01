package com.mrleonardos.codeessentials.platform;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.command.EssentialsMaintenance;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;

final class PlatformMaintenance implements EssentialsMaintenance {

    static final String ROOTS_NEED_RESTART = "command roots apply after a restart";

    private final ConfigFile<EssentialsSettings> settings;
    private final ConfigFile<CommandRoots> commands;
    private final ConfigFile<WarpsFile> warps;
    private final ConfigFile<SpawnFile> spawn;
    private final Runnable refresh;
    private final Logger log;

    PlatformMaintenance(ConfigFile<EssentialsSettings> settings, ConfigFile<CommandRoots> commands,
        ConfigFile<WarpsFile> warps, ConfigFile<SpawnFile> spawn, Runnable refresh, Logger log) {
        this.settings = settings;
        this.commands = commands;
        this.warps = warps;
        this.spawn = spawn;
        this.refresh = refresh;
        this.log = log;
    }

    @Override
    public StoreResult reloadSettings() {
        try {
            settings.reload();
            commands.reload();
            warps.reload();
            spawn.reload();
        } catch (RuntimeException failure) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        }
        refresh.run();
        log.info(
            "Settings are reread. Command roots are handed to the server once, at startup, so edits in {}.json "
                + "wait for a restart",
            EssentialsSettings.COMMANDS_FILE);
        return StoreResult.success(summary());
    }

    private String summary() {
        EssentialsSettings current = settings.get();
        return "warmup " + current.warmupSeconds(current.ceilings())
            + "s, "
            + warps.get().warps.size()
            + " warp(s), "
            + spawns()
            + " spawn point(s), "
            + ROOTS_NEED_RESTART;
    }

    private int spawns() {
        SpawnFile held = spawn.get();
        int count = held.dimensions == null ? 0 : held.dimensions.size();
        return held.globalSpawn == null || held.globalSpawn.isEmpty() ? count : count + 1;
    }
}
