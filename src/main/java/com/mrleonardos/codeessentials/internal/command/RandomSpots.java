package com.mrleonardos.codeessentials.internal.command;

import java.util.Objects;
import java.util.Optional;

import com.mrleonardos.codeessentials.api.model.Point;

public interface RandomSpots {

    enum Outcome {

        FOUND,
        SPAWN,
        OFF,
        WRONG_WORLD,
        NO_WORLD,
        NOT_FOUND
    }

    final class Reply {

        private final Outcome outcome;
        private final Point spot;
        private final int attempts;

        private Reply(Outcome outcome, Point spot, int attempts) {
            this.outcome = outcome;
            this.spot = spot;
            this.attempts = attempts;
        }

        public static Reply found(Point spot, int attempts) {
            return new Reply(Outcome.FOUND, Objects.requireNonNull(spot, "spot"), attempts);
        }

        public static Reply spawn(Point spot, int attempts) {
            return new Reply(Outcome.SPAWN, Objects.requireNonNull(spot, "spot"), attempts);
        }

        public static Reply refused(Outcome outcome, int attempts) {
            return new Reply(Objects.requireNonNull(outcome, "outcome"), null, attempts);
        }

        public Outcome outcome() {
            return outcome;
        }

        public Optional<Point> spot() {
            return Optional.ofNullable(spot);
        }

        public int attempts() {
            return attempts;
        }

        @Override
        public String toString() {
            return outcome + (spot == null ? "" : " " + spot.print()) + " after " + attempts + " try(s)";
        }
    }

    Reply find(Point origin);
}
