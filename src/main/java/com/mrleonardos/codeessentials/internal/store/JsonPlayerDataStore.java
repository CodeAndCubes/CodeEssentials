package com.mrleonardos.codeessentials.internal.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;

public final class JsonPlayerDataStore implements PlayerDataStore, BufferedStore {

    public static final String ID = "json";
    public static final String MODID = "codeessentials";
    public static final String PLAYERS_FILE = "players";
    public static final String RATES_FILE = "rates";

    private final ConfigFile<JsonObject> playersFile;
    private final ConfigFile<JsonObject> ratesFile;
    private final LocationsCodec codec;
    private final LongSupplier clock;
    private final Logger log;

    private final Object lock = new Object();
    private final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();

    private LocationsCodec.Quarantine quarantine = LocationsCodec.Quarantine.empty();
    private volatile boolean dirty;

    public JsonPlayerDataStore(ConfigFile<JsonObject> playersFile, ConfigFile<JsonObject> ratesFile,
        EssentialsLimits limits, LongSupplier clock, Logger log) {
        this.playersFile = playersFile;
        this.ratesFile = ratesFile;
        this.codec = new LocationsCodec(limits);
        this.clock = clock;
        this.log = log;
    }

    public static JsonPlayerDataStore create(ConfigService configs, EssentialsLimits limits, LongSupplier clock,
        Logger log) {
        return new JsonPlayerDataStore(configs.open(playersSpec()), configs.open(ratesSpec()), limits, clock, log);
    }

    public static ConfigSpec<JsonObject> playersSpec() {
        SchemaMigrations.checkChain(SchemaMigrations.playersChain(), SchemaMigrations.PLAYERS_VERSION, PLAYERS_FILE);
        ConfigSpec.Builder<JsonObject> builder = ConfigSpec.of(MODID, PLAYERS_FILE, JsonObject.class)
            .scope(ConfigScope.WORLD_STATE)
            .schemaVersion(SchemaMigrations.PLAYERS_VERSION);
        for (Migration migration : SchemaMigrations.playersChain()) {
            builder.migration(migration);
        }
        return builder.defaults(LocationsCodec::emptyPlayers)
            .build();
    }

    public static ConfigSpec<JsonObject> ratesSpec() {
        SchemaMigrations.checkChain(SchemaMigrations.ratesChain(), SchemaMigrations.RATES_VERSION, RATES_FILE);
        ConfigSpec.Builder<JsonObject> builder = ConfigSpec.of(MODID, RATES_FILE, JsonObject.class)
            .scope(ConfigScope.WORLD_STATE)
            .schemaVersion(SchemaMigrations.RATES_VERSION);
        for (Migration migration : SchemaMigrations.ratesChain()) {
            builder.migration(migration);
        }
        return builder.defaults(LocationsCodec::emptyRates)
            .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Map<UUID, PlayerRecord> loadPlayers() {
        LocationsCodec.DecodedPlayers decoded = codec.readPlayers(data(playersFile, PLAYERS_FILE), log);
        if (decoded.dropped() > 0) {
            log.warn("{} record(s) in {}.json are unusable and were skipped", decoded.dropped(), PLAYERS_FILE);
        }
        if (decoded.quarantine()
            .records() > 0) {
            log.warn(
                "{} entry(ies) of {}.json stay in the file untouched until a human looks at them",
                decoded.quarantine()
                    .records(),
                PLAYERS_FILE);
        }
        synchronized (lock) {
            quarantine = decoded.quarantine();
            players.clear();
            players.putAll(decoded.players());
            return new LinkedHashMap<>(players);
        }
    }

    @Override
    public Map<UUID, Map<String, Long>> loadCooldowns() {
        LocationsCodec.DecodedCooldowns decoded = codec
            .readCooldowns(data(ratesFile, RATES_FILE), clock.getAsLong(), log);
        if (decoded.dropped() > 0) {
            log.warn("{} record(s) in {}.json are unusable and were skipped", decoded.dropped(), RATES_FILE);
        }
        synchronized (lock) {
            cooldowns.clear();
            cooldowns.putAll(decoded.cooldowns());
            return copyCooldowns();
        }
    }

    @Override
    public StoreResult apply(ChangeBatch batch) {
        synchronized (lock) {
            for (ChangeBatch.Change change : batch.changes()) {
                switch (change.kind()) {
                    case UPSERT_PLAYER:
                        players.put(
                            change.player(),
                            change.record()
                                .get());
                        break;
                    case REMOVE_PLAYER:
                        players.remove(change.player());
                        break;
                    case SET_COOLDOWN:
                        cooldowns.computeIfAbsent(change.player(), key -> new TreeMap<String, Long>())
                            .put(
                                change.cooldownKey()
                                    .get(),
                                Long.valueOf(change.expiresAt()));
                        break;
                    default:
                        cooldowns.remove(change.player());
                        break;
                }
            }
            dirty = true;
        }
        return StoreResult.success();
    }

    @Override
    public boolean unsaved() {
        return dirty;
    }

    @Override
    public StoreResult flush() {
        List<PlayerRecord> written;
        Map<UUID, Map<String, Long>> stamps;
        LocationsCodec.Quarantine held;
        synchronized (lock) {
            if (!dirty) {
                return StoreResult.success();
            }
            written = new ArrayList<>(players.values());
            stamps = copyCooldowns();
            held = quarantine;
            dirty = false;
        }
        try {
            codec.writePlayers(data(playersFile, PLAYERS_FILE), written, held);
            playersFile.save();
            codec.writeCooldowns(data(ratesFile, RATES_FILE), stamps);
            ratesFile.save();
            return StoreResult.success();
        } catch (RuntimeException failure) {
            dirty = true;
            log.error("Player state was not written, the mod keeps working on the last snapshot", failure);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, failure.toString());
        }
    }

    private Map<UUID, Map<String, Long>> copyCooldowns() {
        Map<UUID, Map<String, Long>> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        return copy;
    }

    private static JsonObject data(ConfigFile<JsonObject> file, String name) {
        if (!file.loaded()) {
            throw new IllegalStateException(
                name + ".json lives in the world folder and opens no earlier than FMLServerStartingEvent");
        }
        return file.get();
    }
}
