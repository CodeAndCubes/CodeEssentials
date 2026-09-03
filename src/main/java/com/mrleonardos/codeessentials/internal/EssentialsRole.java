package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeessentials.api.manage.BackService;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.teleport.TeleportService;

public final class EssentialsRole {

    public static final RoleCapability HOMES = RoleCapability.of("homes");
    public static final RoleCapability WARPS = RoleCapability.of("warps");
    public static final RoleCapability SPAWN = RoleCapability.of("spawn");
    public static final RoleCapability BACK = RoleCapability.of("back");
    public static final RoleCapability TPA = RoleCapability.of("tpa");
    public static final RoleCapability RANDOM = RoleCapability.of("rtp");

    private EssentialsRole() {}

    public static RoleSpec spec() {
        return RoleSpec.of(ConfigRoles.ESSENTIALS)
            .capabilities(HOMES, WARPS, SPAWN, BACK, TPA, RANDOM)
            .services(
                TeleportService.class,
                HomeService.class,
                WarpService.class,
                SpawnService.class,
                BackService.class)
            .build();
    }
}
