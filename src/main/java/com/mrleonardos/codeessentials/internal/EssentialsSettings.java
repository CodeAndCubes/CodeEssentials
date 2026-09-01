package com.mrleonardos.codeessentials.internal;

import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;

@Comment({ "Редкие настройки перемещений CodeEssentials.",
    "Число домов, прогрев и кулдауны лежат в главном файле config/code/config.toml, секция [essentials]." })
public final class EssentialsSettings {

    public static final String MODID = "codeessentials";

    public static final int SETTINGS_VERSION = 1;

    public static final String META_MAX_HOMES = "codeessentials.maxhomes";
    public static final String META_WARMUP = "codeessentials.warmup";
    public static final String META_BACK_DEPTH = "codeessentials.backdepth";

    public static final String DEFAULT_POLICY = "builtin";

    public static final double DEFAULT_WARMUP_MOVE_RADIUS = 2.0D;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_REQUEST_RATE_SECONDS = 10;
    public static final int DEFAULT_BACK_DEPTH = 1;

    public static final String BACK_NONE = "NONE";
    public static final String BACK_TELEPORT = "TELEPORT";
    public static final String BACK_DEATH = "DEATH";
    public static final String BACK_BOTH = "BOTH";

    private static final double VERTICAL_SHARE = 0.5D;

    private static final EssentialsSettings DEFAULTS = new EssentialsSettings();

    public Teleport teleport = new Teleport();
    public SafeSpot safeSpot = new SafeSpot();
    public Back back = new Back();
    public Requests requests = new Requests();
    public Limits limits = new Limits();

    public static EssentialsSettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<EssentialsSettings> spec() {
        return ConfigSpec.settings(MODID, EssentialsSettings.class)
            .role(ConfigRoles.ESSENTIALS)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SETTINGS_VERSION)
            .defaults(EssentialsSettings::new)
            .validator(EssentialsSettings::heal)
            .build();
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

    public String policy() {
        return trimmed(safeSpot.policy, DEFAULT_POLICY);
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

    public SafeSpotLimits spotLimits(EssentialsLimits ceilings) {
        return SafeSpotLimits
            .of(safeSpot.maxUp, safeSpot.maxDown, ceilings.clampSafeSpotRadius(safeSpot.radius), safeSpot.liquidOk);
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
        if (settings.teleport == null) {
            settings.teleport = new Teleport();
        }
        if (settings.safeSpot == null) {
            settings.safeSpot = new SafeSpot();
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
    }

    private static String trimmed(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.trim();
        return text.isEmpty() ? fallback : text;
    }

    @Comment("Поведение прогрева. Сколько он длится, сказано в главном файле.")
    public static final class Teleport {

        @Comment({ "На сколько блоков игрок вправе сдвинуться за время прогрева.",
            "По высоте разрешена половина этого числа." })
        public double warmupMoveRadius = DEFAULT_WARMUP_MOVE_RADIUS;

        @Comment("Снимать ли перенос, если игроку успели нанести урон.")
        public boolean warmupCancelOnDamage = true;

        @Comment({ "Догружать ли чанк цели, когда его нет на диске.",
            "Ложь бережёт диск: перенос в незнакомую даль просто не состоится." })
        public boolean generateChunks = false;
    }

    @Comment("Поиск безопасной точки рядом с целью.")
    public static final class SafeSpot {

        @Comment({ "Имя политики поиска. Встроена \"builtin\", остальные приносят моды.",
            "Незнакомое имя уводит на встроенную с записью в лог." })
        public String policy = DEFAULT_POLICY;

        @Comment("На сколько блоков вверх поиск поднимается от цели.")
        public int maxUp = SafeSpotLimits.DEFAULT_MAX_UP;

        @Comment("На сколько блоков вниз поиск опускается от цели.")
        public int maxDown = SafeSpotLimits.DEFAULT_MAX_DOWN;

        @Comment("Радиус поиска по горизонтали в блоках.")
        public int radius = SafeSpotLimits.DEFAULT_RADIUS;

        @Comment("Считать ли воду и лаву годным местом для приземления.")
        public boolean liquidOk = false;
    }

    @Comment("Что попадает в стек возврата команды /back.")
    public static final class Back {

        @Comment({ "NONE ничего, TELEPORT только переносы, DEATH только места гибели, BOTH и то и другое.",
            "Незнакомое слово читается как BOTH." })
        public String on = BACK_BOTH;
    }

    @Comment("Просьбы о переносе: /tpa и /tpahere.")
    public static final class Requests {

        @Comment("Через сколько секунд неотвеченная просьба пропадает.")
        public int timeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

        @Comment("Сколько просьб одновременно висит у одного игрока.")
        public int maxPending = EssentialsLimits.DEFAULT_PENDING_REQUESTS;

        @Comment({ "Пауза между двумя просьбами одного игрока в секундах.",
            "Списывается при отправке, а не при переносе. Ноль снимает." })
        public int rateSeconds = DEFAULT_REQUEST_RATE_SECONDS;
    }

    @Comment({ "Потолки, выше которых не поднять ни настройкой, ни метой игрока.",
        "Заводское значение опускается только вниз, число меньше единицы читается как незаданное." })
    public static final class Limits {

        @Comment("Длина имени дома и варпа в символах.")
        public int nameLength = EssentialsLimits.DEFAULT_NAME_LENGTH;

        @Comment("Домов у одного игрока. Сколько их без меты, сказано в главном файле.")
        public int homesPerPlayer = EssentialsLimits.DEFAULT_HOMES_PER_PLAYER;

        @Comment("Варпов на сервере.")
        public int warps = EssentialsLimits.DEFAULT_WARPS;

        @Comment("Просьб о переносе у одного игрока.")
        public int pendingRequests = EssentialsLimits.DEFAULT_PENDING_REQUESTS;

        @Comment("Срок жизни просьбы в секундах.")
        public int requestTimeoutSeconds = EssentialsLimits.DEFAULT_REQUEST_TIMEOUT_SECONDS;

        @Comment("Длина прогрева в секундах. Сама длина стоит в главном файле.")
        public int warmupSeconds = EssentialsLimits.DEFAULT_WARMUP_SECONDS;

        @Comment("Радиус поиска безопасной точки в блоках.")
        public int safeSpotRadius = EssentialsLimits.DEFAULT_SAFE_SPOT_RADIUS;

        @Comment("Глубина стека возврата.")
        public int backDepth = EssentialsLimits.DEFAULT_BACK_DEPTH;
    }
}
