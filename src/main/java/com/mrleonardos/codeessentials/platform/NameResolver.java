package com.mrleonardos.codeessentials.platform;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.util.PlayerNames;
import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.internal.store.EssentialsState;

final class NameResolver {

    private final Supplier<EssentialsState> states;

    NameResolver(Supplier<EssentialsState> states) {
        this.states = states;
    }

    Optional<String> name(UUID player) {
        String known = PlayerNames.byId(player);
        if (known != null && !known.isEmpty()) {
            return Optional.of(known);
        }
        PlayerRecord held = states.get()
            .players()
            .get(player);
        return held != null && held.name() != null ? Optional.of(held.name()) : Optional.<String>empty();
    }

    Optional<UUID> id(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        UUID known = PlayerNames.idByName(name);
        if (known != null) {
            return Optional.of(known);
        }
        for (PlayerRecord held : states.get()
            .players()
            .values()) {
            if (held.name() != null && held.name()
                .equalsIgnoreCase(name)) {
                return Optional.of(held.uuid());
            }
        }
        return Optional.empty();
    }

    List<String> suggest(String partial, int limit) {
        List<String> found = new ArrayList<>();
        if (limit <= 0) {
            return found;
        }
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>(Players.onlineNames());
        for (PlayerRecord held : states.get()
            .players()
            .values()) {
            if (held.name() != null) {
                candidates.add(held.name());
            }
        }
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == limit) {
                    break;
                }
            }
        }
        return found;
    }
}
