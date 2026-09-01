package com.mrleonardos.codeessentials.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.event.TeleportEvents;

public final class Listeners implements EssentialsEvents {

    private final Teleports teleports = new Teleports();
    private final Homes homes = new Homes();

    @Override
    public TeleportEvents teleports() {
        return teleports;
    }

    @Override
    public HomeEvents homes() {
        return homes;
    }

    public void clear() {
        teleports.ranked.clear();
        homes.ranked.clear();
    }

    public static final class Teleports implements TeleportEvents {

        private final Ranked<Listener> ranked = new Ranked<>();

        @Override
        public void register(int priority, Listener listener) {
            ranked.add(priority, listener);
        }

        @Override
        public void unregister(Listener listener) {
            ranked.drop(listener);
        }

        @Override
        public List<Listener> listeners() {
            return ranked.all();
        }
    }

    public static final class Homes implements HomeEvents {

        private final Ranked<Listener> ranked = new Ranked<>();

        @Override
        public void register(int priority, Listener listener) {
            ranked.add(priority, listener);
        }

        @Override
        public void unregister(Listener listener) {
            ranked.drop(listener);
        }

        @Override
        public List<Listener> listeners() {
            return ranked.all();
        }
    }

    private static final class Ranked<L> {

        private final List<Seat<L>> seats = new ArrayList<>();

        private long taken;
        private volatile List<L> shown = Collections.emptyList();

        synchronized void add(int priority, L listener) {
            Objects.requireNonNull(listener, "listener");
            remove(listener);
            seats.add(new Seat<>(priority, ++taken, listener));
            seats.sort(Ranked::earlier);
            publish();
        }

        synchronized void drop(L listener) {
            if (listener != null && remove(listener)) {
                publish();
            }
        }

        synchronized void clear() {
            seats.clear();
            publish();
        }

        List<L> all() {
            return shown;
        }

        private boolean remove(L listener) {
            for (Iterator<Seat<L>> held = seats.iterator(); held.hasNext();) {
                if (held.next().listener == listener) {
                    held.remove();
                    return true;
                }
            }
            return false;
        }

        private void publish() {
            List<L> next = new ArrayList<>(seats.size());
            for (Seat<L> seat : seats) {
                next.add(seat.listener);
            }
            shown = Collections.unmodifiableList(next);
        }

        private static int earlier(Seat<?> left, Seat<?> right) {
            if (left.priority != right.priority) {
                return Integer.compare(left.priority, right.priority);
            }
            return Long.compare(left.seat, right.seat);
        }
    }

    private static final class Seat<L> {

        private final int priority;
        private final long seat;
        private final L listener;

        Seat(int priority, long seat, L listener) {
            this.priority = priority;
            this.seat = seat;
            this.listener = listener;
        }
    }
}
