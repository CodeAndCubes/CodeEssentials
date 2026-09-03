package com.mrleonardos.codeessentials.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.internal.command.EssentialsArguments;
import com.mrleonardos.codeessentials.internal.command.EssentialsMessages;
import com.mrleonardos.codeessentials.internal.command.Nodes;

final class PlatformArguments implements EssentialsArguments {

    private static final int SUGGESTION_LIMIT = 50;

    private final NameResolver names;
    private final Supplier<HomeService> homes;
    private final Supplier<WarpService> warps;
    private final CorePermissions permissions;

    PlatformArguments(NameResolver names, Supplier<HomeService> homes, Supplier<WarpService> warps,
        CorePermissions permissions) {
        this.names = names;
        this.homes = homes;
        this.warps = warps;
        this.permissions = permissions;
    }

    @Override
    public ArgumentType<String> playerName() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                return raw.trim();
            }

            @Override
            public List<String> suggestions(ICommandSender sender, String partial) {
                return names.suggest(partial, SUGGESTION_LIMIT);
            }
        };
    }

    @Override
    public ArgumentType<String> homeName() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                return lower(raw);
            }

            @Override
            public List<String> suggestions(ICommandSender sender, String partial) {
                PlayerRef player = Senders.of(sender)
                    .player()
                    .orElse(null);
                if (player == null) {
                    return Collections.emptyList();
                }
                return startingWith(
                    homes.get()
                        .homes(player.id())
                        .keySet(),
                    partial);
            }
        };
    }

    @Override
    public ArgumentType<String> warpName() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                return lower(raw);
            }

            @Override
            public List<String> suggestions(ICommandSender sender, String partial) {
                CommandSender who = Senders.of(sender);
                List<String> open = new ArrayList<>();
                for (String name : warps.get()
                    .warps()
                    .keySet()) {
                    if (permissions.allowed(who, Nodes.warpGo(name))) {
                        open.add(name);
                    }
                }
                return startingWith(open, partial);
            }
        };
    }

    @Override
    public ArgumentType<Double> coordinate() {
        return new ArgumentType<Double>() {

            @Override
            public Double parse(String raw) {
                try {
                    double value = Double.parseDouble(raw.trim());
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        throw new CommandException(EssentialsMessages.ERROR_BAD_ARGUMENTS, raw);
                    }
                    return Double.valueOf(value);
                } catch (NumberFormatException notANumber) {
                    throw new CommandException(EssentialsMessages.ERROR_BAD_ARGUMENTS, raw);
                }
            }
        };
    }

    private static String lower(String raw) {
        return raw == null ? ""
            : raw.trim()
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> startingWith(Iterable<String> candidates, String partial) {
        List<String> found = new ArrayList<>();
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == SUGGESTION_LIMIT) {
                    break;
                }
            }
        }
        return found;
    }
}
