package com.mrleonardos.codeessentials.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockSample;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

class SafeSpotFinderTest {

    private static final Point TARGET = Point.of(0, 0.5D, 64.0D, 0.5D, 90.0F, -12.5F);

    private final SafeSpotFinder finder = new SafeSpotFinder();

    @Test
    void policyKeepsItsName() {
        assertEquals("builtin", finder.id());
    }

    @Test
    void spotInMidAirIsExactBecauseTheHomeMayHangThere() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.defaults());

        assertEquals(SafeSpotResult.Outcome.EXACT, found.outcome());
        assertEquals(
            TARGET,
            found.spot()
                .get());
        assertEquals(1, found.probes(), "точная точка обязана стоить одну пробу");
    }

    @Test
    void lavaColumnSendsThePlayerToTheBankAndSaysTheSpotWasMoved() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        for (int y = 61; y <= 67; y++) {
            world.put(0, y, 0, BlockSample.LAVA);
        }
        world.floor(0, 60, 0);
        world.floor(1, 63, 0);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.defaults());

        assertEquals(SafeSpotResult.Outcome.CORRECTED, found.outcome());
        assertEquals(
            TARGET.withBlock(1, 64, 0),
            found.spot()
                .get());
        assertTrue(found.corrected());
    }

    @Test
    void walledUpPlayerGetsARefusalAndNobodyMoves() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 40; y <= 90; y++) {
                    world.put(x, y, z, BlockSample.SOLID);
                }
            }
        }

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.defaults());

        assertEquals(SafeSpotResult.Outcome.UNSAFE, found.outcome());
        assertFalse(
            found.spot()
                .isPresent());
        assertEquals(
            CancelReason.UNSAFE,
            found.failure()
                .get());
    }

    @Test
    void columnIsSearchedBeforeTheRings() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.put(0, 64, 0, BlockSample.SOLID);
        world.put(0, 65, 0, BlockSample.SOLID);
        world.floor(1, 63, 0);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.defaults());

        assertEquals(
            TARGET.withBlock(0, 66, 0),
            found.spot()
                .get(),
            "подъём в своей колонне идёт раньше колец");
    }

    @Test
    void waterUnderTheFeetSendsThePlayerDownToTheFloor() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.put(0, 64, 0, BlockSample.WATER);
        for (int y = 65; y <= 73; y++) {
            world.put(0, y, 0, BlockSample.SOLID);
        }
        world.floor(0, 59, 0);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.of(8, 16, 0, false));

        assertEquals(
            TARGET.withBlock(0, 60, 0),
            found.spot()
                .get());
    }

    @Test
    void waterIsFineWhenTheSettingAllowsIt() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.put(0, 64, 0, BlockSample.WATER);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.of(8, 16, 3, true));

        assertEquals(SafeSpotResult.Outcome.EXACT, found.outcome());
    }

    @Test
    void unloadedChunkStopsTheSearchBeforeASingleBlockIsRead() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.unloadChunk(0, 0);

        SafeSpotResult found = finder.find(world, TARGET, SafeSpotLimits.defaults());

        assertEquals(SafeSpotResult.Outcome.CHUNK_MISSING, found.outcome());
        assertEquals(0, found.probes());
        assertEquals(0, world.samples(), "незагруженный чанк не спрашивают о блоках");
        assertEquals(
            CancelReason.CHUNK_MISSING,
            found.failure()
                .get());
    }

    @Test
    void neighbourChunkNearTheEdgeHasToBeLoadedToo() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        world.unloadChunk(1, 0);
        Point edge = Point.of(0, 15.5D, 64.0D, 0.5D);

        assertEquals(
            SafeSpotResult.Outcome.CHUNK_MISSING,
            finder.find(world, edge, SafeSpotLimits.defaults())
                .outcome());
        assertEquals(
            SafeSpotResult.Outcome.EXACT,
            finder.find(world, Point.of(0, 8.5D, 64.0D, 8.5D), SafeSpotLimits.defaults())
                .outcome(),
            "в середине чанка соседей спрашивать незачем");
    }

    @Test
    void searchNeverGoesPastItsProbeCeiling() {
        EngineFixtures.FakeWorld world = new EngineFixtures.FakeWorld(0);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 0; y <= 255; y++) {
                    world.put(x, y, z, BlockSample.SOLID);
                }
            }
        }
        SafeSpotLimits tight = SafeSpotLimits.of(1, 1, 1, false);

        SafeSpotResult found = finder.find(world, TARGET, tight);

        assertEquals(SafeSpotResult.Outcome.UNSAFE, found.outcome());
        assertEquals(27, tight.maxProbes());
        assertTrue(found.probes() <= tight.maxProbes(), "проб больше потолка быть не может: " + found.probes());
        assertEquals(19, found.probes(), "три пробы в своей колонне и по две в каждой из восьми соседних");
    }

    @Test
    void forcedSpotStaysInsideTheWorldAndKeepsTheCamera() {
        Point overhead = Point.of(0, 10.5D, 900.0D, 20.5D, 45.0F, -10.0F);
        Point underground = Point.of(0, 10.5D, -50.0D, 20.5D, 45.0F, -10.0F);
        Point plain = Point.of(0, 10.5D, 70.0D, 20.5D, 45.0F, -10.0F);

        assertEquals(
            255.0D,
            SafeSpotFinder.forced(overhead, 256)
                .y());
        assertEquals(
            0.0D,
            SafeSpotFinder.forced(underground, 256)
                .y());
        assertEquals(
            45.0F,
            SafeSpotFinder.forced(overhead, 256)
                .yaw());
        assertEquals(
            10.5D,
            SafeSpotFinder.forced(overhead, 256)
                .x());
        assertEquals(plain, SafeSpotFinder.forced(plain, 256));
    }
}
