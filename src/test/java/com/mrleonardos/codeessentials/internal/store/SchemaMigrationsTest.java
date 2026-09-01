package com.mrleonardos.codeessentials.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;

class SchemaMigrationsTest {

    @Test
    void firstVersionIsCurrentForBothWorldFiles() {
        assertEquals(1, SchemaMigrations.PLAYERS_VERSION);
        assertEquals(1, SchemaMigrations.RATES_VERSION);
        assertTrue(
            SchemaMigrations.playersChain()
                .isEmpty());
        assertTrue(
            SchemaMigrations.ratesChain()
                .isEmpty());
    }

    @Test
    void bothFilesLiveInTheWorldAndCarryTheirVersion() {
        ConfigSpec<JsonObject> players = JsonPlayerDataStore.playersSpec();
        ConfigSpec<JsonObject> rates = JsonPlayerDataStore.ratesSpec();

        assertEquals(ConfigScope.WORLD_STATE, players.scope());
        assertEquals(ConfigScope.WORLD_STATE, rates.scope());
        assertEquals(SchemaMigrations.PLAYERS_VERSION, players.schemaVersion());
        assertEquals(SchemaMigrations.RATES_VERSION, rates.schemaVersion());
        assertEquals(
            SchemaMigrations.playersChain()
                .size(),
            players.migrations()
                .size());
        assertTrue(
            players.defaults()
                .get()
                .has(LocationsCodec.PLAYERS));
        assertTrue(
            rates.defaults()
                .get()
                .has(LocationsCodec.MOVES));
    }

    @Test
    void chainWithAHoleIsRefusedBeforeTheServerStarts() {
        Migration first = step(1, 2);
        Migration skipped = step(3, 4);

        assertThrows(
            IllegalStateException.class,
            () -> SchemaMigrations.checkChain(Arrays.asList(first, skipped), 3, "players"));
        assertThrows(
            IllegalStateException.class,
            () -> SchemaMigrations.checkChain(Arrays.asList(first), 3, "players"));
        SchemaMigrations.checkChain(Arrays.asList(first), 2, "players");
    }

    private static Migration step(int from, int to) {
        return new Migration() {

            @Override
            public int from() {
                return from;
            }

            @Override
            public int to() {
                return to;
            }

            @Override
            public void apply(JsonObject data) {}
        };
    }
}
