package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codeessentials.TestConfigs;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;

class JsonPlayerDataStoreTest {

    private static final Logger LOG = LogManager.getLogger(JsonPlayerDataStoreTest.class);
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AtomicLong clock = new AtomicLong(10_000L);

    @TempDir
    Path root;

    @Test
    void worldStateIsUnreachableBeforeTheWorldIsBound() {
        ConfigService configs = TestConfigs.of(root);
        JsonPlayerDataStore store = JsonPlayerDataStore.create(configs, EssentialsLimits.defaults(), clock::get, LOG);

        IllegalStateException early = assertThrows(IllegalStateException.class, store::loadPlayers);

        assertTrue(
            early.getMessage()
                .contains("FMLServerStartingEvent"),
            "ранний вызов обязан объяснять, какого события ждать: " + early.getMessage());
        assertThrows(IllegalStateException.class, store::loadCooldowns);
    }

    @Test
    void firstRunCreatesBothFilesInTheWorldFolder() {
        store();

        assertTrue(Files.isRegularFile(path(JsonPlayerDataStore.PLAYERS_FILE_NAME)));
        assertTrue(Files.isRegularFile(path(JsonPlayerDataStore.RATES_FILE_NAME)));
    }

    @Test
    void brokenPlayersFileGoesAsideAndTheModStartsEmpty() throws Exception {
        Path players = path(JsonPlayerDataStore.PLAYERS_FILE_NAME);
        Files.createDirectories(players.getParent());
        Files.write(players, Arrays.asList("{ \"players\": {"));

        JsonPlayerDataStore store = store();

        assertTrue(
            store.loadPlayers()
                .isEmpty());
        assertTrue(
            Files.isRegularFile(
                players.resolveSibling(
                    players.getFileName()
                        .toString() + ".broken")));
    }

    @Test
    void homeWithAnUnreadablePointIsDroppedAndTheRestSurvives() {
        JsonObject players = new JsonObject();
        JsonObject steve = new JsonObject();
        JsonObject homes = new JsonObject();
        homes.addProperty("home", "0@1.5,64.0,2.5,0.0,0.0");
        homes.addProperty("broken", "not a point at all");
        steve.add("homes", homes);
        steve.addProperty("name", "Steve");
        players.add(STEVE.toString(), steve);
        write(players);

        PlayerRecord record = store().loadPlayers()
            .get(STEVE);

        assertEquals(
            1,
            record.homes()
                .size());
        assertTrue(
            record.home("home")
                .isPresent());
    }

    @Test
    void unreadablePlayerEntryStaysInTheFileAfterAWrite() throws Exception {
        JsonObject players = new JsonObject();
        players.addProperty("not-a-uuid", "whatever it was");
        write(players);
        JsonPlayerDataStore store = store();
        store.loadPlayers();

        store.apply(
            ChangeBatch.builder("test")
                .upsert(
                    PlayerRecord.of(
                        STEVE,
                        "Steve",
                        Arrays.asList(HomeRecord.of("home", Point.of(0, 1.5D, 64.0D, 2.5D), 1L)),
                        Collections.<BackPoint>emptyList()))
                .build());
        store.flush();

        String text = new String(
            Files.readAllBytes(path(JsonPlayerDataStore.PLAYERS_FILE_NAME)),
            StandardCharsets.UTF_8);
        assertTrue(text.contains("not-a-uuid"), "нечитаемая запись обязана остаться в файле: " + text);
        assertTrue(text.contains(STEVE.toString()));
    }

    @Test
    void ratesFileKeepsMovesAndRequestsApart() throws Exception {
        JsonPlayerDataStore store = store();
        store.loadCooldowns();
        store.apply(
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", 99_000L)
                .setCooldown(STEVE, "request", 98_000L)
                .build());
        store.flush();

        String text = new String(Files.readAllBytes(path(JsonPlayerDataStore.RATES_FILE_NAME)), StandardCharsets.UTF_8);
        JsonObject file = TestConfigs.readJson(path(JsonPlayerDataStore.RATES_FILE_NAME));

        assertTrue(text.contains("99000"), text);
        assertEquals(
            99_000L,
            file.getAsJsonObject("moves")
                .getAsJsonObject(STEVE.toString())
                .get("home")
                .getAsLong());
        assertEquals(
            98_000L,
            file.getAsJsonObject("requests")
                .get(STEVE.toString())
                .getAsLong());
    }

    @Test
    void failedWriteKeepsTheStoreUnsaved() {
        JsonPlayerDataStore store = new JsonPlayerDataStore(
            new BrokenFile(),
            new BrokenFile(),
            EssentialsLimits.defaults(),
            clock::get,
            LOG);
        store.apply(
            ChangeBatch.builder("test")
                .setCooldown(STEVE, "home", 99_000L)
                .build());

        assertFalse(
            store.flush()
                .successful());
        assertTrue(store.unsaved(), "неудачная запись обязана оставить пометку о несохранённом");
    }

    private JsonPlayerDataStore store() {
        ConfigService configs = TestConfigs.of(root);
        JsonPlayerDataStore store = JsonPlayerDataStore.create(configs, EssentialsLimits.defaults(), clock::get, LOG);
        TestConfigs.attachWorld(configs, root.resolve(TestConfigs.WORLD));
        return store;
    }

    private void write(JsonObject players) {
        JsonObject file = new JsonObject();
        file.add(LocationsCodec.PLAYERS, players);
        TestConfigs.writeJson(path(JsonPlayerDataStore.PLAYERS_FILE_NAME), file);
    }

    private Path path(String fileName) {
        return TestConfigs.worldState(root)
            .resolve(fileName);
    }

    private static final class BrokenFile implements ConfigFile<JsonObject> {

        private final JsonObject data = LocationsCodec.emptyPlayers();

        @Override
        public JsonObject get() {
            return data;
        }

        @Override
        public boolean loaded() {
            return true;
        }

        @Override
        public void save() {
            throw new IllegalStateException("the disk is gone");
        }

        @Override
        public void reload() {}

        @Override
        public Path path() {
            return null;
        }
    }
}
