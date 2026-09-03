package com.mrleonardos.codeessentials.internal.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class RandomRules {

    public static final int DEFAULT_MIN_RADIUS = 500;
    public static final int DEFAULT_MAX_RADIUS = 5000;
    public static final int DEFAULT_ATTEMPTS = 8;
    public static final int DEFAULT_WORLD = 0;

    private static final RandomRules DEFAULTS = builder().build();

    private final boolean enabled;
    private final int minRadius;
    private final int maxRadius;
    private final boolean centerAtSpawn;
    private final int centerX;
    private final int centerZ;
    private final Set<Integer> worlds;
    private final Set<String> blockedBiomes;
    private final int attempts;
    private final boolean fallbackToSpawn;

    private RandomRules(Builder builder) {
        this.enabled = builder.enabled;
        this.minRadius = Math.max(0, builder.minRadius);
        this.maxRadius = Math.max(this.minRadius, builder.maxRadius);
        this.centerAtSpawn = builder.centerAtSpawn;
        this.centerX = builder.centerX;
        this.centerZ = builder.centerZ;
        this.worlds = Collections.unmodifiableSet(new LinkedHashSet<>(builder.worlds));
        this.blockedBiomes = Collections.unmodifiableSet(new TreeSet<>(builder.blockedBiomes));
        this.attempts = Math.max(1, builder.attempts);
        this.fallbackToSpawn = builder.fallbackToSpawn;
    }

    public static RandomRules defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String normalize(String biome) {
        if (biome == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (int index = 0; index < biome.length(); index++) {
            char symbol = biome.charAt(index);
            if (symbol != ' ' && symbol != '_' && symbol != '-') {
                cleaned.append(symbol);
            }
        }
        return cleaned.toString()
            .toLowerCase(Locale.ROOT);
    }

    public boolean enabled() {
        return enabled;
    }

    public int minRadius() {
        return minRadius;
    }

    public int maxRadius() {
        return maxRadius;
    }

    public boolean centerAtSpawn() {
        return centerAtSpawn;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public Set<Integer> worlds() {
        return worlds;
    }

    public Set<String> blockedBiomes() {
        return blockedBiomes;
    }

    public int attempts() {
        return attempts;
    }

    public boolean fallbackToSpawn() {
        return fallbackToSpawn;
    }

    public boolean allows(int dimension) {
        return worlds.isEmpty() || worlds.contains(Integer.valueOf(dimension));
    }

    public boolean blocked(String biome) {
        String name = normalize(biome);
        return !name.isEmpty() && blockedBiomes.contains(name);
    }

    @Override
    public String toString() {
        return (enabled ? "on" : "off") + " ring "
            + minRadius
            + ".."
            + maxRadius
            + " around "
            + (centerAtSpawn ? "spawn" : centerX + "," + centerZ)
            + ", "
            + attempts
            + " tries";
    }

    public static final class Builder {

        private final Set<Integer> worlds = new LinkedHashSet<>();
        private final Set<String> blockedBiomes = new TreeSet<>();

        private boolean enabled = true;
        private int minRadius = DEFAULT_MIN_RADIUS;
        private int maxRadius = DEFAULT_MAX_RADIUS;
        private boolean centerAtSpawn = true;
        private int centerX;
        private int centerZ;
        private int attempts = DEFAULT_ATTEMPTS;
        private boolean fallbackToSpawn;

        private Builder() {
            worlds.add(Integer.valueOf(DEFAULT_WORLD));
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder radius(int min, int max) {
            minRadius = min;
            maxRadius = max;
            return this;
        }

        public Builder center(boolean atSpawn, int x, int z) {
            centerAtSpawn = atSpawn;
            centerX = x;
            centerZ = z;
            return this;
        }

        public Builder worlds(Collection<Integer> values) {
            worlds.clear();
            if (values == null) {
                return this;
            }
            for (Integer dimension : values) {
                if (dimension != null) {
                    worlds.add(dimension);
                }
            }
            return this;
        }

        public Builder blockedBiomes(Collection<String> values) {
            blockedBiomes.clear();
            if (values == null) {
                return this;
            }
            for (String biome : values) {
                String name = normalize(biome);
                if (!name.isEmpty()) {
                    blockedBiomes.add(name);
                }
            }
            return this;
        }

        public Builder attempts(int value) {
            attempts = value;
            return this;
        }

        public Builder fallbackToSpawn(boolean value) {
            fallbackToSpawn = value;
            return this;
        }

        public RandomRules build() {
            return new RandomRules(this);
        }
    }
}
