package com.mrleonardos.codeessentials.platform;

import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSection;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.command.CommandRoots;
import com.mrleonardos.codeessentials.internal.command.EssentialsMaintenance;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.service.KitServiceImpl;
import com.mrleonardos.codeessentials.internal.service.KitsFile;
import com.mrleonardos.codeessentials.internal.service.SpawnFile;
import com.mrleonardos.codeessentials.internal.service.SpawnServiceImpl;
import com.mrleonardos.codeessentials.internal.service.WarpServiceImpl;
import com.mrleonardos.codeessentials.internal.service.WarpsFile;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

final class PlatformMaintenance implements EssentialsMaintenance {

    static final String ROOTS_NEED_RESTART = "command roots apply after a restart";
    static final String OWNER_NEEDS_RESTART = "the role owner applies after a restart";

    private static final String OWNERS_SECTION = "owners";
    private static final String OWNER_AUTO = "auto";

    private final ConfigService configs;
    private final ConfigFile<EssentialsSettings> settings;
    private final ConfigFile<EssentialsSection> section;
    private final ConfigFile<CommandRoots> commands;
    private final ConfigFile<WarpsFile> warpsFile;
    private final ConfigFile<SpawnFile> spawnFile;
    private final ConfigFile<KitsFile> kitsFile;
    private final Supplier<EngineRules> rules;
    private final WarpServiceImpl warps;
    private final SpawnServiceImpl spawns;
    private final KitServiceImpl kits;
    private final SingleWriter writer;
    private final Runnable refresh;
    private final Logger log;
    private final String bootOwner;

    PlatformMaintenance(ConfigService configs, ConfigFile<EssentialsSettings> settings,
        ConfigFile<EssentialsSection> section, ConfigFile<CommandRoots> commands, ConfigFile<WarpsFile> warpsFile,
        ConfigFile<SpawnFile> spawnFile, ConfigFile<KitsFile> kitsFile, Supplier<EngineRules> rules,
        WarpServiceImpl warps, SpawnServiceImpl spawns, KitServiceImpl kits, SingleWriter writer, Runnable refresh,
        Logger log) {
        this.configs = configs;
        this.settings = settings;
        this.section = section;
        this.commands = commands;
        this.warpsFile = warpsFile;
        this.spawnFile = spawnFile;
        this.kitsFile = kitsFile;
        this.rules = rules;
        this.warps = warps;
        this.spawns = spawns;
        this.kits = kits;
        this.writer = writer;
        this.refresh = refresh;
        this.log = log;
        this.bootOwner = owner();
    }

    @Override
    public StoreResult reloadSettings() {
        try {
            settings.reload();
            section.reload();
            commands.reload();
            warpsFile.reload();
            spawnFile.reload();
            kitsFile.reload();
        } catch (RuntimeException failure) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        }
        refresh.run();
        warps.reread();
        spawns.reread();
        kits.reread();
        log.info(
            "Settings are reread. Command roots are handed to the server once, at startup, so edits in {} "
                + "wait for a restart",
            CommandRoots.FILE_NAME);
        boolean ownerMoved = ownerMoved();
        if (ownerMoved) {
            log.info(
                "Owner of the {} role became {} in the main config, the server keeps {} until a restart",
                ConfigRoles.ESSENTIALS,
                owner(),
                bootOwner);
        }
        return StoreResult.success(summary(ownerMoved));
    }

    @Override
    public boolean stateOn() {
        return writer.working();
    }

    private String summary(boolean ownerMoved) {
        String text = "warmup " + rules.get()
            .warmupSeconds()
            + "s, "
            + warps.warps()
                .size()
            + " warp(s), "
            + kits.kits()
                .size()
            + " kit(s), "
            + spawns()
            + " spawn point(s), "
            + ROOTS_NEED_RESTART;
        return ownerMoved ? text + ", " + OWNER_NEEDS_RESTART : text;
    }

    private boolean ownerMoved() {
        return !bootOwner.equalsIgnoreCase(owner());
    }

    private String owner() {
        return configs.main()
            .string(OWNERS_SECTION + "." + ConfigRoles.ESSENTIALS, OWNER_AUTO);
    }

    private int spawns() {
        SpawnTable table = spawns.table();
        return table.byDimension()
            .size()
            + (table.global()
                .isPresent() ? 1 : 0);
    }
}
