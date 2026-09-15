package com.mrleonardos.codeessentials.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.RandomRules;

@Comment({ "Редкие настройки перемещений CodeEssentials.",
    "Число домов, прогрев и кулдауны лежат в главном файле config/code/config.toml, секция [essentials]." })
public final class EssentialsSettings {

    public static final String MODID = "codeessentials";

    public static final int SETTINGS_VERSION = 1;

    public static final String META_MAX_HOMES = "codeessentials.maxhomes";
    public static final String META_WARMUP = "codeessentials.warmup";
    public static final String META_BACK_DEPTH = "codeessentials.backdepth";

    public static final String DEFAULT_POLICY = "builtin";

    public static final int DEFAULT_BACK_DEPTH = 1;

    public static final String BACK_NONE = "NONE";
    public static final String BACK_TELEPORT = "TELEPORT";
    public static final String BACK_DEATH = "DEATH";
    public static final String BACK_BOTH = "BOTH";

    public static final String CENTER_SPAWN = "spawn";
    public static final String CENTER_POINT = "point";

    public static final String FAILURE_REFUSE = "refuse";
    public static final String FAILURE_SPAWN = "spawn";

    private static final double VERTICAL_SHARE = 0.5D;

    private static final EssentialsSettings DEFAULTS = new EssentialsSettings();

    public Teleport teleport = new Teleport();
    public SafeSpot safeSpot = new SafeSpot();
    public Back back = new Back();
    public Requests requests = new Requests();
    public Rtp rtp = new Rtp();
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

    public RandomRules randomRules(EssentialsLimits ceilings) {
        return RandomRules.builder()
            .enabled(rtp.enabled)
            .radius(rtp.minRadius, rtp.maxRadius)
            .center(CENTER_SPAWN.equals(centerMode()), rtp.centerX, rtp.centerZ)
            .worlds(rtp.worlds)
            .blockedBiomes(rtp.blockedBiomes)
            .attempts(ceilings.clampRandomAttempts(rtp.attempts))
            .fallbackToSpawn(FAILURE_SPAWN.equals(failureMode()))
            .build();
    }

    public String centerMode() {
        return word(rtp.center, CENTER_SPAWN, CENTER_SPAWN, CENTER_POINT);
    }

    public String centerMode(Logger log) {
        return told(log, "rtp.center", rtp.center, centerMode(), CENTER_SPAWN + ", " + CENTER_POINT);
    }

    public String failureMode() {
        return word(rtp.onFailure, FAILURE_REFUSE, FAILURE_REFUSE, FAILURE_SPAWN);
    }

