package com.mrleonardos.codeessentials.internal.service;

import java.util.UUID;

public interface PlayerMeta {

    String value(UUID player, String key, String fallback);
}
