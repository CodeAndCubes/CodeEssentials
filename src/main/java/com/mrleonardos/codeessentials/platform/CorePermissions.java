package com.mrleonardos.codeessentials.platform;

import java.util.OptionalInt;
import java.util.UUID;

import net.minecraft.command.ICommandSender;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeessentials.internal.engine.PlayerRights;
import com.mrleonardos.codeessentials.internal.service.PlayerMeta;

final class CorePermissions implements PlayerRights, PlayerMeta {

    private final Logger log;
    private boolean missingTold;

    CorePermissions(Logger log) {
        this.log = log;
    }

    @Override
    public boolean has(UUID player, String node) {
        PermissionService service = service();
        return service != null && service.has(player, node);
    }

    @Override
    public OptionalInt number(UUID player, String key) {
        String raw = value(player, key, null);
        if (raw == null || raw.trim()
            .isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            log.warn("Meta {} of {} is not a number and is read as absent: {}", key, player, raw);
            return OptionalInt.empty();
        }
    }

    @Override
    public String value(UUID player, String key, String fallback) {
        PermissionService service = service();
        return service == null ? fallback : service.meta(player, key, fallback);
    }

    boolean allowed(ICommandSender sender, String node) {
        PermissionService service = service();
        return service == null || service.has(sender, node);
    }

    private PermissionService service() {
        PermissionService held = CodeApi.services()
            .find(PermissionService.class)
            .orElse(null);
        if (held == null && !missingTold) {
            missingTold = true;
            log.error("No mod holds PermissionService, every check of CodeEssentials answers no");
        }
        return held;
    }
}
