package com.mrleonardos.codeessentials.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Потолки модели: длина имени, число домов и варпов, границы задержек и радиусов.
 *
 * <p>
 * Нужны там, где значение приходит от человека или от чужого мода: команда, вызов API, разбор
 * файла. Без потолка одна команда с длинным хвостом записывает в хранилище мегабайт мусора, а
 * радиус поиска в тысячу блоков останавливает сервер на минуту.
 *
 * <p>
 * Заводские значения это верхняя граница. Через конфиг потолки меняются только вниз:
 * {@link #loweredTo(EssentialsLimits)} берёт минимум по каждому полю. Значение меньше единицы
 * потолком быть не может, поэтому считается незаданным и заменяется заводским с замечанием в
 * {@link Builder#remarks()}: опечатка в конфиге не роняет мод и не отбирает у игроков дома молча.
 *
 * <p>
 * У задержек и числа запросов есть ещё и нижняя граница, она не настраивается: кулдаун ожидания
 * ответа в одну секунду и ноль запросов в доске делают tpa неработающим, а не гибким.
 */
public final class EssentialsLimits {

    /** Длина имени дома и варпа в символах. */
    public static final int DEFAULT_NAME_LENGTH = 32;

    /** Шаблон имени дома и варпа. Из имени строятся ноды и ключи хранилища, поэтому он узкий. */
    public static final String NAME_PATTERN = "[a-z0-9_-]{1," + DEFAULT_NAME_LENGTH + "}";

    /** Сколько домов допускается у одного игрока. */
    public static final int DEFAULT_HOMES_PER_PLAYER = 128;

    /** Сколько варпов хранится всего. */
    public static final int DEFAULT_WARPS = 512;

    /** Сколько tpa-запросов ждут ответа у одного игрока. */
    public static final int DEFAULT_PENDING_REQUESTS = 8;

    /** Сколько секунд tpa-запрос ждёт ответа, потолок. */
    public static final int REQUEST_TIMEOUT_SECONDS_CEILING = 3600;

    /** Сколько секунд длится тёплая задержка, потолок. */
    public static final int WARMUP_SECONDS_CEILING = 300;

    /** Радиус колец поиска безопасной точки в блоках. */
    public static final int DEFAULT_SAFE_SPOT_RADIUS = 8;

    /** Глубина стека возврата. */
    public static final int DEFAULT_BACK_DEPTH = 10;

    /** Сколько точек перебирает случайный перенос за один вызов. */
    public static final int DEFAULT_RANDOM_ATTEMPTS = 32;

    /** Меньше одного запроса в доске означает выключенный tpa, а не настройку. */
    public static final int MIN_PENDING_REQUESTS = 1;

    /** Ответить на запрос за пять секунд человек ещё успевает, за одну уже нет. */
    public static final int MIN_REQUEST_TIMEOUT_SECONDS = 5;

    /** Глубина стека возврата меньше одного означает выключенный {@code /back}, а не настройку. */
    public static final int MIN_BACK_DEPTH = 1;

    /** Ноль попыток означает выключенный {@code /rtp}, для этого есть {@code rtp.enabled}. */
    public static final int MIN_RANDOM_ATTEMPTS = 1;

    private static final Pattern NAME_SHAPE = Pattern.compile("[a-z0-9_-]+");

    private static final EssentialsLimits DEFAULTS = new EssentialsLimits(
        DEFAULT_NAME_LENGTH,
        DEFAULT_HOMES_PER_PLAYER,
        DEFAULT_WARPS,
        DEFAULT_PENDING_REQUESTS,
        REQUEST_TIMEOUT_SECONDS_CEILING,
        WARMUP_SECONDS_CEILING,
        DEFAULT_SAFE_SPOT_RADIUS,
        DEFAULT_BACK_DEPTH,
        DEFAULT_RANDOM_ATTEMPTS);

    private final int nameLength;
    private final int homesPerPlayer;
    private final int warps;
    private final int pendingRequests;
    private final int requestTimeoutSeconds;
    private final int warmupSeconds;
    private final int safeSpotRadius;
    private final int backDepth;
    private final int randomAttempts;

    private EssentialsLimits(int nameLength, int homesPerPlayer, int warps, int pendingRequests,
        int requestTimeoutSeconds, int warmupSeconds, int safeSpotRadius, int backDepth, int randomAttempts) {
        this.nameLength = nameLength;
        this.homesPerPlayer = homesPerPlayer;
        this.warps = warps;
        this.pendingRequests = pendingRequests;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.warmupSeconds = warmupSeconds;
        this.safeSpotRadius = safeSpotRadius;
        this.backDepth = backDepth;
        this.randomAttempts = randomAttempts;
    }

    /** Заводские потолки. */
    public static EssentialsLimits defaults() {
        return DEFAULTS;
    }

    /** Начать собирать потолки из значений конфига. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Проверить имя дома или варпа по заводскому потолку.
     *
     * @param field что за имя, попадает в текст ошибки
     * @return то же имя
     * @throws IllegalArgumentException если имя не подходит под {@link #NAME_PATTERN}
     */
    public static String checkedName(String name, String field) {
        Objects.requireNonNull(name, field);
        if (!DEFAULTS.acceptsName(name)) {
            throw new IllegalArgumentException(field + " must match " + NAME_PATTERN + ": " + name);
        }
        return name;
    }

    /**
     * Те же потолки, ужатые до указанных.
     *
     * <p>
     * По каждому полю берётся меньшее из двух значений, поэтому конфиг способен опустить потолок, но
     * не поднять его выше заводского.
     */
    public EssentialsLimits loweredTo(EssentialsLimits requested) {
        return new EssentialsLimits(
            Math.min(nameLength, requested.nameLength),
            Math.min(homesPerPlayer, requested.homesPerPlayer),
            Math.min(warps, requested.warps),
            Math.min(pendingRequests, requested.pendingRequests),
            Math.min(requestTimeoutSeconds, requested.requestTimeoutSeconds),
            Math.min(warmupSeconds, requested.warmupSeconds),
            Math.min(safeSpotRadius, requested.safeSpotRadius),
            Math.min(backDepth, requested.backDepth),
            Math.min(randomAttempts, requested.randomAttempts));
    }

    /** Длина имени дома и варпа в символах. */
    public int nameLength() {
        return nameLength;
    }

    /** Сколько домов допускается у одного игрока. */
    public int homesPerPlayer() {
        return homesPerPlayer;
    }

    /** Сколько варпов хранится всего. */
    public int warps() {
        return warps;
    }

    /** Сколько tpa-запросов ждут ответа у одного игрока. */
    public int pendingRequests() {
        return pendingRequests;
    }

    /** Сколько секунд tpa-запрос ждёт ответа. */
    public int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /** Сколько секунд длится тёплая задержка. */
    public int warmupSeconds() {
        return warmupSeconds;
    }

    /** Радиус колец поиска безопасной точки в блоках. */
    public int safeSpotRadius() {
        return safeSpotRadius;
    }

    /** Глубина стека возврата. */
    public int backDepth() {
        return backDepth;
    }

    /** Сколько точек перебирает случайный перенос за один вызов. */
    public int randomAttempts() {
        return randomAttempts;
    }

    /** Проходит ли имя дома или варпа по шаблону и текущему потолку длины. */
    public boolean acceptsName(String name) {
        if (name == null || name.isEmpty() || name.length() > nameLength) {
            return false;
        }
        return NAME_SHAPE.matcher(name)
            .matches();
    }

    /** Число домов в границах потолка: отрицательное значение меты считается нулём. */
    public int clampHomes(int value) {
        return clamp(value, 0, homesPerPlayer);
    }

    /** Число ждущих запросов в границах потолка. */
    public int clampPendingRequests(int value) {
        return clamp(value, MIN_PENDING_REQUESTS, pendingRequests);
    }

    /** Срок ожидания ответа на запрос в границах потолка. */
    public int clampRequestTimeoutSeconds(int value) {
        return clamp(value, MIN_REQUEST_TIMEOUT_SECONDS, requestTimeoutSeconds);
    }

    /** Тёплая задержка в границах потолка: ноль означает перенос без задержки. */
    public int clampWarmupSeconds(int value) {
        return clamp(value, 0, warmupSeconds);
    }

    /** Радиус колец в границах потолка: ноль означает поиск только в колонне цели. */
    public int clampSafeSpotRadius(int value) {
        return clamp(value, 0, safeSpotRadius);
    }

    /** Глубина стека возврата в границах потолка. */
    public int clampBackDepth(int value) {
        return clamp(value, MIN_BACK_DEPTH, backDepth);
    }

    /** Число попыток случайного переноса в границах потолка. */
    public int clampRandomAttempts(int value) {
        return clamp(value, MIN_RANDOM_ATTEMPTS, randomAttempts);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EssentialsLimits)) {
            return false;
        }
        EssentialsLimits that = (EssentialsLimits) other;
        return nameLength == that.nameLength && homesPerPlayer == that.homesPerPlayer
            && warps == that.warps
            && pendingRequests == that.pendingRequests
            && requestTimeoutSeconds == that.requestTimeoutSeconds
            && warmupSeconds == that.warmupSeconds
            && safeSpotRadius == that.safeSpotRadius
            && backDepth == that.backDepth
            && randomAttempts == that.randomAttempts;
    }

    @Override
    public int hashCode() {
        return (((((((nameLength * 31 + homesPerPlayer) * 31 + warps) * 31 + pendingRequests) * 31
            + requestTimeoutSeconds) * 31 + warmupSeconds) * 31 + safeSpotRadius) * 31 + backDepth) * 31
            + randomAttempts;
    }

    @Override
    public String toString() {
        return "names " + nameLength
            + "ch, homes "
            + homesPerPlayer
            + ", warps "
            + warps
            + ", requests "
            + pendingRequests
            + "x"
            + requestTimeoutSeconds
            + "s, warmup "
            + warmupSeconds
            + "s, radius "
            + safeSpotRadius
            + ", back "
            + backDepth
            + ", random tries "
            + randomAttempts;
    }

    private static int clamp(int value, int floor, int ceiling) {
        return Math.max(floor, Math.min(value, ceiling));
    }

    /** Сборщик потолков из значений конфига. */
    public static final class Builder {

        private final List<String> remarks = new ArrayList<>();

        private int nameLength = DEFAULT_NAME_LENGTH;
        private int homesPerPlayer = DEFAULT_HOMES_PER_PLAYER;
        private int warps = DEFAULT_WARPS;
        private int pendingRequests = DEFAULT_PENDING_REQUESTS;
        private int requestTimeoutSeconds = REQUEST_TIMEOUT_SECONDS_CEILING;
        private int warmupSeconds = WARMUP_SECONDS_CEILING;
        private int safeSpotRadius = DEFAULT_SAFE_SPOT_RADIUS;
        private int backDepth = DEFAULT_BACK_DEPTH;
        private int randomAttempts = DEFAULT_RANDOM_ATTEMPTS;

        private Builder() {}

        /** Длина имени. Значение выше заводского ужимается до заводского. */
        public Builder nameLength(int value) {
            nameLength = lower(value, DEFAULT_NAME_LENGTH, "limits.nameLength");
            return this;
        }

        /** Домов на игрока. Значение выше заводского ужимается до заводского. */
        public Builder homesPerPlayer(int value) {
            homesPerPlayer = lower(value, DEFAULT_HOMES_PER_PLAYER, "limits.homesPerPlayer");
            return this;
        }

        /** Число варпов. Значение выше заводского ужимается до заводского. */
        public Builder warps(int value) {
            warps = lower(value, DEFAULT_WARPS, "limits.warps");
            return this;
        }

        /** Ждущих запросов на игрока. Значение выше заводского ужимается до заводского. */
        public Builder pendingRequests(int value) {
            pendingRequests = lower(value, DEFAULT_PENDING_REQUESTS, "limits.pendingRequests");
            return this;
        }

        /** Срок ожидания ответа. Значение выше заводского ужимается до заводского. */
        public Builder requestTimeoutSeconds(int value) {
            requestTimeoutSeconds = lower(value, REQUEST_TIMEOUT_SECONDS_CEILING, "limits.requestTimeoutSeconds");
            return this;
        }

        /** Тёплая задержка. Значение выше заводского ужимается до заводского. */
        public Builder warmupSeconds(int value) {
            warmupSeconds = lower(value, WARMUP_SECONDS_CEILING, "limits.warmupSeconds");
            return this;
        }

        /** Радиус колец поиска. Значение выше заводского ужимается до заводского. */
        public Builder safeSpotRadius(int value) {
            safeSpotRadius = lower(value, DEFAULT_SAFE_SPOT_RADIUS, "limits.safeSpotRadius");
            return this;
        }

        /** Глубина стека возврата. Значение выше заводского ужимается до заводского. */
        public Builder backDepth(int value) {
            backDepth = lower(value, DEFAULT_BACK_DEPTH, "limits.backDepth");
            return this;
        }

        /** Число попыток случайного переноса. Значение выше заводского ужимается до заводского. */
        public Builder randomAttempts(int value) {
            randomAttempts = lower(value, DEFAULT_RANDOM_ATTEMPTS, "limits.randomAttempts");
            return this;
        }

        /** Замечания о значениях конфига, которые пришлось заменить заводскими. */
        public List<String> remarks() {
            return new ArrayList<>(remarks);
        }

        /** Готовые потолки. */
        public EssentialsLimits build() {
            return new EssentialsLimits(
                nameLength,
                homesPerPlayer,
                warps,
                pendingRequests,
                requestTimeoutSeconds,
                warmupSeconds,
                safeSpotRadius,
                backDepth,
                randomAttempts);
        }

        private int lower(int value, int factoryValue, String field) {
            if (value < 1) {
                remarks.add(
                    field + " = "
                        + value
                        + " carries no usable ceiling, the factory value "
                        + factoryValue
                        + " is used");
                return factoryValue;
            }
            return Math.min(value, factoryValue);
        }
    }
}
