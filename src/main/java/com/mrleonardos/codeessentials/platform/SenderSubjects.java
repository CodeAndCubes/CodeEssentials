package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.internal.command.EssentialsSubjects;

final class SenderSubjects implements EssentialsSubjects {

    static final String CONSOLE = "console";
    static final String RCON = "rcon";
    static final String COMMAND_BLOCK = "commandblock";

    private final NameResolver names;
    private final CorePermissions permissions;

    SenderSubjects(NameResolver names, CorePermissions permissions) {
        this.names = names;
        this.permissions = permissions;
    }

    @Override
    public Optional<UUID> playerOf(CommandContext context) {
        EntityPlayerMP player = context.player();
        return player == null ? Optional.<UUID>empty() : Optional.of(player.getUniqueID());
    }

    @Override
    public String actorOf(CommandContext context) {
        return actorOf(context.sender());
    }

    @Override
    public boolean allowed(CommandContext context, String node) {
        return permissions.allowed(context.sender(), node);
    }

    @Override
    public Optional<Point> positionOf(CommandContext context) {
        EntityPlayerMP player = context.player();
        return player == null ? Optional.<Point>empty() : Optional.of(Points.of(player));
    }

    @Override
    public Optional<Point> positionOf(UUID player) {
        EntityPlayerMP online = Players.online(player);
        return online == null ? Optional.<Point>empty() : Optional.of(Points.of(online));
    }

    @Override
    public Optional<UUID> resolve(String name) {
        return names.id(name);
    }

    @Override
    public Optional<String> nameOf(UUID player) {
        return names.name(player);
    }

    @Override
    public boolean online(UUID player) {
        return Players.online(player) != null;
    }

    @Override
    public void tell(UUID player, String translationKey, Object... arguments) {
        EntityPlayerMP online = Players.online(player);
        if (online != null) {
            online.addChatMessage(new ChatComponentTranslation(translationKey, arguments));
        }
    }

    static String actorOf(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return sender.getCommandSenderName();
        }
        if (sender instanceof CommandBlockLogic) {
            ChunkCoordinates at = sender.getPlayerCoordinates();
            return at == null ? COMMAND_BLOCK : COMMAND_BLOCK + "@" + at.posX + "," + at.posY + "," + at.posZ;
        }
        return sender instanceof RConConsoleSource ? RCON : CONSOLE;
    }
}
