package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.BlockSample;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.command.RandomSpots;

class RandomFinderTest {

    private static final int GROUND = 64;
    private static final Point HERE = Point.of(0, 0.5D, 65.0D, 0.5D, 90.0F, 10.0F);

    private EngineFixtures.FakeWorlds worlds;
    private SafeSpotPolicy policy;
    private FakeSpawns spawns;
    private RandomRules rtp;

    @BeforeEach
    void setUp() {
        worlds = new EngineFixtures.FakeWorlds();
        policy = new SafeSpotFinder();
        spawns = new FakeSpawns();
        rtp = ring(0, 10);
    }

    @Test
    void theSpotStaysInsideTheRing() {
        plate(worlds.world(0), -40, 40);
        rtp = RandomRules.builder()
            .radius(12, 30)
            .center(false, 0, 0)
            .build();
        RandomFinder finder = finder(7L);

        for (int run = 0; run < 40; run++) {
            RandomSpots.Reply reply = finder.find(HERE);

            Point spot = spot(reply);
            double distance = Math.hypot(spot.x(), spot.z());
            assertTrue(distance >= 11.0D, () -> "точка ближе кольца: " + distance);
            assertTrue(distance <= 31.0D, () -> "точка дальше кольца: " + distance);
        }
    }

    @Test
    void theRingIsFilledByAreaAndNotByLine() {
        plate(worlds.world(0), -50, 50);
        rtp = ring(0, 40);
        RandomFinder finder = finder(11L);
        double half = 40.0D / Math.sqrt(2.0D);
        int far = 0;

        for (int run = 0; run < 200; run++) {
            Point spot = spot(finder.find(HERE));
            if (Math.hypot(spot.x(), spot.z()) > half) {
                far++;
            }
        }

        int found = far;
        assertTrue(
            found > 70 && found < 130,
            () -> "за половиной площади кольца лежит половина точек, а вышло " + found);
    }

    @Test
    void aBlockedBiomeCostsATryAndNotAChunk() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        plate(world, -20, 20);
        world.biomeEverywhere("Deep Ocean");
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .blockedBiomes(Collections.singletonList("deepocean"))
            .attempts(3)
            .build();

        RandomSpots.Reply reply = finder(3L).find(HERE);

