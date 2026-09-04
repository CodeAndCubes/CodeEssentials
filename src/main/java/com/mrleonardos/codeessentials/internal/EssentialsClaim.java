package com.mrleonardos.codeessentials.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeessentials.api.EssentialsCapabilities;

public final class EssentialsClaim implements RoleAdapter {

    public static final String NAME = EssentialsSettings.MODID;

    private static final Set<RoleCapability> CAPABILITIES = Collections.unmodifiableSet(
        new LinkedHashSet<>(
            Arrays.asList(
                EssentialsCapabilities.HOMES,
                EssentialsCapabilities.WARPS,
                EssentialsCapabilities.SPAWN,
                EssentialsCapabilities.BACK,
                EssentialsCapabilities.TPA,
                EssentialsCapabilities.RANDOM,
                EssentialsCapabilities.KITS)));

    private final Supplier<RoleServices> build;

    public EssentialsClaim(Supplier<RoleServices> build) {
        this.build = build;
    }

    @Override
    public String role() {
        return ConfigRoles.ESSENTIALS;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RoleOwnerKind kind() {
        return RoleOwnerKind.MOD;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Set<RoleCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public RoleServices create() {
        return build.get();
    }
}
