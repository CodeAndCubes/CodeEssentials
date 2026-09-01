package com.mrleonardos.codeessentials.api.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;

class SafeSpotTest {

    private static final Point TARGET = Point.of(0, 0.5D, 64.0D, 0.5D, 90.0F, 0.0F);

    @Test
    void blockIsEitherPassableOrSolid() {
        assertThrows(IllegalArgumentException.class, () -> BlockSample.of(true, true, false, false));

        BlockSample cactus = BlockSample.of(false, true, true, false);

        assertTrue(cactus.solid());
        assertTrue(cactus.harmful());
        assertFalse(cactus.passable());
        assertFalse(cactus.liquid());
        assertEquals(cactus, BlockSample.of(false, true, true, false));
        assertEquals(
            cactus.hashCode(),
            BlockSample.of(false, true, true, false)
                .hashCode());
        assertFalse(cactus.equals(BlockSample.SOLID));
        assertFalse(cactus.equals("cactus"));
    }

    @Test
    void factoryBlocksSayWhatTheyAre() {
        assertTrue(BlockSample.AIR.passable());
        assertFalse(BlockSample.AIR.solid());
        assertTrue(BlockSample.SOLID.solid());
        assertTrue(BlockSample.WATER.liquid());
        assertFalse(BlockSample.WATER.harmful());
        assertTrue(BlockSample.LAVA.harmful());
        assertTrue(BlockSample.LAVA.liquid());
        assertTrue(BlockSample.FIRE.harmful());
        assertFalse(BlockSample.FIRE.liquid());
        assertEquals("passable harmful liquid", BlockSample.LAVA.toString());
    }

    @Test
    void playerFitsOnGroundAndNowhereNearLava() {
        FakeWorld world = new FakeWorld();
        world.ground(0, 63, 0);
        world.put(1, 64, 0, BlockSample.LAVA);
        world.put(2, 64, 0, BlockSample.FIRE);

        assertTrue(world.passableSpot(0, 64, 0, false));
        assertTrue(world.solidFloorUnder(0, 64, 0));
        assertFalse(world.passableSpot(1, 64, 0, false));
        assertFalse(world.passableSpot(1, 64, 0, true));
        assertFalse(world.passableSpot(2, 64, 0, false));
    }

    @Test
    void walledUpPlayerHasNowhereToStand() {
        FakeWorld world = new FakeWorld();
        world.ground(0, 63, 0);
        world.put(0, 65, 0, BlockSample.SOLID);

        assertFalse(world.passableSpot(0, 64, 0, false));
    }

    @Test
    void waterNeedsPermission() {
        FakeWorld world = new FakeWorld();
        world.ground(0, 63, 0);
        world.put(0, 64, 0, BlockSample.WATER);

        assertFalse(world.passableSpot(0, 64, 0, false));
        assertTrue(world.passableSpot(0, 64, 0, true));
    }

    @Test
    void worldEdgesAreNotSpots() {
        FakeWorld world = new FakeWorld();
        world.ground(0, 63, 0);

        assertFalse(world.passableSpot(0, -1, 0, false));
        assertFalse(world.passableSpot(0, 255, 0, false));
        assertFalse(world.solidFloorUnder(0, 0, 0));
    }

    @Test
    void burningFloorIsNoFloor() {
        FakeWorld world = new FakeWorld();
        world.put(0, 63, 0, BlockSample.of(false, true, true, false));

        assertFalse(world.solidFloorUnder(0, 64, 0));
        assertFalse(world.solidFloorUnder(5, 64, 5));
    }

    @Test
    void limitsCountTheirWorstCase() {
        SafeSpotLimits limits = SafeSpotLimits.defaults();

        assertEquals(8, limits.maxUp());
        assertEquals(16, limits.maxDown());
        assertEquals(3, limits.radius());
        assertFalse(limits.liquidOk());
        assertEquals(1225, limits.maxProbes());
        assertEquals(
            25,
            SafeSpotLimits.of(8, 16, 0, false)
                .maxProbes());
    }

