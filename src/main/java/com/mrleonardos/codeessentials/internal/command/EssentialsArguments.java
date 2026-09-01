package com.mrleonardos.codeessentials.internal.command;

import com.mrleonardos.codecore.api.command.ArgumentType;

public interface EssentialsArguments {

    ArgumentType<String> playerName();

    ArgumentType<String> homeName();

    ArgumentType<String> warpName();

    ArgumentType<Double> coordinate();
}
