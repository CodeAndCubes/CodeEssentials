package com.mrleonardos.codeessentials.internal;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;

public final class EssentialsSettings {

    public static final String MODID = "codeessentials";
    public static final String SETTINGS_FILE = "config";
    public static final String COMMANDS_FILE = "commands";
    public static final String WARPS_FILE = "warps";
    public static final String SPAWN_FILE = "spawn";

    public static final int SETTINGS_VERSION = 1;

    public static final String META_MAX_HOMES = "codeessentials.maxhomes";
    public static final String META_WARMUP = "codeessentials.warmup";
    public static final String META_BACK_DEPTH = "codeessentials.backdepth";

    public static final String DEFAULT_PROVIDER = "json";
    public static final String DEFAULT_POLICY = "builtin";
    public static final ServicePriority DEFAULT_SERVICE_PRIORITY = ServicePriority.ADDON;

    public static final int DEFAULT_AUTOSAVE_SECONDS = 30;
    public static final int DEFAULT_WARMUP_SECONDS = 3;
    public static final double DEFAULT_WARMUP_MOVE_RADIUS = 2.0D;
    public static final int DEFAULT_HOMES = 3;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_REQUEST_RATE_SECONDS = 10;
    public static final int DEFAULT_BACK_DEPTH = 1;

    public static final String BACK_NONE = "NONE";
    public static final String BACK_TELEPORT = "TELEPORT";
    public static final String BACK_DEATH = "DEATH";
    public static final String BACK_BOTH = "BOTH";

    private static final double VERTICAL_SHARE = 0.5D;

    private static final EssentialsSettings DEFAULTS = new EssentialsSettings();

    public String servicePriority = DEFAULT_SERVICE_PRIORITY.name();
    public Storage storage = new Storage();
    public Teleport teleport = new Teleport();
    public SafeSpot safeSpot = new SafeSpot();
    public Homes homes = new Homes();
    public Back back = new Back();
    public Requests requests = new Requests();
    public Limits limits = new Limits();
    public Audit audit = new Audit();

    public static EssentialsSettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<EssentialsSettings> spec() {
        return ConfigSpec.of(MODID, SETTINGS_FILE, EssentialsSettings.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SETTINGS_VERSION)
            .defaults(EssentialsSettings::new)
            .validator(EssentialsSettings::heal)
            .build();
    }

    public ServicePriority priority(Logger log) {
        String requested = servicePriority == null ? "" : servicePriority.trim();
        for (ServicePriority known : ServicePriority.values()) {
            if (known.name()
                .equalsIgnoreCase(requested)) {
                return known;
            }
        }
        log.warn(
            "servicePriority = {} is not one of {}, {} is used",
            servicePriority,
            Arrays.toString(ServicePriority.values()),
            DEFAULT_SERVICE_PRIORITY);
        return DEFAULT_SERVICE_PRIORITY;
    }

    public EssentialsLimits ceilings() {
        return ceilingsBuilder().build();
    }

    public EssentialsLimits ceilings(Logger log) {
        EssentialsLimits.Builder builder = ceilingsBuilder();
        EssentialsLimits ceilings = builder.build();
        for (String remark : builder.remarks()) {
            log.warn("Config ceiling is unusable: {}", remark);
        }
        return ceilings;
    }

    public String provider() {
        return trimmed(storage.playerProvider, DEFAULT_PROVIDER);
    }

    public String policy() {
        return trimmed(safeSpot.policy, DEFAULT_POLICY);
    }

    public int autosaveSeconds() {
        return Math.max(1, storage.autosaveSeconds);
    }

    public int autosaveTicks() {
        return autosaveSeconds() * 20;
    }

    public int warmupSeconds(EssentialsLimits ceilings) {
        return ceilings.clampWarmupSeconds(teleport.warmupSeconds);
    }

    public double warmupMoveRadius() {
        return Math.max(0.0D, teleport.warmupMoveRadius);
    }

    public double warmupMoveHeight() {
        return warmupMoveRadius() * VERTICAL_SHARE;
    }

    public boolean warmupCancelOnDamage() {
        return teleport.warmupCancelOnDamage;
    }

    public boolean generateChunks() {
        return teleport.generateChunks;
    }

    public int cooldownSeconds(TeleportCause cause) {
        Integer held = teleport.cooldowns.get(cause.key());
        return held == null || held.intValue() < 0 ? 0 : held.intValue();
    }

    public SafeSpotLimits spotLimits(EssentialsLimits ceilings) {
        return SafeSpotLimits
            .of(safeSpot.maxUp, safeSpot.maxDown, ceilings.clampSafeSpotRadius(safeSpot.radius), safeSpot.liquidOk);
    }

    public int defaultHomes(EssentialsLimits ceilings) {
        return ceilings.clampHomes(homes.defaultMax);
    }

    public int defaultBackDepth(EssentialsLimits ceilings) {
        return ceilings.clampBackDepth(DEFAULT_BACK_DEPTH);
    }

    public boolean backRecordsTeleports() {
        String mode = backMode();
        return BACK_BOTH.equals(mode) || BACK_TELEPORT.equals(mode);
    }

    public boolean backRecordsDeaths() {
        String mode = backMode();
        return BACK_BOTH.equals(mode) || BACK_DEATH.equals(mode);
    }

