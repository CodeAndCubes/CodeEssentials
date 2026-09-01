package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.StoreResult;

class RespawnChoiceTest {

    private static final Point SPAWN = Point.of(0, 10.5D, 65.0D, -3.5D);

    @Test
    void aBedBeatsTheServerSpawn() {
        RespawnChoice choice = new RespawnChoice(() -> new Spawns(SPAWN));

        assertFalse(
            choice.pointFor(true, 0)
                .isPresent());
    }

    @Test
    void withoutABedTheServerSpawnIsTaken() {
        RespawnChoice choice = new RespawnChoice(() -> new Spawns(SPAWN));

        assertEquals(
            SPAWN,
            choice.pointFor(false, 0)
                .orElse(null));
    }

    @Test
    void withoutABedAndWithoutASpawnNothingIsSubstituted() {
        RespawnChoice choice = new RespawnChoice(() -> new Spawns(null));

        assertFalse(
            choice.pointFor(false, 0)
                .isPresent());
    }

    private static final class Spawns implements SpawnService {

        private final Point point;

        Spawns(Point point) {
            this.point = point;
        }

        @Override
        public SpawnTable table() {
            return SpawnTable.empty();
        }

        @Override
        public Optional<Point> spawnFor(int dimension) {
            return Optional.ofNullable(point);
        }

        @Override
        public StoreResult setGlobalSpawn(Point spot, String actor) {
            return StoreResult.success();
        }

        @Override
        public StoreResult setDimensionSpawn(Point spot, String actor) {
            return StoreResult.success();
        }
    }
}