    @Test
    void limitsFromConfigStayInBounds() {
        SafeSpotLimits greedy = SafeSpotLimits.of(9999, 9999, 9999, true);
        SafeSpotLimits negative = SafeSpotLimits.of(-1, -1, -1, false);

        assertEquals(256, greedy.maxUp());
        assertEquals(256, greedy.maxDown());
        assertEquals(8, greedy.radius());
        assertTrue(greedy.liquidOk());
        assertEquals(0, negative.maxUp());
        assertEquals(0, negative.maxDown());
        assertEquals(0, negative.radius());
        assertEquals(1, negative.maxProbes());
        assertEquals(SafeSpotLimits.defaults(), SafeSpotLimits.of(8, 16, 3, false));
        assertEquals(
            SafeSpotLimits.defaults()
                .hashCode(),
            SafeSpotLimits.of(8, 16, 3, false)
                .hashCode());
        assertFalse(
            SafeSpotLimits.defaults()
                .equals(greedy));
        assertFalse(
            SafeSpotLimits.defaults()
                .equals("limits"));
    }

    @Test
    void foundSpotTellsTheTruthAboutTheCorrection() {
        SafeSpotResult exact = SafeSpotResult.found(TARGET, TARGET, 1);
        SafeSpotResult aside = SafeSpotResult.found(TARGET, TARGET.withBlock(2, 64, 0), 17);

        assertEquals(SafeSpotResult.Outcome.EXACT, exact.outcome());
        assertTrue(exact.found());
        assertFalse(exact.corrected());
        assertEquals(1, exact.probes());
        assertFalse(
            exact.failure()
                .isPresent());

        assertEquals(SafeSpotResult.Outcome.CORRECTED, aside.outcome());
        assertTrue(aside.corrected());
        assertEquals(
            TARGET.withBlock(2, 64, 0),
            aside.spot()
                .get());
        assertEquals(17, aside.probes());
    }

    @Test
    void refusalCarriesTheReasonForTheJob() {
        SafeSpotResult unsafe = SafeSpotResult.unsafe(1225);
        SafeSpotResult missing = SafeSpotResult.chunkMissing();

        assertFalse(unsafe.found());
        assertFalse(unsafe.corrected());
        assertFalse(
            unsafe.spot()
                .isPresent());
        assertEquals(
            CancelReason.UNSAFE,
            unsafe.failure()
                .get());
        assertEquals(1225, unsafe.probes());

        assertFalse(missing.found());
        assertEquals(0, missing.probes());
        assertEquals(
            CancelReason.CHUNK_MISSING,
            missing.failure()
                .get());

        assertThrows(IllegalArgumentException.class, () -> SafeSpotResult.unsafe(-1));
        assertThrows(NullPointerException.class, () -> SafeSpotResult.found(TARGET, null, 0));
    }

    @Test
    void resultsCompareByEveryField() {
        SafeSpotResult exact = SafeSpotResult.found(TARGET, TARGET, 1);

        assertEquals(exact, SafeSpotResult.found(TARGET, TARGET, 1));
        assertEquals(
            exact.hashCode(),
            SafeSpotResult.found(TARGET, TARGET, 1)
                .hashCode());
        assertFalse(exact.equals(SafeSpotResult.found(TARGET, TARGET, 2)));
        assertFalse(exact.equals(SafeSpotResult.unsafe(1)));
        assertFalse(exact.equals("exact"));
        assertTrue(
            SafeSpotResult.unsafe(3)
                .toString()
                .contains("3 probe(s)"));
    }

    @Test
    void unloadedChunkIsVisibleBeforeAnySampling() {
        FakeWorld world = new FakeWorld();
        world.unload(100, 100);

        assertTrue(world.chunkLoaded(0, 0));
        assertFalse(world.chunkLoaded(100, 100));
        assertEquals(0, world.dimension());
        assertEquals(256, world.height());
    }

    private static final class FakeWorld implements BlockView {

        private final Map<String, BlockSample> blocks = new HashMap<>();
        private final Set<String> unloaded = new HashSet<>();

        void put(int x, int y, int z, BlockSample sample) {
            blocks.put(x + ":" + y + ":" + z, sample);
        }

        void ground(int x, int y, int z) {
            put(x, y, z, BlockSample.SOLID);
        }

        void unload(int x, int z) {
            unloaded.add(x + ":" + z);
        }

        @Override
        public int dimension() {
            return 0;
        }

        @Override
        public boolean chunkLoaded(int blockX, int blockZ) {
            return !unloaded.contains(blockX + ":" + blockZ);
        }

        @Override
        public BlockSample sample(int blockX, int blockY, int blockZ) {
            BlockSample held = blocks.get(blockX + ":" + blockY + ":" + blockZ);
            return held == null ? BlockSample.AIR : held;
        }

        @Override
        public int height() {
            return 256;
        }
    }
}
