package com.mrleonardos.codeessentials.internal.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.manage.HomeService;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.PlayerRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.ChangeBatch;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

public final class HomeServiceImpl implements HomeService {

    private final Supplier<EssentialsSettings> settings;
    private final PlayerMeta meta;
    private final PlayerStateWriter state;
    private final Supplier<HomeEvents> events;
    private final Scheduler scheduler;
    private final Logger log;

    private final Set<UUID> touched = new LinkedHashSet<>();
    private boolean flushScheduled;

    public HomeServiceImpl(Supplier<EssentialsSettings> settings, PlayerMeta meta, PlayerStateWriter state,
        Supplier<HomeEvents> events, Scheduler scheduler, Logger log) {
        this.settings = settings;
        this.meta = meta;
        this.state = state;
        this.events = events;
        this.scheduler = scheduler;
        this.log = log;
    }

    @Override
    public Optional<HomeRecord> home(UUID player, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return record(player).home(lower(name));
    }

    @Override
    public Map<String, HomeRecord> homes(UUID player) {
        return record(player).homes();
    }

    @Override
    public int homeLimit(UUID player) {
        EssentialsSettings current = settings.get();
        EssentialsLimits ceilings = current.ceilings();
        int fallback = current.defaultHomes(ceilings);
        String raw = meta.value(player, EssentialsSettings.META_MAX_HOMES, null);
        if (raw == null || raw.trim()
            .isEmpty()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            log.warn("Meta {} of {} is not a number: {}", EssentialsSettings.META_MAX_HOMES, player, raw);
            return fallback;
        }
        if (parsed < 0 || parsed > ceilings.homesPerPlayer()) {
            log.warn(
                "Meta {} of {} is out of 0..{}: {}",
                EssentialsSettings.META_MAX_HOMES,
                player,
                Integer.valueOf(ceilings.homesPerPlayer()),
                raw);
            return fallback;
        }
        return parsed;
    }

    @Override
    public StoreResult setHome(UUID player, String name, Point point, String actor) {
        if (player == null || point == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "player, point and actor are required");
        }
        String key = lower(name);
        EssentialsSettings current = settings.get();
        if (!current.ceilings()
            .acceptsName(key)) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, String.valueOf(name));
        }
        PlayerRecord held = record(player);
        boolean overwrite = held.homes()
            .containsKey(key);
        int limit = homeLimit(player);
        if (!overwrite && held.homes()
            .size() >= limit) {
            return StoreResult.failure(StoreResult.Failure.LIMIT_REACHED, String.valueOf(limit));
        }
        HomeRecord home = HomeRecord.of(key, point, System.currentTimeMillis());
        String veto = vetoOf(player, home, overwrite);
        if (veto != null) {
            return StoreResult.failure(StoreResult.Failure.VETOED, veto);
        }
        PlayerRecord next = held.withHome(home);
        StoreResult written = state.commit(
            next,
            ChangeBatch.builder(actor)
                .upsert(next)
                .build());
        if (!written.successful()) {
            return written;
        }
        announceSet(player, home, overwrite);
        if (current.logChanges()) {
            log.info("{} {} home {} of {} at {}", actor, overwrite ? "moved" : "set", key, player, point.print());
        }
        return written;
    }

    @Override
    public StoreResult deleteHome(UUID player, String name, String actor) {
        if (player == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "player and actor are required");
        }
        String key = lower(name);
        PlayerRecord held = record(player);
        if (!held.homes()
            .containsKey(key)) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, String.valueOf(name));
        }
        PlayerRecord next = held.withoutHome(key);
        StoreResult written = state.commit(
            next,
            ChangeBatch.builder(actor)
                .upsert(next)
                .build());
        if (!written.successful()) {
            return written;
        }
        touch(player);
        if (settings.get()
            .logChanges()) {
            log.info("{} deleted home {} of {}", actor, key, player);
        }
        return written;
    }

    private PlayerRecord record(UUID player) {
        return state.player(player)
            .orElseGet(() -> PlayerRecord.empty(player, null));
    }

    private String vetoOf(UUID player, HomeRecord home, boolean overwrite) {
        HomeEvents registry = events.get();
        if (registry == null) {
            return null;
        }
        for (HomeEvents.Listener listener : registry.listeners()) {
            EssentialsEvents.Decision decision;
            try {
                decision = listener.beforeSet(player, home, overwrite);
            } catch (RuntimeException broken) {
                if (listener.kind() == EssentialsEvents.Kind.ENFORCE) {
                    log.warn("Enforcing home listener {} failed, the home is refused", listener, broken);
                    return listener.getClass()
                        .getName();
                }
                log.warn("Home listener {} failed and was skipped", listener, broken);
                continue;
            }
            if (decision != null && !decision.allowed()) {
                return decision.reason()
                    .orElse(
                        listener.getClass()
                            .getName());
            }
        }
        return null;
    }

    private void announceSet(UUID player, HomeRecord home, boolean overwrite) {
        HomeEvents registry = events.get();
        if (registry != null) {
            for (HomeEvents.Listener listener : registry.listeners()) {
                try {
                    listener.afterSet(player, home, overwrite);
                } catch (RuntimeException broken) {
                    log.warn("Home listener {} failed after the write", listener, broken);
                }
            }
        }
        touch(player);
    }

    private void touch(UUID player) {
        touched.add(player);
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        scheduler.afterTicks(1, this::flushTouched);
    }

    private void flushTouched() {
        flushScheduled = false;
        if (touched.isEmpty()) {
            return;
        }
        Set<UUID> batch = Collections.unmodifiableSet(new LinkedHashSet<>(touched));
        touched.clear();
        HomeEvents registry = events.get();
        if (registry == null) {
            return;
        }
        for (HomeEvents.Listener listener : registry.listeners()) {
            try {
                listener.changed(batch);
            } catch (RuntimeException broken) {
                log.warn("Home listener {} failed on the change notice", listener, broken);
            }
        }
    }

    private static String lower(String name) {
        return name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT);
    }
}
