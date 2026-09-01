package com.mrleonardos.codeessentials.platform;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.mrleonardos.codeessentials.internal.engine.TeleportEngine;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class PlayerTracker {

    private final TeleportEngine engine;
    private boolean watching;

    PlayerTracker(TeleportEngine engine) {
        this.engine = engine;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (engine.idle()) {
            rest();
            return;
        }
        watch();
        engine.tick();
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.entityLiving instanceof EntityPlayerMP) {
            engine.damaged(((EntityPlayerMP) event.entityLiving).getUniqueID());
        }
    }

    void rest() {
        if (watching) {
            MinecraftForge.EVENT_BUS.unregister(this);
            watching = false;
        }
    }

    boolean watching() {
        return watching;
    }

    private void watch() {
        if (!watching) {
            MinecraftForge.EVENT_BUS.register(this);
            watching = true;
        }
    }
}
