package com.mrleonardos.codeessentials.internal.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;

public final class LocationsCodec {

    public static final String PLAYERS = "players";
    public static final String NAME = "name";
    public static final String HOMES = "homes";
    public static final String BACK = "back";
    public static final String POINT = "point";
    public static final String CREATED_AT = "createdAt";
    public static final String ORIGIN = "origin";
    public static final String RECORDED_AT = "recordedAt";
    public static final String MOVES = "moves";
    public static final String REQUESTS = "requests";

    private final EssentialsLimits limits;

    public LocationsCodec(EssentialsLimits limits) {
        this.limits = limits;
    }

    public static JsonObject emptyPlayers() {
        JsonObject file = new JsonObject();
        file.add(PLAYERS, new JsonObject());
        return file;
    }

    public static JsonObject emptyRates() {
        JsonObject file = new JsonObject();
        file.add(MOVES, new JsonObject());
        file.add(REQUESTS, new JsonObject());
        return file;
    }

    public DecodedPlayers readPlayers(JsonObject file, Logger log) {
        Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
        Quarantine quarantine = new Quarantine();
        int dropped = 0;
        for (Map.Entry<String, JsonElement> entry : object(file, PLAYERS).entrySet()) {
            UUID uuid = uuidOf(entry.getKey());
            if (uuid == null || !entry.getValue()
                .isJsonObject()) {
                dropped++;
                quarantine.hold(entry.getKey(), entry.getValue());
                warn(log, "Player entry {} has no readable uuid and stays in the file untouched", entry.getKey());
                continue;
            }
            if (players.containsKey(uuid)) {
                dropped++;
                quarantine.hold(entry.getKey(), entry.getValue());
                warn(log, "Player {} is declared twice, keeping the first declaration", uuid);
                continue;
            }
            JsonObject data = entry.getValue()
                .getAsJsonObject();
            Tally tally = new Tally();
            PlayerRecord record = readPlayer(uuid, data, log, tally, quarantine.of(entry.getKey()));
            if (record == null) {
                dropped++;
                quarantine.hold(entry.getKey(), entry.getValue());
                continue;
            }
            players.put(uuid, record);
            dropped += tally.count;
        }
        return new DecodedPlayers(players, dropped, quarantine);
    }

    public void writePlayers(JsonObject file, Collection<PlayerRecord> players, Quarantine quarantine) {
        JsonObject encoded = new JsonObject();
        for (PlayerRecord player : players) {
            String key = player.uuid()
                .toString();
            JsonObject data = encodePlayer(player);
            quarantine.mergeInto(key, data);
            if (data.entrySet()
                .isEmpty()) {
                continue;
            }
            encoded.add(key, data);
        }
        for (String key : quarantine.keys()) {
            if (encoded.has(key)) {
                continue;
            }
            JsonElement whole = quarantine.whole(key);
            if (whole != null) {
                encoded.add(key, whole);
                continue;
            }
            JsonObject data = new JsonObject();
            quarantine.mergeInto(key, data);
            if (!data.entrySet()
                .isEmpty()) {
                encoded.add(key, data);
            }
        }
        file.add(PLAYERS, encoded);
    }

    public DecodedCooldowns readCooldowns(JsonObject file, long now, Logger log) {
        Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();
        int dropped = 0;
        for (Map.Entry<String, JsonElement> entry : object(file, MOVES).entrySet()) {
            UUID uuid = uuidOf(entry.getKey());
            if (uuid == null || !entry.getValue()
                .isJsonObject()) {
                dropped++;
                warn(log, "Cooldown entry {} has no readable uuid and was dropped", entry.getKey());
                continue;
            }
            for (Map.Entry<String, JsonElement> stamp : entry.getValue()
                .getAsJsonObject()
                .entrySet()) {
                long expiresAt = millis(stamp.getValue());
                if (expiresAt <= 0L) {
                    dropped++;
                    warn(log, "Cooldown {} of {} carries no readable deadline and was dropped", stamp.getKey(), uuid);
                    continue;
                }
                if (expiresAt <= now) {
                    continue;
                }
                cooldowns.computeIfAbsent(uuid, key -> new TreeMap<String, Long>())
                    .put(
                        stamp.getKey()
                            .toLowerCase(Locale.ROOT),
                        Long.valueOf(expiresAt));
            }
        }
        for (Map.Entry<String, JsonElement> entry : object(file, REQUESTS).entrySet()) {
            UUID uuid = uuidOf(entry.getKey());
            long expiresAt = millis(entry.getValue());
            if (uuid == null || expiresAt <= 0L) {
                dropped++;
                warn(log, "Request rate entry {} is unreadable and was dropped", entry.getKey());
                continue;
            }
            if (expiresAt <= now) {
                continue;
            }
            cooldowns.computeIfAbsent(uuid, key -> new TreeMap<String, Long>())
                .put(PlayerDataStore.REQUEST_COOLDOWN_KEY, Long.valueOf(expiresAt));
        }
        return new DecodedCooldowns(cooldowns, dropped);
    }

