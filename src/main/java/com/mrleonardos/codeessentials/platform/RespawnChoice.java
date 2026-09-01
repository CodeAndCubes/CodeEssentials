package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.function.Supplier;

import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.model.Point;

final class RespawnChoice {

    private final Supplier<SpawnService> spawns;

    RespawnChoice(Supplier<SpawnService> spawns) {
        this.spawns = spawns;
    }

    Optional<Point> pointFor(boolean bedded, int dimension) {
        if (bedded) {
            return Optional.empty();
        }
        return spawns.get()
            .spawnFor(dimension);
    }
}
