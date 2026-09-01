package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.engine.SafeSpotFinder;
import com.mrleonardos.codeessentials.internal.engine.WorldAccess;
import com.mrleonardos.codeessentials.internal.service.SpotCheck;

final class ServerWorlds implements WorldAccess, SpotCheck {

    private final Supplier<EssentialsSettings> settings;
    private final Supplier<SafeSpotPolicy> policy;
    private final Logger log;

    ServerWorlds(Supplier<EssentialsSettings> settings, Supplier<SafeSpotPolicy> policy, Logger log) {
        this.settings = settings;
        this.policy = policy;
        this.log = log;
    }

    @Override
    public Optional<BlockView> view(int dimension) {
        WorldServer world = server(dimension);
        return world == null ? Optional.<BlockView>empty() : Optional.<BlockView>of(validator(world));
    }

    @Override
    public Optional<Point> position(UUID player) {
        EntityPlayerMP online = Players.online(player);
        return online == null ? Optional.<Point>empty() : Optional.of(Points.of(online));
    }

    @Override
    public SafeSpotResult check(Point point) {
        BlockView view = view(point.dimension()).orElse(null);
        if (view == null) {
            return SafeSpotResult.chunkMissing();
        }
        EssentialsSettings current = settings.get();
        return policy.get()
            .find(view, point, current.spotLimits(current.ceilings()));
    }

    WorldServer server(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world != null) {
            return world;
        }
        if (!DimensionManager.isDimensionRegistered(dimension)) {
            return null;
        }
        try {
            DimensionManager.initDimension(dimension);
        } catch (RuntimeException failure) {
            log.error("Dimension {} did not come up: {}", Integer.valueOf(dimension), failure.toString(), failure);
            return null;
        }
        return DimensionManager.getWorld(dimension);
    }

    boolean ready(WorldServer world, Point point) {
        BlockView view = validator(world);
        int x = point.blockX();
        int z = point.blockZ();
        for (int dx = -SafeSpotFinder.CHUNK_EDGE; dx <= SafeSpotFinder.CHUNK_EDGE; dx += SafeSpotFinder.CHUNK_EDGE) {
            for (int dz = -SafeSpotFinder.CHUNK_EDGE; dz
                <= SafeSpotFinder.CHUNK_EDGE; dz += SafeSpotFinder.CHUNK_EDGE) {
                if (!view.bringUpChunk(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private SafeSpotValidator validator(WorldServer world) {
        return new SafeSpotValidator(
            world,
            settings.get()
                .generateChunks(),
            log);
    }
}
