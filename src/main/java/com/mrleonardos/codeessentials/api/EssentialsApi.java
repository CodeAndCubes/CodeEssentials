package com.mrleonardos.codeessentials.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

/**
 * Точка входа для чужих модов.
 *
 * <pre>
 *
 * EssentialsApi.registerStore(new SqlPlayerStore());
 * EssentialsApi.registerPolicy(new SkyIslandsPolicy());
 * EssentialsApi.teleports()
 *     .request(request);
 * </pre>
 *
 * <p>
 * Хранилище и политику поиска регистрируют на инициализации своего мода: реестр закрывается на
 * старте сервера, до первой загрузки данных. Поздняя регистрация это ошибка с исключением, а не
 * тихо потерянный провайдер.
 *
 * <p>
 * Сервисы берутся из реестра ядра, а не из полей с реализациями. Мод, подменивший
 * {@link TeleportService} весом {@code OVERRIDE}, виден и через эту дверь: она ведёт к тому, кто
 * держит сервис сейчас. Обращение до того, как CodeEssentials поднялся, даёт понятную ошибку, а не
 * падение.
 */
public final class EssentialsApi {

    /**
     * Откуда брать сервисы. Это реестр ядра, завёрнутый CodeEssentials, чтобы api не тянул за собой
     * типы платформы.
     */
    public interface Lookup {

        /** Кто держит сервис сейчас. */
        <T> Optional<T> find(Class<T> type);
    }

    private static final Map<String, PlayerDataStore> STORES = new TreeMap<>();
    private static final Map<String, SafeSpotPolicy> POLICIES = new TreeMap<>();

    private static volatile Lookup services;
    private static volatile EssentialsEvents events;
    private static volatile boolean frozen;

    private EssentialsApi() {}

    /**
     * Зарегистрировать хранилище состояния игроков. Активным становится то, чьё имя указано в
     * настройке {@code storage.playerProvider}.
     *
     * @throws IllegalArgumentException если имя уже занято другим провайдером
     * @throws IllegalStateException    если реестр уже закрыт стартом сервера
     */
    public static synchronized void registerStore(PlayerDataStore store) {
        Objects.requireNonNull(store, "store");
        ensureOpen("Player data stores", store.id());
        PlayerDataStore held = STORES.get(store.id());
        if (held != null && held != store) {
            throw new IllegalArgumentException("Player data store is already registered: " + store.id());
        }
        STORES.put(store.id(), store);
    }

    /** Хранилище по имени из настройки {@code storage.playerProvider}. */
    public static synchronized Optional<PlayerDataStore> store(String id) {
        return Optional.ofNullable(STORES.get(Objects.requireNonNull(id, "id")));
    }

    /** Все зарегистрированные хранилища в алфавитном порядке имён. */
    public static synchronized List<PlayerDataStore> stores() {
        return new ArrayList<>(STORES.values());
    }

    /**
     * Зарегистрировать политику поиска безопасной точки.
     *
     * @throws IllegalArgumentException если имя уже занято другой политикой
     * @throws IllegalStateException    если реестр уже закрыт стартом сервера
     */
    public static synchronized void registerPolicy(SafeSpotPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        ensureOpen("Safe spot policies", policy.id());
        SafeSpotPolicy held = POLICIES.get(policy.id());
        if (held != null && held != policy) {
            throw new IllegalArgumentException("Safe spot policy is already registered: " + policy.id());
        }
        POLICIES.put(policy.id(), policy);
    }

    /** Политика по имени из настройки {@code safeSpot.policy}. */
    public static synchronized Optional<SafeSpotPolicy> policy(String id) {
        return Optional.ofNullable(POLICIES.get(Objects.requireNonNull(id, "id")));
    }

    /** Все зарегистрированные политики в алфавитном порядке имён. */
    public static synchronized List<SafeSpotPolicy> policies() {
        return new ArrayList<>(POLICIES.values());
    }

    /** Движок переносов: та же реализация, что держит реестр сервисов ядра. */
    public static TeleportService teleports() {
        return require(TeleportService.class);
    }

    /** Дома игроков. */
    public static HomeService homes() {
        return require(HomeService.class);
    }

    /** Варпы сервера. */
    public static WarpService warps() {
        return require(WarpService.class);
    }

    /** Точки спавна. */
    public static SpawnService spawns() {
        return require(SpawnService.class);
    }

    /** Стек возврата. */
    public static BackService backs() {
        return require(BackService.class);
    }

    /** Реестры слушателей. */
    public static EssentialsEvents events() {
        EssentialsEvents installed = events;
        if (installed == null) {
            throw new IllegalStateException(notReady());
        }
        return installed;
    }

    /** Закрыть регистрацию хранилищ и политик. Зовёт сам CodeEssentials на старте сервера. */
    public static synchronized void freeze() {
        frozen = true;
    }

    /** Закрыта ли регистрация. */
    public static boolean frozen() {
        return frozen;
    }

    /**
     * Подключает реализацию. Вызывается самим CodeEssentials: чужим модам метод не нужен.
     *
     * @param registry откуда брать сервисы; это реестр ядра, а не поля с реализациями, чтобы подмена
     *                 сервиса чужим модом была видна и через эту дверь
     */
    public static void install(Lookup registry, EssentialsEvents installedEvents) {
        services = Objects.requireNonNull(registry, "registry");
        events = Objects.requireNonNull(installedEvents, "installedEvents");
    }

    static synchronized void reopen() {
        frozen = false;
        services = null;
        events = null;
        STORES.clear();
        POLICIES.clear();
    }

    private static <T> T require(Class<T> type) {
        Lookup installed = services;
        if (installed == null) {
            throw new IllegalStateException(notReady());
        }
        return installed.find(type)
            .orElseThrow(
                () -> new IllegalStateException(
                    "No mod holds " + type.getSimpleName() + " in the core service registry"));
    }

    private static String notReady() {
        return "CodeEssentials is not ready yet, call it no earlier than its init phase";
    }

    private static void ensureOpen(String what, String id) {
        if (frozen) {
            throw new IllegalStateException(what + " are frozen since the server start, register " + id + " in init");
        }
    }
}
