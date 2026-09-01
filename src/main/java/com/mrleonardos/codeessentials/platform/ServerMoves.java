package com.mrleonardos.codeessentials.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codeessentials.api.model.Point;

final class ServerMoves implements WorldMover.Moves {

    private static final int NOWHERE = Integer.MIN_VALUE;

    private final ServerWorlds worlds;

    ServerMoves(ServerWorlds worlds) {
        this.worlds = worlds;
    }

    @Override
    public boolean online(UUID player) {
        return Players.online(player) != null;
    }

    @Override
    public boolean world(int dimension) {
        return worlds.server(dimension) != null;
    }

    @Override
    public boolean chunks(Point landing) {
        WorldServer world = worlds.server(landing.dimension());
        return world != null && worlds.ready(world, landing);
    }

    @Override
    public int dimensionOf(UUID player) {
        EntityPlayerMP online = Players.online(player);
        return online == null ? NOWHERE : online.dimension;
    }

    @Override
    public void dismount(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online != null && online.ridingEntity != null) {
            online.mountEntity(null);
        }
    }

    @Override
    public void closeScreen(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online != null && online.openContainer != online.inventoryContainer) {
            online.closeScreen();
        }
    }

    @Override
    public void transfer(UUID player, Point landing) {
        EntityPlayerMP online = Players.online(player);
        WorldServer world = worlds.server(landing.dimension());
        MinecraftServer server = MinecraftServer.getServer();
        if (online == null || world == null || server == null) {
            return;
        }
        server.getConfigurationManager()
            .transferPlayerToDimension(online, landing.dimension(), new NoPortalTeleporter(world));
    }

    @Override
    public void place(UUID player, Point landing) {
        EntityPlayerMP online = Players.online(player);
        if (online != null && online.playerNetServerHandler != null) {
            online.playerNetServerHandler
                .setPlayerLocation(landing.x(), landing.y(), landing.z(), landing.yaw(), landing.pitch());
        }
    }

    @Override
    public void settle(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return;
        }
        online.fallDistance = 0.0F;
        online.motionX = 0.0D;
        online.motionY = 0.0D;
        online.motionZ = 0.0D;
    }
}
