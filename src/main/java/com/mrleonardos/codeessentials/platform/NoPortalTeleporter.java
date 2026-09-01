package com.mrleonardos.codeessentials.platform;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public final class NoPortalTeleporter extends Teleporter {

    public NoPortalTeleporter(WorldServer world) {
        super(world);
    }

    @Override
    public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float yaw) {
        return true;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {}
}
