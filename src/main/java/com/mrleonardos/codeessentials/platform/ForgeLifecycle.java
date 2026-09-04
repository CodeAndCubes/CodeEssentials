package com.mrleonardos.codeessentials.platform;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.internal.command.EssentialsMessages;
import com.mrleonardos.codeessentials.internal.engine.RequestBoard;
import com.mrleonardos.codeessentials.internal.engine.TeleportEngine;
import com.mrleonardos.codeessentials.internal.service.StateWriter;
import com.mrleonardos.codeessentials.internal.store.SingleWriter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public final class ForgeLifecycle {

    static final int RESPAWN_DIMENSION = 0;

    private final TeleportEngine engine;
    private final RequestBoard board;
    private final StateWriter state;
    private final RespawnChoice respawns;
    private final Supplier<KitService> kits;
    private final Scheduler scheduler;

    ForgeLifecycle(TeleportEngine engine, RequestBoard board, StateWriter state, RespawnChoice respawns,
        Supplier<KitService> kits, Scheduler scheduler) {
        this.engine = engine;
        this.board = board;
        this.state = state;
        this.respawns = respawns;
        this.kits = kits;
        this.scheduler = scheduler;
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        StoreResult remembered = state.remember(player.getUniqueID(), player.getCommandSenderName());
        if (!remembered.successful()) {
            CodeEssentialsMod.LOG
                .warn("Name of {} was not written down: {}", player.getCommandSenderName(), remembered);
        }
        remindOfBuffer(player.getUniqueID());
    }

    private void remindOfBuffer(UUID player) {
        KitService service = kits.get();
        for (Map.Entry<String, Integer> pending : service.pendingByKit(player)
            .entrySet()) {
            if (service.kit(pending.getKey())
                .isPresent()) {
                ServerChat.tell(
                    player,
                    EssentialsMessages.KIT_PENDING,
                    pending.getKey(),
                    pending.getValue(),
                    pending.getKey());
            }
        }
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = id(event.player);
        engine.left(player);
        board.left(player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        UUID player = id(event.player);
        scheduler.afterTicks(1, () -> engine.respawned(player));
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        engine.dimensionChanged(id(event.player));
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        UUID id = player.getUniqueID();
        engine.died(id, Points.of(player));
        boolean bedded = player.getBedLocation(RESPAWN_DIMENSION) != null;
        Point target = respawns.pointFor(bedded, RESPAWN_DIMENSION)
            .orElse(null);
        if (target == null) {
            return;
        }
        engine.request(
            TeleportRequest.builder(id, target, TeleportCause.RESPAWN)
                .safeSpot(true)
                .actor(SingleWriter.AUTHOR)
                .build());
    }

    private static UUID id(EntityPlayer player) {
        return player.getUniqueID();
    }
}