    public String backMode() {
        String requested = back.on == null ? ""
            : back.on.trim()
                .toUpperCase(Locale.ROOT);
        if (BACK_NONE.equals(requested) || BACK_TELEPORT.equals(requested)
            || BACK_DEATH.equals(requested)
            || BACK_BOTH.equals(requested)) {
            return requested;
        }
        return BACK_BOTH;
    }

    public String backMode(Logger log) {
        String mode = backMode();
        if (back.on != null && !mode.equalsIgnoreCase(back.on.trim())) {
            log.warn("back.on = {} is not one of NONE, TELEPORT, DEATH, BOTH, {} is used", back.on, mode);
        }
        return mode;
    }

    public int requestTimeoutSeconds(EssentialsLimits ceilings) {
        return ceilings.clampRequestTimeoutSeconds(requests.timeoutSeconds);
    }

    public int maxPending(EssentialsLimits ceilings) {
        return ceilings.clampPendingRequests(requests.maxPending);
    }

    public int requestRateSeconds() {
        return Math.max(0, requests.rateSeconds);
    }

    public boolean logChanges() {
        return audit.logChanges;
    }

    public boolean logChecks() {
        return audit.logChecks;
    }

    private EssentialsLimits.Builder ceilingsBuilder() {
        return EssentialsLimits.builder()
            .nameLength(limits.nameLength)
            .homesPerPlayer(limits.homesPerPlayer)
            .warps(limits.warps)
            .pendingRequests(limits.pendingRequests)
            .requestTimeoutSeconds(limits.requestTimeoutSeconds)
            .warmupSeconds(limits.warmupSeconds)
            .safeSpotRadius(limits.safeSpotRadius)
            .backDepth(limits.backDepth);
    }

    private static void heal(EssentialsSettings settings) {
        if (settings.storage == null) {
            settings.storage = new Storage();
        }
        if (settings.teleport == null) {
            settings.teleport = new Teleport();
        }
        if (settings.teleport.cooldowns == null) {
            settings.teleport.cooldowns = factoryCooldowns();
        }
        if (settings.safeSpot == null) {
            settings.safeSpot = new SafeSpot();
        }
        if (settings.homes == null) {
            settings.homes = new Homes();
        }
        if (settings.back == null) {
            settings.back = new Back();
        }
        if (settings.requests == null) {
            settings.requests = new Requests();
        }
        if (settings.limits == null) {
            settings.limits = new Limits();
        }
        if (settings.audit == null) {
            settings.audit = new Audit();
        }
    }

    private static Map<String, Integer> factoryCooldowns() {
        Map<String, Integer> zeroes = new LinkedHashMap<>();
        zeroes.put(TeleportCause.HOME.key(), Integer.valueOf(0));
        zeroes.put(TeleportCause.SPAWN.key(), Integer.valueOf(0));
        zeroes.put(TeleportCause.WARP.key(), Integer.valueOf(0));
        zeroes.put(TeleportCause.BACK.key(), Integer.valueOf(0));
        zeroes.put(TeleportCause.TPA.key(), Integer.valueOf(0));
        return zeroes;
    }

    private static String trimmed(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.trim();
        return text.isEmpty() ? fallback : text;
    }

    public static final class Storage {

        public String playerProvider = DEFAULT_PROVIDER;
        public int autosaveSeconds = DEFAULT_AUTOSAVE_SECONDS;
    }

    public static final class Teleport {

        public int warmupSeconds = DEFAULT_WARMUP_SECONDS;
        public double warmupMoveRadius = DEFAULT_WARMUP_MOVE_RADIUS;
        public boolean warmupCancelOnDamage = true;
        public boolean generateChunks = false;
        public Map<String, Integer> cooldowns = factoryCooldowns();
    }

    public static final class SafeSpot {

        public String policy = DEFAULT_POLICY;
        public int maxUp = SafeSpotLimits.DEFAULT_MAX_UP;
        public int maxDown = SafeSpotLimits.DEFAULT_MAX_DOWN;
        public int radius = SafeSpotLimits.DEFAULT_RADIUS;
        public boolean liquidOk = false;
    }

    public static final class Homes {

        public int defaultMax = DEFAULT_HOMES;
    }

    public static final class Back {

        public String on = BACK_BOTH;
    }

    public static final class Requests {

        public int timeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;
        public int maxPending = EssentialsLimits.DEFAULT_PENDING_REQUESTS;
        public int rateSeconds = DEFAULT_REQUEST_RATE_SECONDS;
    }

    public static final class Limits {

        public int nameLength = EssentialsLimits.DEFAULT_NAME_LENGTH;
        public int homesPerPlayer = EssentialsLimits.DEFAULT_HOMES_PER_PLAYER;
        public int warps = EssentialsLimits.DEFAULT_WARPS;
        public int pendingRequests = EssentialsLimits.DEFAULT_PENDING_REQUESTS;
        public int requestTimeoutSeconds = EssentialsLimits.DEFAULT_REQUEST_TIMEOUT_SECONDS;
        public int warmupSeconds = EssentialsLimits.DEFAULT_WARMUP_SECONDS;
        public int safeSpotRadius = EssentialsLimits.DEFAULT_SAFE_SPOT_RADIUS;
        public int backDepth = EssentialsLimits.DEFAULT_BACK_DEPTH;
    }

    public static final class Audit {

        public boolean logChanges = true;
        public boolean logChecks = false;
    }
}
