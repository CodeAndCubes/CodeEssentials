package com.mrleonardos.codeessentials.internal.engine;

import java.util.OptionalInt;
import java.util.UUID;

public interface PlayerRights {

    boolean has(UUID player, String node);

    OptionalInt number(UUID player, String key);
}