    public void writeCooldowns(JsonObject file, Map<UUID, Map<String, Long>> cooldowns) {
        JsonObject moves = new JsonObject();
        JsonObject requests = new JsonObject();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            String uuid = entry.getKey()
                .toString();
            JsonObject stamps = new JsonObject();
            for (Map.Entry<String, Long> stamp : entry.getValue()
                .entrySet()) {
                if (PlayerDataStore.REQUEST_COOLDOWN_KEY.equals(stamp.getKey())) {
                    requests.addProperty(uuid, stamp.getValue());
                    continue;
                }
                stamps.addProperty(stamp.getKey(), stamp.getValue());
            }
            if (stamps.entrySet()
                .size() > 0) {
                moves.add(uuid, stamps);
            }
        }
        file.add(MOVES, moves);
        file.add(REQUESTS, requests);
    }

    private PlayerRecord readPlayer(UUID uuid, JsonObject data, Logger log, Tally tally, Held held) {
        List<HomeRecord> homes = readHomes(uuid, data.get(HOMES), log, tally, held);
        List<BackPoint> back = readBack(uuid, data.get(BACK), log, tally, held);
        try {
            return PlayerRecord.of(uuid, text(data.get(NAME)), homes, back);
        } catch (RuntimeException broken) {
            warn(log, "Player {} is unusable and stays in the file untouched: {}", uuid, broken.getMessage());
            return null;
        }
    }

    private List<HomeRecord> readHomes(UUID uuid, JsonElement element, Logger log, Tally tally, Held held) {
        List<HomeRecord> homes = new ArrayList<>();
        if (element == null || !element.isJsonObject()) {
            return homes;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject()
            .entrySet()) {
            String name = entry.getKey()
                .toLowerCase(Locale.ROOT);
            if (!limits.acceptsName(name) || homes.size() >= limits.homesPerPlayer()) {
                tally.count++;
                held.home(entry.getKey(), entry.getValue());
                warn(
                    log,
                    "Home {} of {} does not fit the ceilings and stays in the file untouched",
                    entry.getKey(),
                    uuid);
                continue;
            }
            HomeRecord home = readHome(name, entry.getValue(), log);
            if (home == null) {
                tally.count++;
                held.home(entry.getKey(), entry.getValue());
                warn(log, "Home {} of {} has no readable point and stays in the file untouched", entry.getKey(), uuid);
                continue;
            }
            homes.add(home);
        }
        return homes;
    }

    private HomeRecord readHome(String name, JsonElement element, Logger log) {
        if (element == null) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return home(name, element.getAsString(), 0L, log);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject data = element.getAsJsonObject();
        return home(name, text(data.get(POINT)), millis(data.get(CREATED_AT)), log);
    }

    private HomeRecord home(String name, String printed, long createdAt, Logger log) {
        Optional<Point> point = Point.parse(printed);
        if (!point.isPresent()) {
            return null;
        }
        try {
            return HomeRecord.of(name, point.get(), Math.max(0L, createdAt));
        } catch (RuntimeException broken) {
            warn(log, "Home {} is unusable and was dropped: {}", name, broken.getMessage());
            return null;
        }
    }

    private List<BackPoint> readBack(UUID uuid, JsonElement element, Logger log, Tally tally, Held held) {
        List<BackPoint> back = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return back;
        }
        for (JsonElement raw : element.getAsJsonArray()) {
            if (back.size() >= limits.backDepth()) {
                tally.count++;
                held.back(raw);
                continue;
            }
            BackPoint point = readBackPoint(raw);
            if (point == null) {
                tally.count++;
                held.back(raw);
                warn(log, "Back entry of {} has no readable point and stays in the file untouched", uuid);
                continue;
            }
            back.add(point);
        }
        return back;
    }

    private BackPoint readBackPoint(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject data = element.getAsJsonObject();
        Optional<Point> point = Point.parse(text(data.get(POINT)));
        if (!point.isPresent()) {
            return null;
        }
        BackPoint.Origin origin = origin(text(data.get(ORIGIN)));
        try {
            return BackPoint.of(point.get(), origin, Math.max(0L, millis(data.get(RECORDED_AT))));
        } catch (RuntimeException broken) {
            return null;
        }
    }

    private JsonObject encodePlayer(PlayerRecord player) {
        JsonObject data = new JsonObject();
        if (player.name() != null) {
            data.addProperty(NAME, player.name());
        }
        if (!player.homes()
            .isEmpty()) {
            JsonObject homes = new JsonObject();
            for (HomeRecord home : player.homes()
                .values()) {
                JsonObject entry = new JsonObject();
                entry.addProperty(
                    POINT,
                    home.point()
                        .print());
                if (home.createdAt() > 0L) {
                    entry.addProperty(CREATED_AT, Long.valueOf(home.createdAt()));
                }
                homes.add(home.name(), entry);
            }
            data.add(HOMES, homes);
        }
        if (!player.back()
            .isEmpty()) {
            JsonArray back = new JsonArray();
            for (BackPoint point : player.back()) {
                JsonObject entry = new JsonObject();
                entry.addProperty(
                    POINT,
                    point.point()
                        .print());
                entry.addProperty(
                    ORIGIN,
                    point.origin()
                        .name());
                if (point.recordedAt() > 0L) {
                    entry.addProperty(RECORDED_AT, Long.valueOf(point.recordedAt()));
                }
                back.add(entry);
            }
            data.add(BACK, back);
        }
        return data;
    }

    private static BackPoint.Origin origin(String raw) {
        if (raw != null && BackPoint.Origin.DEATH.name()
            .equalsIgnoreCase(raw.trim())) {
            return BackPoint.Origin.DEATH;
        }
        return BackPoint.Origin.TELEPORT;
    }

    private static JsonObject object(JsonObject file, String field) {
        JsonElement element = file == null ? null : file.get(field);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String text(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static long millis(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return 0L;
        }
    }

    private static UUID uuidOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static void warn(Logger log, String message, Object... arguments) {
        if (log != null) {
            log.warn(message, arguments);
        }
    }

    private static final class Tally {

        private int count;
    }

    static final class Held {

        private final Map<String, JsonElement> homes = new LinkedHashMap<>();
        private final List<JsonElement> back = new ArrayList<>();

        void home(String name, JsonElement element) {
            if (element != null) {
                homes.put(name, element);
            }
        }

        void back(JsonElement element) {
            if (element != null) {
                back.add(element);
            }
        }

        int records() {
            return homes.size() + back.size();
        }

        void mergeInto(JsonObject data) {
            if (!homes.isEmpty()) {
                JsonObject written = section(data, HOMES);
                for (Map.Entry<String, JsonElement> home : homes.entrySet()) {
                    if (!written.has(home.getKey())) {
                        written.add(home.getKey(), home.getValue());
                    }
                }
                data.add(HOMES, written);
            }
            if (!back.isEmpty()) {
                JsonArray written = data.has(BACK) && data.get(BACK)
                    .isJsonArray() ? data.getAsJsonArray(BACK) : new JsonArray();
                for (JsonElement point : back) {
                    written.add(point);
                }
                data.add(BACK, written);
            }
        }

        private static JsonObject section(JsonObject data, String field) {
            JsonElement element = data.get(field);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    public static final class Quarantine {

        private final Map<String, JsonElement> entries = new LinkedHashMap<>();
        private final Map<String, Held> parts = new LinkedHashMap<>();

        public static Quarantine empty() {
            return new Quarantine();
        }

        public int records() {
            int count = entries.size();
            for (Held held : parts.values()) {
                count += held.records();
            }
            return count;
        }

        Held of(String key) {
            return parts.computeIfAbsent(key, name -> new Held());
        }

        void hold(String key, JsonElement element) {
            if (element != null) {
                entries.put(key, element);
                parts.remove(key);
            }
        }

        Collection<String> keys() {
            Set<String> keys = new LinkedHashSet<>(entries.keySet());
            keys.addAll(parts.keySet());
            return keys;
        }

        JsonElement whole(String key) {
            return entries.get(key);
        }

        void mergeInto(String key, JsonObject data) {
            Held held = parts.get(key);
            if (held != null) {
                held.mergeInto(data);
            }
        }
    }

    public static final class DecodedPlayers {

        private final Map<UUID, PlayerRecord> players;
        private final int dropped;
        private final Quarantine quarantine;

        DecodedPlayers(Map<UUID, PlayerRecord> players, int dropped, Quarantine quarantine) {
            this.players = players;
            this.dropped = dropped;
            this.quarantine = quarantine;
        }

        public Map<UUID, PlayerRecord> players() {
            return players;
        }

        public int dropped() {
            return dropped;
        }

        public Quarantine quarantine() {
            return quarantine;
        }
    }

    public static final class DecodedCooldowns {

        private final Map<UUID, Map<String, Long>> cooldowns;
        private final int dropped;

        DecodedCooldowns(Map<UUID, Map<String, Long>> cooldowns, int dropped) {
            this.cooldowns = cooldowns;
            this.dropped = dropped;
        }

        public Map<UUID, Map<String, Long>> cooldowns() {
            return cooldowns;
        }

        public int dropped() {
            return dropped;
        }
    }
}
