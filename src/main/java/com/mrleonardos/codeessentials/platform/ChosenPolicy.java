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
import com.mrleonardos.codeessentials.internal.engine.PolicyChoice;

final class ChosenPolicy implements SafeSpotPolicy {

    private final PolicyChoice choice;

    ChosenPolicy(Supplier<String> wanted, Logger log) {
        this.choice = new PolicyChoice(wanted, EssentialsApi::policy, log);
    }

    @Override
    public String id() {
        return active().id();
    }

    @Override
    public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
        return active().find(view, hint, limits);
    }

    void reset() {
        choice.reset();
    }

    private SafeSpotPolicy active() {
        SafeSpotPolicy held = CodeApi.services()
            .find(SafeSpotPolicy.class)
            .orElse(null);
        return held == null || held == this ? choice.named() : held;
    }
}
