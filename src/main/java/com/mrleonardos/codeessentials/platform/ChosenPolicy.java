package com.mrleonardos.codeessentials.platform;

import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codeessentials.api.EssentialsApi;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

final class ChosenPolicy implements SafeSpotPolicy {

    private final Supplier<String> wanted;
    private final SafeSpotPolicy builtin;
    private final Logger log;

    private volatile SafeSpotPolicy named;
    private boolean unknownTold;

    ChosenPolicy(Supplier<String> wanted, SafeSpotPolicy builtin, Logger log) {
        this.wanted = wanted;
        this.builtin = builtin;
        this.log = log;
    }

    @Override
    public String id() {
        return active().id();
    }

    @Override
    public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
        return active().find(view, hint, limits);
    }

    private SafeSpotPolicy active() {
        SafeSpotPolicy held = CodeApi.services()
            .find(SafeSpotPolicy.class)
            .orElse(null);
        return held == null || held == this ? named() : held;
    }

    private SafeSpotPolicy named() {
        SafeSpotPolicy known = named;
        if (known != null) {
            return known;
        }
        String id = wanted.get();
        SafeSpotPolicy found = EssentialsApi.policy(id)
            .orElse(null);
        if (found == null) {
            if (!unknownTold) {
                unknownTold = true;
                log.warn("Safe spot policy {} is not registered, the built in one is used", id);
            }
            found = builtin;
        }
        named = found;
        return found;
    }
}
