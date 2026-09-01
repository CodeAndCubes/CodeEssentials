package com.mrleonardos.codeessentials.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.event.TeleportEvents;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.PlayerDataStore;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

class EssentialsApiTest {

    @BeforeEach
    @AfterEach
    void clearRegistries() {
        EssentialsApi.reopen();
    }

    @Test
    void storesAndPoliciesAnswerByName() {
        PlayerDataStore sql = new StubStore("sql");
        SafeSpotPolicy islands = new StubPolicy("islands");

        EssentialsApi.registerStore(sql);
        EssentialsApi.registerPolicy(islands);

        assertSame(
            sql,
            EssentialsApi.store("sql")
                .get());
        assertSame(
            islands,
            EssentialsApi.policy("islands")
                .get());
        assertFalse(
            EssentialsApi.store("json")
                .isPresent());
        assertFalse(
            EssentialsApi.policy("builtin")
                .isPresent());
        assertEquals(
            1,
            EssentialsApi.stores()
                .size());
        assertEquals(
            1,
            EssentialsApi.policies()
                .size());
    }

    @Test
    void takenNameStaysWithItsOwner() {
        PlayerDataStore sql = new StubStore("sql");
        SafeSpotPolicy islands = new StubPolicy("islands");

        EssentialsApi.registerStore(sql);
        EssentialsApi.registerStore(sql);
        EssentialsApi.registerPolicy(islands);
        EssentialsApi.registerPolicy(islands);

        assertThrows(IllegalArgumentException.class, () -> EssentialsApi.registerStore(new StubStore("sql")));
        assertThrows(IllegalArgumentException.class, () -> EssentialsApi.registerPolicy(new StubPolicy("islands")));
        assertThrows(NullPointerException.class, () -> EssentialsApi.registerStore(null));
        assertThrows(NullPointerException.class, () -> EssentialsApi.registerPolicy(null));
    }

    @Test
    void lateRegistrationIsAnErrorAndNotALostProvider() {
        assertFalse(EssentialsApi.frozen());
        EssentialsApi.freeze();

        assertTrue(EssentialsApi.frozen());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.registerStore(new StubStore("sql")));
        assertThrows(IllegalStateException.class, () -> EssentialsApi.registerPolicy(new StubPolicy("islands")));
    }

    @Test
    void callingBeforeTheModIsUpSaysSo() {
        assertThrows(IllegalStateException.class, () -> EssentialsApi.teleports());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.homes());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.warps());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.spawns());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.backs());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.events());
    }

    @Test
    void doorLeadsToWhoeverHoldsTheServiceNow() {
        Map<Class<?>, Object> registry = new HashMap<>();
        TeleportService held = new StubTeleports();
        registry.put(TeleportService.class, held);
        EssentialsApi.install(new MapLookup(registry), new StubEvents());

        assertSame(held, EssentialsApi.teleports());
        assertThrows(IllegalStateException.class, () -> EssentialsApi.homes());
        assertThrows(NullPointerException.class, () -> EssentialsApi.install(null, new StubEvents()));
    }

    private static final class MapLookup implements EssentialsApi.Lookup {

        private final Map<Class<?>, Object> registry;

        MapLookup(Map<Class<?>, Object> registry) {
            this.registry = registry;
        }

        @Override
        public <T> Optional<T> find(Class<T> type) {
            return Optional.ofNullable(type.cast(registry.get(type)));
        }
    }

    private static final class StubStore implements PlayerDataStore {

        private final String id;

        StubStore(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Map<UUID, PlayerRecord> loadPlayers() {
            return new HashMap<>();
        }

        @Override
        public Map<UUID, Map<String, Long>> loadCooldowns() {
            return new HashMap<>();
        }

        @Override
        public StoreResult apply(ChangeBatch batch) {
            return StoreResult.success();
        }
    }

    private static final class StubPolicy implements SafeSpotPolicy {

        private final String id;

        StubPolicy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
            return SafeSpotResult.found(hint, hint, 1);
        }
    }

    private static final class StubTeleports implements TeleportService {

        @Override
        public TeleportJob request(TeleportRequest request) {
            return TeleportJob.starting(1L, request);
        }

        @Override
        public Optional<TeleportJob> job(UUID player) {
            return Optional.empty();
        }

        @Override
        public List<TeleportJob> jobs() {
            return new ArrayList<>();
        }

        @Override
        public Optional<TeleportJob> cancel(UUID player, CancelReason reason) {
            return Optional.empty();
        }

        @Override
        public long cooldownRemaining(UUID player, TeleportCause cause) {
            return 0L;
        }

        @Override
        public StoreResult clearCooldowns(UUID player) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, player.toString());
        }
    }

    private static final class StubEvents implements EssentialsEvents {

        @Override
        public TeleportEvents teleports() {
            return null;
        }

        @Override
        public HomeEvents homes() {
            return null;
        }
    }
}
