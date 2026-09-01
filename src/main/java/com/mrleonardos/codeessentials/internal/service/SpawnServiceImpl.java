package com.mrleonardos.codeessentials.internal.service;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeessentials.api.manage.SpawnService;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.SharedSettings;

public final class SpawnServiceImpl implements SpawnService {

    private final Supplier<SharedSettings> shared;
    private final ConfigFile<SpawnFile> file;
    private final Logger log;

    public SpawnServiceImpl(Supplier<SharedSettings> shared, ConfigFile<SpawnFile> file, Logger log) {
        this.shared = shared;
        this.file = file;
        this.log = log;
    }

    @Override
    public SpawnTable table() {
        if (!file.loaded()) {
            return SpawnTable.empty();
        }
        SpawnFile stored = file.get();
        Map<Integer, Point> byDimension = new TreeMap<>();
        for (Map.Entry<String, String> entry : stored.dimensions.entrySet()) {
            Integer dimension = dimensionOf(entry.getKey());
            Point point = Point.parse(entry.getValue())
                .orElse(null);
            if (dimension == null || point == null) {
                log.warn("Spawn of dimension {} is unreadable and was skipped: {}", entry.getKey(), entry.getValue());
                continue;
            }
            byDimension.put(dimension, point);
        }
        Point global = Point.parse(stored.global)
            .orElse(null);
        if (global == null && stored.global != null) {
            log.warn("Global spawn is unreadable and was skipped: {}", stored.global);
        }
        return SpawnTable.of(global, byDimension);
    }

    @Override
    public Optional<Point> spawnFor(int dimension) {
        return table().pointFor(dimension);
    }

    @Override
    public StoreResult setGlobalSpawn(Point point, String actor) {
        if (point == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "point and actor are required");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, SpawnFile.FILE);
        }
        SpawnFile stored = file.get();
        String previous = stored.global;
        stored.global = point.print();
        StoreResult written = write();
        if (!written.successful()) {
            stored.global = previous;
            return written;
        }
        if (shared.get()
            .logChanges()) {
            log.info("{} set the global spawn at {}", actor, point.print());
        }
        return written;
    }

    @Override
    public StoreResult setDimensionSpawn(Point point, String actor) {
        if (point == null || actor == null) {
            return StoreResult.failure(StoreResult.Failure.INVALID_VALUE, "point and actor are required");
        }
        if (!file.loaded()) {
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, SpawnFile.FILE);
        }
        SpawnFile stored = file.get();
        String key = String.valueOf(point.dimension());
        String previous = stored.dimensions.get(key);
        stored.dimensions.put(key, point.print());
        StoreResult written = write();
        if (!written.successful()) {
            if (previous == null) {
                stored.dimensions.remove(key);
            } else {
                stored.dimensions.put(key, previous);
            }
            return written;
        }
        if (shared.get()
            .logChanges()) {
            log.info("{} set the spawn of dimension {} at {}", actor, key, point.print());
        }
        return written;
    }

    private StoreResult write() {
        try {
            file.save();
        } catch (RuntimeException broken) {
            log.warn("{} was not written, nothing changed", SpawnFile.FILE_NAME, broken);
            return StoreResult.failure(StoreResult.Failure.PROVIDER_FAILED, String.valueOf(broken.getMessage()));
        }
        return StoreResult.success();
    }

    private static Integer dimensionOf(String key) {
        try {
            return Integer.valueOf(key.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
