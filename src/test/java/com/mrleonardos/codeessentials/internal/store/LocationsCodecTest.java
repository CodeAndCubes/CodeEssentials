package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;

class LocationsCodecTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final LocationsCodec codec = new LocationsCodec(EssentialsLimits.defaults());

    @Test
    void playerSurvivesTheRoundTripWithEveryDigitOfEveryPoint() {
        Point odd = Point.of(-1, 10.123456789D, 64.5D, -20.7654321D, 91.25F, -12.5F);
        PlayerRecord written = PlayerRecord.of(
            STEVE,
            "Steve",
            Arrays.asList(HomeRecord.of("home", odd, 1_700L), HomeRecord.of("mine", Point.of(0, 1.0D, 2.0D, 3.0D), 0L)),
            Arrays.asList(BackPoint.of(odd, BackPoint.Origin.DEATH, 1_900L)));
        JsonObject file = LocationsCodec.emptyPlayers();

        codec.writePlayers(file, Arrays.asList(written), LocationsCodec.Quarantine.empty());
        PlayerRecord read = codec.readPlayers(file, null)
            .players()
            .get(STEVE);

        assertEquals(written, read);
        assertEquals(
            odd,
            read.home("home")
                .get()
                .point());
        assertEquals(
            odd,
            read.topBack()
                .get()
                .point());
    }

    @Test
    void emptyPlayerIsNotWrittenAtAll() {
        JsonObject file = LocationsCodec.emptyPlayers();

        codec.writePlayers(file, Arrays.asList(PlayerRecord.empty(STEVE, null)), LocationsCodec.Quarantine.empty());

        assertEquals(
            0,
            file.getAsJsonObject(LocationsCodec.PLAYERS)
                .entrySet()
                .size());
    }

    @Test
    void homeWithABadNameOrPointIsDroppedAndTheRecordStays() {
        JsonObject homes = new JsonObject();
        homes.addProperty("home", "0@1.5,64.0,2.5,0.0,0.0");
        homes.addProperty("ДОМ", "0@1.5,64.0,2.5,0.0,0.0");
        homes.addProperty("broken", "0@nowhere");
        JsonObject file = playersFile(entry("Steve", homes, null));

        LocationsCodec.DecodedPlayers decoded = codec.readPlayers(file, null);

        assertEquals(
            1,
            decoded.players()
                .get(STEVE)
                .homes()
                .size());
        assertEquals(2, decoded.dropped());
    }

    @Test
    void backEntryWithoutAPointIsDroppedAndTheOrderStays() {
        JsonArray back = new JsonArray();
        back.add(backEntry("0@1.5,64.0,2.5,0.0,0.0", "DEATH"));
        back.add(backEntry("nonsense", "TELEPORT"));
        back.add(backEntry("0@5.5,70.0,6.5,0.0,0.0", "TELEPORT"));
        JsonObject file = playersFile(entry("Steve", null, back));

        List<BackPoint> read = codec.readPlayers(file, null)
            .players()
            .get(STEVE)
            .back();

        assertEquals(2, read.size());
        assertTrue(
            read.get(0)
                .fromDeath());
        assertEquals(
            Point.of(0, 5.5D, 70.0D, 6.5D),
            read.get(1)
                .point());
    }

    @Test
    void backStackDeeperThanTheCeilingIsCutOnRead() {
        JsonArray back = new JsonArray();
        for (int index = 0; index < EssentialsLimits.DEFAULT_BACK_DEPTH + 5; index++) {
            back.add(backEntry("0@" + index + ".5,64.0,0.5,0.0,0.0", "TELEPORT"));
        }
        JsonObject file = playersFile(entry("Steve", null, back));

        LocationsCodec.DecodedPlayers decoded = codec.readPlayers(file, null);

        assertEquals(
            EssentialsLimits.DEFAULT_BACK_DEPTH,
            decoded.players()
                .get(STEVE)
                .back()
                .size());
        assertEquals(5, decoded.dropped());
    }

    @Test
    void entryUnderAnUnreadableKeyIsHeldBackAndWrittenAgain() {
        JsonObject players = new JsonObject();
        players.addProperty("not-a-uuid", "kept as it was");
        JsonObject file = new JsonObject();
        file.add(LocationsCodec.PLAYERS, players);

        LocationsCodec.DecodedPlayers decoded = codec.readPlayers(file, null);
        JsonObject written = LocationsCodec.emptyPlayers();
        codec.writePlayers(written, Collections.<PlayerRecord>emptyList(), decoded.quarantine());

        assertEquals(1, decoded.dropped());
        assertEquals(
            1,
            decoded.quarantine()
                .records());
        assertTrue(
            written.getAsJsonObject(LocationsCodec.PLAYERS)
                .has("not-a-uuid"));
    }

    @Test
    void cooldownsSplitIntoMovesAndRequestsAndComeBackTogether() {
        Map<UUID, Map<String, Long>> stamps = new LinkedHashMap<>();
        Map<String, Long> steve = new TreeMap<>();
        steve.put("home", Long.valueOf(5_000L));
        steve.put(PlayerDataStore.REQUEST_COOLDOWN_KEY, Long.valueOf(6_000L));
        stamps.put(STEVE, steve);
        JsonObject file = LocationsCodec.emptyRates();

        codec.writeCooldowns(file, stamps);

        assertTrue(
            file.getAsJsonObject(LocationsCodec.MOVES)
                .has(STEVE.toString()));
        assertTrue(
            file.getAsJsonObject(LocationsCodec.REQUESTS)
                .has(STEVE.toString()));
        assertEquals(
            stamps,
            codec.readCooldowns(file, 1_000L, null)
                .cooldowns());
    }

    @Test
    void expiredStampsLeaveQuietlyAndUnreadableOnesAreCounted() {
        JsonObject moves = new JsonObject();
        JsonObject steve = new JsonObject();
        steve.addProperty("home", Long.valueOf(5_000L));
        steve.addProperty("warp", "tomorrow");
        moves.add(STEVE.toString(), steve);
        JsonObject requests = new JsonObject();
        requests.addProperty(ALEX.toString(), Long.valueOf(9_000L));
        JsonObject file = new JsonObject();
        file.add(LocationsCodec.MOVES, moves);
        file.add(LocationsCodec.REQUESTS, requests);

        LocationsCodec.DecodedCooldowns decoded = codec.readCooldowns(file, 6_000L, null);

        assertFalse(
            decoded.cooldowns()
                .containsKey(STEVE),
            "протухшая метка уходит молча");
        assertEquals(
            Long.valueOf(9_000L),
            decoded.cooldowns()
                .get(ALEX)
                .get(PlayerDataStore.REQUEST_COOLDOWN_KEY));
        assertEquals(1, decoded.dropped(), "нечитаемая метка обязана попасть в счёт отброшенного");
    }

    @Test
    void missingSectionsReadAsEmpty() {
        assertTrue(
            codec.readPlayers(new JsonObject(), null)
                .players()
                .isEmpty());
        assertTrue(
            codec.readCooldowns(new JsonObject(), 0L, null)
                .cooldowns()
                .isEmpty());
    }

    private JsonObject playersFile(JsonObject entry) {
        JsonObject players = new JsonObject();
        players.add(STEVE.toString(), entry);
        JsonObject file = new JsonObject();
        file.add(LocationsCodec.PLAYERS, players);
        return file;
    }

    private static JsonObject entry(String name, JsonObject homes, JsonArray back) {
        JsonObject data = new JsonObject();
        data.addProperty(LocationsCodec.NAME, name);
        if (homes != null) {
            data.add(LocationsCodec.HOMES, homes);
        }
        if (back != null) {
            data.add(LocationsCodec.BACK, back);
        }
        return data;
    }

    private static JsonObject backEntry(String point, String origin) {
        JsonObject data = new JsonObject();
        data.addProperty(LocationsCodec.POINT, point);
        data.addProperty(LocationsCodec.ORIGIN, origin);
        return data;
    }
}
