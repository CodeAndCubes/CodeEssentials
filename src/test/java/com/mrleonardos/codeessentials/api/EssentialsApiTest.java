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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.event.KitEvents;
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
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.engine.Cooldowns;
import com.mrleonardos.codeessentials.internal.engine.EngineFixtures;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;
import com.mrleonardos.codeessentials.internal.engine.SafeSpotFinder;
import com.mrleonardos.codeessentials.internal.engine.TeleportEngine;
import com.mrleonardos.codeessentials.internal.service.BackServiceImpl;
import com.mrleonardos.codeessentials.internal.service.StateWriter;
import com.mrleonardos.codeessentials.internal.store.SingleWriterImpl;

class EssentialsApiTest {

    private static final Logger LOG = LogManager.getLogger("codeessentials-test");

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

    @Test
    void aForeignModMeetsTheSameCooldownGateThroughTheDoor() {
        AtomicLong clock = new AtomicLong(1_000L);
        EngineFixtures.FakeWorlds worlds = new EngineFixtures.FakeWorlds();
        worlds.world(0)
            .plate(63, -10, 10);
        worlds.standing(EngineFixtures.STEVE, Point.of(0, 0.5D, 64.0D, 0.5D));
        EngineRules rules = EngineRules.builder()
            .warmupSeconds(0)
            .cooldown(TeleportCause.API, 30)
            .build();
        SingleWriterImpl writer = EngineFixtures.writer(clock::get);
        EngineFixtures.FakeRights rights = new EngineFixtures.FakeRights();
        Cooldowns cooldowns = new Cooldowns(writer, rights, () -> rules, clock::get);
        TeleportEngine engine = new TeleportEngine(
            () -> rules,
            worlds,
            rights,
            new SafeSpotFinder(),
            new EngineFixtures.FakeMover(),
            cooldowns,
            new BackServiceImpl(EssentialsSettings::defaults, rights, new StateWriter(writer), LOG),
            new EngineFixtures.FakeEvents(),
            new EngineFixtures.TestScheduler(),
            clock::get,
            LOG);
        Map<Class<?>, Object> registry = new HashMap<>();
        registry.put(TeleportService.class, engine);
        EssentialsApi.install(new MapLookup(registry), new StubEvents());

        assertEquals(TeleportJob.State.DONE, ask().state());

        TeleportJob second = ask();

        assertEquals(
            CancelReason.COOLDOWN,
            second.reason()
                .get(),
            "чужой мод идёт тем же конвейером, включая кулдауны");
        assertEquals(TeleportJob.State.CANCELLED, second.state());

        clock.set(clock.get() + 31_000L);

        assertEquals(TeleportJob.State.DONE, ask().state());
    }

    private static TeleportJob ask() {
        return EssentialsApi.teleports()
            .request(
                TeleportRequest.builder(EngineFixtures.STEVE, Point.of(0, 5.5D, 64.0D, 5.5D), TeleportCause.API)
                    .build());
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

        @Override
        public KitEvents kits() {
            return null;
        }
    }
}
