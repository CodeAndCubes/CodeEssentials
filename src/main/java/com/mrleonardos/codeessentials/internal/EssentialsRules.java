package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;

public final class EssentialsRules {

    private EssentialsRules() {}

    public static EngineRules of(EssentialsSettings settings, SharedSettings shared, EssentialsLimits ceilings) {
        EngineRules.Builder builder = EngineRules.builder()
            .limits(ceilings)
            .warmupSeconds(shared.warmupSeconds(ceilings))
            .moveRadius(settings.warmupMoveRadius())
            .verticalMoveRadius(settings.warmupMoveHeight())
            .cancelOnDamage(settings.warmupCancelOnDamage())
            .requestRateSeconds(settings.requestRateSeconds())
            .requestTimeoutSeconds(settings.requestTimeoutSeconds(ceilings))
            .maxPending(settings.maxPending(ceilings))
            .safeSpot(settings.spotLimits(ceilings));
        for (TeleportCause cause : TeleportCause.values()) {
            builder.cooldown(cause, shared.cooldownSeconds(cause));
        }
        return builder.build();
    }
}