    public String failureMode(Logger log) {
        return told(log, "rtp.onFailure", rtp.onFailure, failureMode(), FAILURE_REFUSE + ", " + FAILURE_SPAWN);
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
            .backDepth(limits.backDepth)
            .randomAttempts(limits.randomAttempts);
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
        if (settings.rtp == null) {
            settings.rtp = new Rtp();
        }
        if (settings.rtp.worlds == null) {
            settings.rtp.worlds = Rtp.factoryWorlds();
        }
        if (settings.rtp.blockedBiomes == null) {
            settings.rtp.blockedBiomes = Rtp.factoryBiomes();
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

    private static String word(String value, String fallback, String... allowed) {
        String text = value == null ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(text)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static String told(Logger log, String field, String written, String chosen, String allowed) {
        if (written != null && !chosen.equalsIgnoreCase(written.trim())) {
            log.warn("{} = {} is not one of {}, {} is used", field, written, allowed, chosen);
        }
        return chosen;
    }

    @Comment("Поведение прогрева. Сколько он длится, сказано в главном файле.")
    public static final class Teleport {

        @Comment({ "На сколько блоков игрок вправе сдвинуться за время прогрева.",
            "По высоте разрешена половина этого числа." })
        public double warmupMoveRadius = EngineRules.DEFAULT_MOVE_RADIUS;

        @Comment("Снимать ли перенос, если игроку успели нанести урон.")
        public boolean warmupCancelOnDamage = true;

        @Comment({ "Догружать ли чанк цели, когда его нет на диске.",
            "Ложь бережёт диск: перенос в незнакомую даль просто не состоится." })
        public boolean generateChunks = false;
    }

    @Comment("Поиск безопасной точки рядом с целью.")
    public static final class SafeSpot {

        @Comment({ "Имя политики поиска. Встроена \"builtin\", остальные приносят моды.",
            "Незнакомое имя выключает поиск: перенос идёт по прямым координатам, в лог уходит строка." })
        public String policy = DEFAULT_POLICY;

        @Comment("На сколько блоков вверх поиск поднимается от цели.")
        public int maxUp = SafeSpotLimits.DEFAULT_MAX_UP;

        @Comment("На сколько блоков вниз поиск опускается от цели.")
        public int maxDown = SafeSpotLimits.DEFAULT_MAX_DOWN;

        @Comment("Радиус поиска по горизонтали в блоках.")
        public int radius = SafeSpotLimits.DEFAULT_RADIUS;

        @Comment({ "Считать ли воду и лаву годным местом для приземления.",
            "Случайный перенос этого не спрашивает: точку он выбирает сам и в жидкость не сажает никогда." })
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
        public int timeoutSeconds = EngineRules.DEFAULT_REQUEST_TIMEOUT_SECONDS;

        @Comment("Сколько просьб одновременно висит у одного игрока.")
        public int maxPending = EssentialsLimits.DEFAULT_PENDING_REQUESTS;

        @Comment({ "Пауза между двумя просьбами одного игрока в секундах.",
            "Списывается при отправке, а не при переносе. Ноль снимает." })
        public int rateSeconds = EngineRules.DEFAULT_REQUEST_RATE_SECONDS;
    }

    @Comment({ "Случайный перенос /rtp: кольцо вокруг центра, отсев биомов и число попыток.",
        "Пауза между вызовами стоит в главном файле, [essentials.cooldowns] random." })
    public static final class Rtp {

        @Comment({ "Работает ли команда. Ложь оставляет корень на месте и отвечает игроку отказом.",
            "Освободить само имя команды для чужого мода можно в essentials-commands.toml." })
        public boolean enabled = true;

        @Comment("Ближняя граница кольца в блоках от центра.")
        public int minRadius = RandomRules.DEFAULT_MIN_RADIUS;

        @Comment("Дальняя граница кольца. Значение ниже ближней подтягивается до неё.")
        public int maxRadius = RandomRules.DEFAULT_MAX_RADIUS;

        @Comment({ "Центр кольца: spawn берёт точку спавна измерения, point берёт centerX и centerZ.",
            "Точки спавна нет: центром становятся centerX и centerZ. Незнакомое слово читается как spawn." })
        public String center = CENTER_SPAWN;

        @Comment("Координата x центра, когда центр задан точкой.")
        public int centerX;

        @Comment("Координата z центра, когда центр задан точкой.")
        public int centerZ;

        @Comment("Измерения, где команда работает. Пустой список разрешает любое.")
        public List<Integer> worlds = factoryWorlds();

        @Comment({ "Биомы, куда не переносить. Имя пишется как в игре, регистр и пробелы неважны.",
            "Мир, который биом не называет, отсев не проходит вовсе: точку решает поиск безопасного места." })
        public List<String> blockedBiomes = factoryBiomes();

        @Comment({ "Сколько точек перебирать за один вызов. Потолок стоит в limits.randomAttempts.",
            "Каждая попытка вправе поднять один чанк, поэтому число тут дороже, чем выглядит." })
        public int attempts = RandomRules.DEFAULT_ATTEMPTS;

        @Comment({ "Что делать, когда точка не нашлась: refuse отвечает отказом, spawn уносит на спавн.",
            "Уход на спавн это состоявшийся перенос, кулдаун за него списывается." })
        public String onFailure = FAILURE_REFUSE;

        static List<Integer> factoryWorlds() {
            return new ArrayList<>(Arrays.asList(Integer.valueOf(RandomRules.DEFAULT_WORLD)));
        }

        static List<String> factoryBiomes() {
            return new ArrayList<>(Arrays.asList("Ocean", "Deep Ocean", "Frozen Ocean", "River", "Frozen River"));
        }
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
        public int requestTimeoutSeconds = EssentialsLimits.REQUEST_TIMEOUT_SECONDS_CEILING;

        @Comment("Длина прогрева в секундах. Сама длина стоит в главном файле.")
        public int warmupSeconds = EssentialsLimits.WARMUP_SECONDS_CEILING;

        @Comment("Радиус поиска безопасной точки в блоках.")
        public int safeSpotRadius = EssentialsLimits.DEFAULT_SAFE_SPOT_RADIUS;

        @Comment("Глубина стека возврата.")
        public int backDepth = EssentialsLimits.DEFAULT_BACK_DEPTH;

        @Comment("Попыток найти точку у случайного переноса. Само число стоит в rtp.attempts.")
        public int randomAttempts = EssentialsLimits.DEFAULT_RANDOM_ATTEMPTS;
    }
}
