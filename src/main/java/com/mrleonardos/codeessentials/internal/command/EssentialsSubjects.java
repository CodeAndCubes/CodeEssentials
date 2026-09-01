package com.mrleonardos.codeessentials.internal.command;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codeessentials.api.model.Point;

public interface EssentialsSubjects {

    Optional<UUID> playerOf(CommandContext context);

    String actorOf(CommandContext context);

    boolean allowed(CommandContext context, String node);

    Optional<Point> positionOf(CommandContext context);

    Optional<Point> positionOf(UUID player);

    Optional<UUID> resolve(String name);

    Optional<String> nameOf(UUID player);

    boolean online(UUID player);

    void tell(UUID player, String translationKey, Object... arguments);
}