        assertEquals(RandomSpots.Outcome.NOT_FOUND, reply.outcome());
        assertEquals(3, reply.attempts());
        assertTrue(
            world.broughtUp()
                .isEmpty(),
            "отсев по биому не платит за диск");
        assertEquals(0, world.samples(), "колонку отсеянного кандидата не читают");
    }

    @Test
    void aWorldThatDoesNotNameItsBiomesIsNotFiltered() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        plate(world, -20, 20);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .blockedBiomes(Arrays.asList("Ocean", "Desert"))
            .build();

        RandomSpots.Reply reply = finder(5L).find(HERE);

        assertEquals(RandomSpots.Outcome.FOUND, reply.outcome());
    }

    @Test
    void aMissingChunkCostsATryAndNoColumnIsRead() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        for (int chunkX = -1; chunkX <= 0; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                world.unloadChunk(chunkX, chunkZ);
            }
        }
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .attempts(4)
            .build();

        RandomSpots.Reply reply = finder(9L).find(HERE);

        assertEquals(RandomSpots.Outcome.NOT_FOUND, reply.outcome());
        assertEquals(4, reply.attempts());
        assertTrue(
            !world.broughtUp()
                .isEmpty(),
            "чанк спросили");
        assertEquals(0, world.samples(), "не поднявшийся чанк не читают");
    }

    @Test
    void theOceanIsTurnedDownByTheCheckAndNotByTheList() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.floor(x, 40, z);
                world.column(x, z, 41, GROUND, BlockSample.WATER);
            }
        }

        RandomSpots.Reply reply = finder(13L).find(HERE);

        assertEquals(RandomSpots.Outcome.NOT_FOUND, reply.outcome());
        assertEquals(RandomRules.DEFAULT_ATTEMPTS, reply.attempts());
    }

    @Test
    void aLavaLakeIsTurnedDownTheSameWay() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.floor(x, 40, z);
                world.column(x, z, 41, 44, BlockSample.LAVA);
            }
        }

        assertEquals(
            RandomSpots.Outcome.NOT_FOUND,
            finder(17L).find(HERE)
                .outcome());
    }

    @Test
    void aPolicyThatOffersAFloatingSpotIsNotTrusted() {
        plate(worlds.world(0), -20, 20);
        policy = new SafeSpotPolicy() {

            @Override
            public String id() {
                return "floating";
            }

            @Override
            public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
                return SafeSpotResult.found(hint, hint.withBlock(hint.blockX(), 200, hint.blockZ()), 1);
            }
        };

        RandomSpots.Reply reply = finder(19L).find(HERE);

        assertEquals(RandomSpots.Outcome.NOT_FOUND, reply.outcome(), "у случайной точки обязателен пол");
    }

    @Test
    void theNumberOfTriesIsTheCeilingOfTheSearch() {
        worlds.world(0);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .attempts(5)
            .build();

        assertEquals(
            5,
            finder(23L).find(HERE)
                .attempts());
    }

    @Test
    void theSpawnTakesOverWhenNothingIsFound() {
        worlds.world(0);
        spawns.spawn = Point.of(0, 8.0D, 70.0D, 9.0D);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .attempts(2)
            .fallbackToSpawn(true)
            .build();

        RandomSpots.Reply reply = finder(29L).find(HERE);

        assertEquals(RandomSpots.Outcome.SPAWN, reply.outcome());
        assertEquals(spawns.spawn, spot(reply));
        assertEquals(2, reply.attempts());
    }

    @Test
    void withoutASpawnTheFallbackIsStillARefusal() {
        worlds.world(0);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .attempts(2)
            .fallbackToSpawn(true)
            .build();

        assertEquals(
            RandomSpots.Outcome.NOT_FOUND,
            finder(31L).find(HERE)
                .outcome());
    }

    @Test
    void theCenterComesFromTheSpawnOfTheWorld() {
        plate(worlds.world(0), 180, 220);
        spawns.spawn = Point.of(0, 200.0D, 65.0D, 200.0D);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(true, 0, 0)
            .build();

        Point spot = spot(finder(37L).find(HERE));

        assertTrue(Math.hypot(spot.x() - 200.0D, spot.z() - 200.0D) <= 11.0D, () -> "центр не спавн: " + spot.print());
    }

    @Test
    void withoutASpawnTheCenterFallsBackToTheGivenPoint() {
        plate(worlds.world(0), 80, 120);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(true, 100, 100)
            .build();

        Point spot = spot(finder(41L).find(HERE));

        assertTrue(Math.hypot(spot.x() - 100.0D, spot.z() - 100.0D) <= 11.0D, () -> "центр не точка: " + spot.print());
    }

    @Test
    void theSpotKeepsTheDimensionAndTheCameraOfTheAsker() {
        plate(worlds.world(0), -20, 20);

        Point spot = spot(finder(43L).find(HERE));

        assertEquals(0, spot.dimension());
        assertEquals(90.0F, spot.yaw(), 0.0F);
        assertEquals(10.0F, spot.pitch(), 0.0F);
        assertEquals(GROUND + 1, spot.blockY(), "игрок встаёт на землю, а не в неё");
    }

    @Test
    void aTurnedOffCommandLooksForNothing() {
        EngineFixtures.FakeWorld world = worlds.world(0);
        plate(world, -20, 20);
        rtp = RandomRules.builder()
            .enabled(false)
            .build();

        RandomSpots.Reply reply = finder(47L).find(HERE);

        assertEquals(RandomSpots.Outcome.OFF, reply.outcome());
        assertEquals(0, reply.attempts());
        assertEquals(0, world.samples());
    }

    @Test
    void aWorldOutsideTheListIsRefusedBeforeTheSearch() {
        EngineFixtures.FakeWorld nether = worlds.world(-1);
        plate(nether, -20, 20);
        rtp = RandomRules.builder()
            .worlds(Collections.singletonList(Integer.valueOf(0)))
            .build();

        RandomSpots.Reply reply = finder(53L).find(Point.of(-1, 0.5D, 65.0D, 0.5D));

        assertEquals(RandomSpots.Outcome.WRONG_WORLD, reply.outcome());
        assertEquals(0, reply.attempts());
        assertEquals(0, nether.samples());
    }

    @Test
    void anEmptyListOfWorldsOpensEveryWorld() {
        plate(worlds.world(-1), -20, 20);
        rtp = RandomRules.builder()
            .radius(0, 10)
            .center(false, 0, 0)
            .worlds(Collections.<Integer>emptyList())
            .build();

        RandomSpots.Reply reply = finder(59L).find(Point.of(-1, 0.5D, 65.0D, 0.5D));

        assertEquals(RandomSpots.Outcome.FOUND, reply.outcome());
        assertEquals(-1, spot(reply).dimension());
    }

    @Test
    void aDimensionThatIsNotUpHasItsOwnAnswer() {
        RandomSpots.Reply reply = finder(61L).find(HERE);

        assertEquals(RandomSpots.Outcome.NO_WORLD, reply.outcome());
        assertEquals(0, reply.attempts());
    }

    private static RandomRules ring(int min, int max) {
        return RandomRules.builder()
            .radius(min, max)
            .center(false, 0, 0)
            .build();
    }

    private static void plate(EngineFixtures.FakeWorld world, int from, int to) {
        world.plate(GROUND, from, to);
    }

    private RandomFinder finder(long seed) {
        EngineRules rules = EngineRules.builder()
            .random(rtp)
            .build();
        return new RandomFinder(worlds, () -> policy, () -> rules, () -> spawns, new Random(seed));
    }

    private static Point spot(RandomSpots.Reply reply) {
        Point spot = reply.spot()
            .orElse(null);
        assertNotNull(spot, () -> "точка не нашлась: " + reply);
        return spot;
    }

    private static final class FakeSpawns implements SpawnService {

        private Point spawn;

        @Override
        public SpawnTable table() {
            return SpawnTable.empty();
        }

        @Override
        public Optional<Point> spawnFor(int dimension) {
            return Optional.ofNullable(spawn);
        }

        @Override
        public StoreResult setGlobalSpawn(Point point, String actor) {
            return StoreResult.success();
        }

        @Override
        public StoreResult setDimensionSpawn(Point point, String actor) {
            return StoreResult.success();
        }
    }
}
