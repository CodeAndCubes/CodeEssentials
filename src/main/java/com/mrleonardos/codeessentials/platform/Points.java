package com.mrleonardos.codeessentials.platform;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codeessentials.api.model.Point;

final class Points {

    private Points() {}

    static Point of(EntityPlayerMP player) {
        return Point
            .of(player.dimension, player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
    }
}
