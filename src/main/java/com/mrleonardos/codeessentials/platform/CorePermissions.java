package com.mrleonardos.codeessentials.platform;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.command.ICommandSender;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.engine.PlayerRights;
import com.mrleonardos.codeessentials.internal.service.PlayerMeta;

final class CorePermissions implements PlayerRights, PlayerMeta {

    private final Supplier<EssentialsSettings> settings;
    private final Supplier<PermissionService> lookup;
    private final Logger log;
    private boolean missingTold;

    CorePermissions(Supplier<EssentialsSettings> settings, Logger log) {
        this(settings, CorePermissions::fromRegistry, log);
    }

    CorePermissions(Supplier<EssentialsSettings> settings, Supplier<PermissionService> lookup, Logger log) {
        this.settings = settings;
        this.lookup = lookup;
        this.log = log;
    }

    private static PermissionService fromRegistry() {
        return CodeApi.services()
            .find(PermissionService.class)
            .orElse(null);
    }

    @Override
    public boolean has(UUID player, String node) {
        PermissionService service = service();
        return explained(player, node, service != null && service.has(player, node));
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
        return explained(sender.getCommandSenderName(), node, service != null && service.has(sender, node));
    }

    private boolean explained(Object subject, String node, boolean allowed) {
        if (!allowed && settings.get()
            .logChecks()) {
            log.debug("{} has no {}", subject, node);
        }
        return allowed;
    }

    private PermissionService service() {
        PermissionService held = lookup.get();
        if (held == null && !missingTold) {
            missingTold = true;
            log.error("No mod holds PermissionService, every check of CodeEssentials answers no");
        }
        return held;
    }
}
