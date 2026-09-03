package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.command.SenderKind;
import com.mrleonardos.codecore.api.command.SenderPosition;
import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codecore.platform.Senders;
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
        return senderOf(context).player()
            .map(PlayerRef::id);
    }

    @Override
    public String actorOf(CommandContext context) {
        return actorOf(senderOf(context));
    }

    @Override
    public boolean allowed(CommandContext context, String node) {
        return permissions.allowed(senderOf(context), node);
    }

    @Override
    public Optional<Point> positionOf(CommandContext context) {
        return playerOf(context).flatMap(Points::of);
    }

    @Override
    public Optional<Point> positionOf(UUID player) {
        return Points.of(player);
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
        ServerChat.tell(player, translationKey, arguments);
    }

    static String actorOf(CommandSender sender) {
        if (sender.kind() == SenderKind.PLAYER) {
            return sender.name();
        }
        if (sender.kind() == SenderKind.COMMAND_BLOCK) {
            return sender.position()
                .map(SenderSubjects::block)
                .orElse(COMMAND_BLOCK);
        }
        return sender.kind() == SenderKind.RCON ? RCON : CONSOLE;
    }

    private static String block(SenderPosition at) {
        return COMMAND_BLOCK + "@" + at.x() + "," + at.y() + "," + at.z();
    }

    private static CommandSender senderOf(CommandContext context) {
        return Senders.of(context.sender());
    }
}
