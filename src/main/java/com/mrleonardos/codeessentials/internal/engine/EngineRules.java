package com.mrleonardos.codeessentials.internal.engine;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.mrleonardos.codeessentials.api.EssentialsLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;

public final class EngineRules {

    public static final int DEFAULT_WARMUP_SECONDS = 3;
    public static final double DEFAULT_MOVE_RADIUS = 2.0D;
    public static final double DEFAULT_VERTICAL_MOVE_RADIUS = 1.0D;
    public static final boolean DEFAULT_CANCEL_ON_DAMAGE = true;
    public static final int DEFAULT_MOVE_DEADLINE_TICKS = 40;
    public static final int DEFAULT_REQUEST_RATE_SECONDS = 10;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_MAX_PENDING = 8;
    public static final int DEFAULT_SWEEP_TICKS = 20;
    public static final int TICKS_PER_SECOND = 20;

    private static final EngineRules DEFAULTS = builder().build();

    private final int warmupSeconds;
    private final double moveRadius;
    private final double verticalMoveRadius;
    private final boolean cancelOnDamage;
    private final int moveDeadlineTicks;
    private final Map<String, Integer> cooldownSeconds;
    private final int requestRateSeconds;
    private final int requestTimeoutSeconds;
    private final int maxPending;
    private final int sweepTicks;
    private final SafeSpotLimits safeSpot;
    private final EssentialsLimits limits;

    private EngineRules(Builder builder) {
        this.limits = builder.limits;
        this.warmupSeconds = builder.limits.clampWarmupSeconds(builder.warmupSeconds);
        this.moveRadius = Math.max(0.0D, builder.moveRadius);
        this.verticalMoveRadius = Math.max(0.0D, builder.verticalMoveRadius);
        this.cancelOnDamage = builder.cancelOnDamage;
        this.moveDeadlineTicks = Math.max(1, builder.moveDeadlineTicks);
        this.cooldownSeconds = Collections.unmodifiableMap(new TreeMap<>(builder.cooldownSeconds));
        this.requestRateSeconds = Math.max(0, builder.requestRateSeconds);
        this.requestTimeoutSeconds = builder.limits.clampRequestTimeoutSeconds(builder.requestTimeoutSeconds);
        this.maxPending = builder.limits.clampPendingRequests(builder.maxPending);
        this.sweepTicks = Math.max(1, builder.sweepTicks);
        this.safeSpot = builder.safeSpot;
    }

    public static EngineRules defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int warmupSeconds() {
        return warmupSeconds;
    }

    public double moveRadius() {
        return moveRadius;
    }

    public double verticalMoveRadius() {
        return verticalMoveRadius;
    }

    public boolean cancelOnDamage() {
        return cancelOnDamage;
    }

    public int moveDeadlineTicks() {
        return moveDeadlineTicks;
    }

    public int requestRateSeconds() {
        return requestRateSeconds;
    }

    public int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int maxPending() {
        return maxPending;
    }

    public int sweepTicks() {
        return sweepTicks;
    }

    public SafeSpotLimits safeSpot() {
        return safeSpot;
    }

    public EssentialsLimits limits() {
        return limits;
    }

    public int cooldownSeconds(TeleportCause cause) {
        Integer seconds = cooldownSeconds.get(cause.key());
        return seconds == null ? 0 : Math.max(0, seconds.intValue());
    }

    public static int ticks(int seconds) {
        return Math.max(0, seconds) * TICKS_PER_SECOND;
    }

    public static long millis(int seconds) {
        return Math.max(0, (long) seconds) * 1000L;
    }

    @Override
    public String toString() {
        return "warmup " + warmupSeconds
            + "s, radius "
            + moveRadius
            + ", requests "
            + maxPending
            + "x"
            + requestTimeoutSeconds
            + "s";
    }

    public static final class Builder {

        private final Map<String, Integer> cooldownSeconds = new TreeMap<>();

        private int warmupSeconds = DEFAULT_WARMUP_SECONDS;
        private double moveRadius = DEFAULT_MOVE_RADIUS;
        private double verticalMoveRadius = DEFAULT_VERTICAL_MOVE_RADIUS;
        private boolean cancelOnDamage = DEFAULT_CANCEL_ON_DAMAGE;
        private int moveDeadlineTicks = DEFAULT_MOVE_DEADLINE_TICKS;
        private int requestRateSeconds = DEFAULT_REQUEST_RATE_SECONDS;
        private int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;
        private int maxPending = DEFAULT_MAX_PENDING;
        private int sweepTicks = DEFAULT_SWEEP_TICKS;
        private SafeSpotLimits safeSpot = SafeSpotLimits.defaults();
        private EssentialsLimits limits = EssentialsLimits.defaults();

        private Builder() {}

        public Builder warmupSeconds(int value) {
            warmupSeconds = value;
            return this;
        }

        public Builder moveRadius(double value) {
            moveRadius = value;
            return this;
        }

        public Builder verticalMoveRadius(double value) {
            verticalMoveRadius = value;
            return this;
        }

        public Builder cancelOnDamage(boolean value) {
            cancelOnDamage = value;
            return this;
        }

        public Builder moveDeadlineTicks(int value) {
            moveDeadlineTicks = value;
            return this;
        }

        public Builder cooldown(String cause, int seconds) {
            Objects.requireNonNull(cause, "cause");
            cooldownSeconds.put(cause.toLowerCase(Locale.ROOT), Integer.valueOf(seconds));
            return this;
        }

        public Builder cooldown(TeleportCause cause, int seconds) {
            return cooldown(cause.key(), seconds);
        }

        public Builder requestRateSeconds(int value) {
            requestRateSeconds = value;
            return this;
        }

        public Builder requestTimeoutSeconds(int value) {
            requestTimeoutSeconds = value;
            return this;
        }

        public Builder maxPending(int value) {
            maxPending = value;
            return this;
        }

        public Builder sweepTicks(int value) {
            sweepTicks = value;
            return this;
        }

        public Builder safeSpot(SafeSpotLimits value) {
            safeSpot = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder limits(EssentialsLimits value) {
            limits = Objects.requireNonNull(value, "value");
            return this;
        }

        public EngineRules build() {
            return new EngineRules(this);
        }
    }
}
