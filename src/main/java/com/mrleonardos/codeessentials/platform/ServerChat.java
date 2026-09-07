package com.mrleonardos.codeessentials.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codecore.platform.ServerTexts;

final class ServerChat {

    private ServerChat() {}

    /**
     * Сказать игроку строку по ключу перевода.
     *
     * <p>
     * Строку собирает сервер, а не клиент: клиентской части у мода нет, файла перевода на клиенте нет,
     * и {@code ChatComponentTranslation} доехал бы до игрока ключом
     * {@code codeessentials.message.kit_saved}.
     */
    static void tell(UUID player, String translationKey, Object... arguments) {
        EntityPlayerMP online = Players.online(player);
        if (online != null) {
            online.addChatMessage(ServerTexts.line(translationKey, arguments));
        }
    }
}
