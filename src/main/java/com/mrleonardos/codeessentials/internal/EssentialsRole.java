package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeessentials.api.EssentialsCapabilities;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

public final class EssentialsRole {

    private EssentialsRole() {}

    public static RoleSpec spec() {
        return RoleSpec.of(ConfigRoles.ESSENTIALS)
            .capabilities(
                EssentialsCapabilities.HOMES,
                EssentialsCapabilities.WARPS,
                EssentialsCapabilities.SPAWN,
                EssentialsCapabilities.BACK,
                EssentialsCapabilities.TPA,
                EssentialsCapabilities.RANDOM)
            .services(
                TeleportService.class,
                HomeService.class,
                WarpService.class,
                SpawnService.class,
                BackService.class)
            .build();
    }
}
