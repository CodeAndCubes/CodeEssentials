package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.store.JsonPlayerDataStore;

public final class SharedSettings {

    public static final int FALLBACK_AUTOSAVE_SECONDS = 30;

    private static final SharedSettings DEFAULTS = new SharedSettings(
        new EssentialsSection(),
        new StorageSettings(JsonPlayerDataStore.ID, FALLBACK_AUTOSAVE_SECONDS),
        new AuditSettings(true, false));

    private final EssentialsSection section;
    private final StorageSettings storage;
    private final AuditSettings audit;

    public SharedSettings(EssentialsSection section, StorageSettings storage, AuditSettings audit) {
        this.section = section;
        this.storage = storage;
        this.audit = audit;
    }

    public static SharedSettings of(ConfigService configs, EssentialsSection section) {
        return new SharedSettings(
            section,
            configs.storage(ConfigRoles.ESSENTIALS),
            configs.audit(ConfigRoles.ESSENTIALS));
    }

    public static SharedSettings defaults() {
        return DEFAULTS;
    }

    public String provider() {
        return storage.provider();
    }

    public int autosaveSeconds() {
        return Math.max(1, storage.autosaveSeconds());
    }

    public int autosaveTicks() {
        return autosaveSeconds() * EngineRules.TICKS_PER_SECOND;
    }

    public boolean logChanges() {
        return audit.logChanges();
    }

    public boolean logChecks() {
        return audit.logChecks();
    }

    public int defaultHomes(EssentialsLimits ceilings) {
        return ceilings.clampHomes(section.homes);
    }

    public int warmupSeconds(EssentialsLimits ceilings) {
        return ceilings.clampWarmupSeconds(section.warmupSeconds);
    }

    public int cooldownSeconds(TeleportCause cause) {
        switch (cause) {
            case HOME:
                return atLeastZero(section.cooldowns.home);
            case SPAWN:
                return atLeastZero(section.cooldowns.spawn);
            case WARP:
                return atLeastZero(section.cooldowns.warp);
            case BACK:
                return atLeastZero(section.cooldowns.back);
            case TPA:
                return atLeastZero(section.cooldowns.tpa);
            default:
                return 0;
        }
    }

    private static int atLeastZero(int seconds) {
        return Math.max(0, seconds);
    }
}
