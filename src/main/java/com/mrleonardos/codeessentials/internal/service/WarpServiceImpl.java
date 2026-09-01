package com.mrleonardos.codeessentials.internal.service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeessentials.api.manage.WarpService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;
import com.mrleonardos.codeessentials.internal.SharedSettings;

public final class WarpServiceImpl implements WarpService {

    private final Supplier<EssentialsSettings> settings;
    private final Supplier<SharedSettings> shared;
    private final ConfigFile<WarpsFile> file;
    private final SpotCheck spots;
    private final Logger log;

    public WarpServiceImpl(Supplier<EssentialsSettings> settings, Supplier<SharedSettings> shared,
        ConfigFile<WarpsFile> file, SpotCheck spots, Logger log) {
        this.settings = settings;
        this.shared = shared;
        this.file = file;
        this.spots = spots;
        this.log = log;
    }

    @Override
    public Optional<WarpRecord> warp(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(warps().get(lower(name)));
    }

    @Override
    public Map<String, WarpRecord> warps() {
        if (!file.loaded()) {
            return Collections.emptyMap();
        }
        Map<String, WarpRecord> decoded = new TreeMap<>();
        for (Map.Entry<String, WarpsFile.Warp> entry : file.get().warps.entrySet()) {
            WarpRecord record = decode(entry.getKey(), entry.getValue());
            if (record != null) {
                decoded.put(record.name(), record);
            }
        }
        return Collections.unmodifiableMap(decoded);
    }

    @Override
    public StoreResult setWarp(WarpRecord warp, boolean safeSpot, String actor) {
        if (warp == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "warp and actor are required");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, WarpsFile.FILE);
        }
        Map<String, WarpRecord> held = warps();
        boolean overwrite = held.containsKey(warp.name());
        if (!overwrite && held.size() >= settings.get()
            .ceilings()
            .warps()) {
            return StoreResult.failure(
                StoreResult.Failure.LIMIT_REACHED,
                String.valueOf(
                    settings.get()
                        .ceilings()
                        .warps()));
        }
        if (safeSpot) {
            SafeSpotResult checked = spots.check(warp.point());
            if (!checked.found()) {
                return StoreResult.failure(
                    StoreResult.Failure.UNSAFE_SPOT,
                    checked.outcome()
                        .name());
            }
        }
        WarpsFile stored = file.get();
        WarpsFile.Warp previous = stored.warps.get(warp.name());
        stored.warps.put(
            warp.name(),
            new WarpsFile.Warp(
                warp.point()
                    .print(),
                warp.description()));
        StoreResult written = write();
        if (!written.successful()) {
            restore(stored, warp.name(), previous);
            return written;
        }
        if (shared.get()
            .logChanges()) {
            log.info(
                "{} {} warp {} at {}",
                actor,
                overwrite ? "moved" : "created",
                warp.name(),
                warp.point()
                    .print());
        }
        return written;
    }

    @Override
    public StoreResult deleteWarp(String name, String actor) {
        if (name == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "name and actor are required");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, WarpsFile.FILE);
        }
        String key = lower(name);
        WarpsFile held = file.get();
        WarpsFile.Warp previous = held.warps.get(key);
        if (previous == null) {
            return StoreResult.failure(StoreResult.Failure.NOT_FOUND, name);
        }
        held.warps.remove(key);
        StoreResult written = write();
        if (!written.successful()) {
            restore(held, key, previous);
            return written;
        }
        if (shared.get()
            .logChanges()) {
            log.info("{} deleted warp {}", actor, key);
        }
        return written;
    }

    private StoreResult write() {
        try {
            file.save();
        } catch (RuntimeException broken) {
            log.warn("{} was not written, nothing changed", WarpsFile.FILE_NAME, broken);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, String.valueOf(broken.getMessage()));
        }
        return StoreResult.success();
    }

    private static void restore(WarpsFile file, String name, WarpsFile.Warp previous) {
        if (previous == null) {
            file.warps.remove(name);
            return;
        }
        file.warps.put(name, previous);
    }

    private WarpRecord decode(String name, WarpsFile.Warp warp) {
        if (name == null || warp == null) {
            return null;
        }
        String key = lower(name);
        Point point = Point.parse(warp.location)
            .orElse(null);
        if (point == null) {
            log.warn("Warp {} carries an unreadable location and was skipped: {}", key, warp.location);
            return null;
        }
        try {
            return WarpRecord.of(key, point, warp.description);
        } catch (IllegalArgumentException invalid) {
            log.warn("Warp name {} does not match the pattern and was skipped", name);
            return null;
        }
    }

    private static String lower(String name) {
        return name.trim()
            .toLowerCase(Locale.ROOT);
    }
}
