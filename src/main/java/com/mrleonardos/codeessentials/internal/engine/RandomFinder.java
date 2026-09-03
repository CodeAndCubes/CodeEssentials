package com.mrleonardos.codeessentials.internal.engine;

import java.util.Random;
import java.util.function.Supplier;

import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.command.RandomSpots;

public final class RandomFinder implements RandomSpots {

    public static final int NO_SURFACE = -1;

    private static final double FULL_TURN = Math.PI * 2.0D;
    private static final int HEAD_ROOM = 3;

    private final WorldAccess world;
    private final Supplier<SafeSpotPolicy> policy;
    private final Supplier<EngineRules> rules;
    private final Supplier<SpawnService> spawns;
    private final Random random;

    public RandomFinder(WorldAccess world, Supplier<SafeSpotPolicy> policy, Supplier<EngineRules> rules,
        Supplier<SpawnService> spawns, Random random) {
        this.world = world;
        this.policy = policy;
        this.rules = rules;
        this.spawns = spawns;
        this.random = random;
    }

    @Override
    public Reply find(Point origin) {
        EngineRules current = rules.get();
        RandomRules rtp = current.random();
        if (!rtp.enabled()) {
            return Reply.refused(Outcome.OFF, 0);
        }
        int dimension = origin.dimension();
        if (!rtp.allows(dimension)) {
            return Reply.refused(Outcome.WRONG_WORLD, 0);
        }
        BlockView view = world.view(dimension)
            .orElse(null);
        if (view == null) {
            return Reply.refused(Outcome.NO_WORLD, 0);
        }
        Point center = center(rtp, dimension);
        int spent = 0;
        while (spent < rtp.attempts()) {
            spent++;
            Point spot = attempt(view, rtp, current.safeSpot(), origin, center);
            if (spot != null) {
                return Reply.found(spot, spent);
            }
        }
        if (rtp.fallbackToSpawn()) {
            Point spawn = spawn(dimension);
            if (spawn != null) {
                return Reply.spawn(spawn, spent);
            }
        }
        return Reply.refused(Outcome.NOT_FOUND, spent);
    }

    private Point attempt(BlockView view, RandomRules rtp, SafeSpotLimits limits, Point origin, Point center) {
        double angle = random.nextDouble() * FULL_TURN;
        double min = rtp.minRadius();
        double max = rtp.maxRadius();
        double radius = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        int x = (int) Math.floor(center.x() + radius * Math.cos(angle));
        int z = (int) Math.floor(center.z() + radius * Math.sin(angle));
        if (rtp.blocked(view.biome(x, z))) {
            return null;
        }
        if (!view.bringUpChunk(x, z)) {
            return null;
        }
        int surface = surface(view, x, z);
        if (surface == NO_SURFACE) {
            return null;
        }
        SafeSpotResult found = policy.get()
            .find(view, origin.withBlock(x, surface, z), limits);
        if (!found.found()) {
            return null;
        }
        Point spot = found.spot()
            .get();
        return standable(view, spot, limits.liquidOk()) ? spot : null;
    }

    private Point center(RandomRules rtp, int dimension) {
        if (rtp.centerAtSpawn()) {
            Point spawn = spawn(dimension);
            if (spawn != null) {
                return spawn;
            }
        }
        return Point.of(dimension, rtp.centerX(), 0.0D, rtp.centerZ());
    }

    private Point spawn(int dimension) {
        SpawnService service = spawns.get();
        return service == null ? null
            : service.spawnFor(dimension)
                .orElse(null);
    }

    private static int surface(BlockView view, int x, int z) {
        for (int y = view.height() - HEAD_ROOM; y > 0; y--) {
            if (view.sample(x, y, z)
                .solid()) {
                return y + 1;
            }
        }
        return NO_SURFACE;
    }

    private static boolean standable(BlockView view, Point spot, boolean liquidOk) {
        int x = spot.blockX();
        int y = spot.blockY();
        int z = spot.blockZ();
        return view.solidFloorUnder(x, y, z) && view.passableSpot(x, y, z, liquidOk);
    }
}
