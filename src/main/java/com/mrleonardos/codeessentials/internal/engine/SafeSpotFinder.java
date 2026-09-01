package com.mrleonardos.codeessentials.internal.engine;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

public final class SafeSpotFinder implements SafeSpotPolicy {

    public static final String ID = "builtin";
    public static final int CHUNK_EDGE = 2;

    private static final int NOTHING = Integer.MIN_VALUE;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
        int x = hint.blockX();
        int y = hint.blockY();
        int z = hint.blockZ();
        if (!loadedAround(view, x, z)) {
            return SafeSpotResult.chunkMissing();
        }
        Probes probes = new Probes(limits.maxProbes());
        if (probes.spend() && view.passableSpot(x, y, z, limits.liquidOk())) {
            return SafeSpotResult.found(hint, hint, probes.spent());
        }
        int straight = target(view, x, y, z, limits, probes);
        if (straight != NOTHING) {
            return SafeSpotResult.found(hint, hint.withBlock(x, straight, z), probes.spent());
        }
        for (int radius = 1; radius <= limits.radius() && !probes.spentAll(); radius++) {
            for (int dx = -radius; dx <= radius && !probes.spentAll(); dx++) {
                for (int dz = -radius; dz <= radius && !probes.spentAll(); dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int ringX = x + dx;
                    int ringZ = z + dz;
                    if (!view.chunkLoaded(ringX, ringZ)) {
                        continue;
                    }
                    int spot = ring(view, ringX, y, ringZ, limits, probes);
                    if (spot != NOTHING) {
                        return SafeSpotResult.found(hint, hint.withBlock(ringX, spot, ringZ), probes.spent());
                    }
                }
            }
        }
        return SafeSpotResult.unsafe(probes.spent());
    }

    public static Point forced(Point destination, int height) {
        double ceiling = Math.max(0.0D, height - 1.0D);
        double y = Math.max(0.0D, Math.min(destination.y(), ceiling));
        if (Double.compare(y, destination.y()) == 0) {
            return destination;
        }
        return destination.withPosition(destination.x(), y, destination.z());
    }

    private int target(BlockView view, int x, int y, int z, SafeSpotLimits limits, Probes probes) {
        int up = upwards(view, x, y, z, limits, probes);
        if (up != NOTHING) {
            return up;
        }
        for (int step = 1; step <= limits.maxDown(); step++) {
            if (!probes.spend()) {
                return NOTHING;
            }
            if (standable(view, x, y - step, z, limits)) {
                return y - step;
            }
        }
        return NOTHING;
    }

    private int ring(BlockView view, int x, int y, int z, SafeSpotLimits limits, Probes probes) {
        if (!probes.spend()) {
            return NOTHING;
        }
        if (standable(view, x, y, z, limits)) {
            return y;
        }
        return upwards(view, x, y, z, limits, probes);
    }

    private int upwards(BlockView view, int x, int y, int z, SafeSpotLimits limits, Probes probes) {
        for (int step = 1; step <= limits.maxUp(); step++) {
            if (!probes.spend()) {
                return NOTHING;
            }
            if (standable(view, x, y + step, z, limits)) {
                return y + step;
            }
        }
        return NOTHING;
    }

    private boolean standable(BlockView view, int x, int y, int z, SafeSpotLimits limits) {
        return view.solidFloorUnder(x, y, z) && view.passableSpot(x, y, z, limits.liquidOk());
    }

    private static boolean loadedAround(BlockView view, int x, int z) {
        if (!view.chunkLoaded(x, z)) {
            return false;
        }
        for (int dx = -CHUNK_EDGE; dx <= CHUNK_EDGE; dx += CHUNK_EDGE) {
            for (int dz = -CHUNK_EDGE; dz <= CHUNK_EDGE; dz += CHUNK_EDGE) {
                if (((x + dx) >> 4) == (x >> 4) && ((z + dz) >> 4) == (z >> 4)) {
                    continue;
                }
                if (!view.chunkLoaded(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class Probes {

        private final int ceiling;
        private int spent;

        Probes(int ceiling) {
            this.ceiling = Math.max(1, ceiling);
        }

        boolean spend() {
            if (spent >= ceiling) {
                return false;
            }
            spent++;
            return true;
        }

        boolean spentAll() {
            return spent >= ceiling;
        }

        int spent() {
            return spent;
        }
    }
}
