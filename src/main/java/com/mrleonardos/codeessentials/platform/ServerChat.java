package com.mrleonardos.codeessentials.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.mrleonardos.codecore.platform.Players;

final class ServerChat {

    private ServerChat() {}

    static void tell(UUID player, String translationKey, Object... arguments) {
        EntityPlayerMP online = Players.online(player);
        if (online != null) {
            online.addChatMessage(new ChatComponentTranslation(translationKey, arguments));
        }
    }
}
