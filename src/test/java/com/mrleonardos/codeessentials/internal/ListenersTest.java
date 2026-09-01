package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.event.EssentialsEvents;
import com.mrleonardos.codeessentials.api.event.HomeEvents;
import com.mrleonardos.codeessentials.api.event.TeleportEvents;
import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;

class ListenersTest {

    @Test
    void smallerPriorityComesFirst() {
        Listeners listeners = new Listeners();
        List<String> order = new ArrayList<>();
        listeners.teleports()
            .register(10, new Watcher("late", order));
        listeners.teleports()
            .register(-5, new Watcher("early", order));
        listeners.teleports()
            .register(0, new Watcher("middle", order));

        for (TeleportEvents.Listener listener : listeners.teleports()
            .listeners()) {
            listener.afterMove(null);
        }

        assertEquals(java.util.Arrays.asList("early", "middle", "late"), order);
    }

    @Test
    void equalPrioritiesKeepTheOrderOfRegistration() {
        Listeners listeners = new Listeners();
        List<String> order = new ArrayList<>();
        listeners.teleports()
            .register(7, new Watcher("first", order));
        listeners.teleports()
            .register(7, new Watcher("second", order));
        listeners.teleports()
            .register(7, new Watcher("third", order));

        for (TeleportEvents.Listener listener : listeners.teleports()
            .listeners()) {
            listener.afterMove(null);
        }

        assertEquals(java.util.Arrays.asList("first", "second", "third"), order);
    }

    @Test
    void registeringTwiceKeepsOneSeatWithTheNewPriority() {
        Listeners listeners = new Listeners();
        List<String> order = new ArrayList<>();
        Watcher moved = new Watcher("moved", order);
        listeners.teleports()
            .register(100, moved);
        listeners.teleports()
            .register(1, new Watcher("other", order));
        listeners.teleports()
            .register(-1, moved);

        assertEquals(
            2,
            listeners.teleports()
                .listeners()
                .size());
        for (TeleportEvents.Listener listener : listeners.teleports()
            .listeners()) {
            listener.afterMove(null);
        }
        assertEquals(java.util.Arrays.asList("moved", "other"), order);
    }

    @Test
    void unregisterDropsOnlyTheAskedListener() {
        Listeners listeners = new Listeners();
        List<String> order = new ArrayList<>();
        Watcher dropped = new Watcher("dropped", order);
        listeners.teleports()
            .register(0, dropped);
        listeners.teleports()
            .register(0, new Watcher("kept", order));

        listeners.teleports()
            .unregister(dropped);

        assertEquals(
            1,
            listeners.teleports()
                .listeners()
                .size());
    }

    @Test
    void teleportAndHomeRegistriesAreSeparate() {
        Listeners listeners = new Listeners();
        listeners.teleports()
            .register(0, new Watcher("only", new ArrayList<>()));

        assertEquals(
            1,
            listeners.teleports()
                .listeners()
                .size());
        assertTrue(
            listeners.homes()
                .listeners()
                .isEmpty());
    }

    @Test
    void theShownListIsNotEditableFromOutside() {
        Listeners listeners = new Listeners();
        listeners.teleports()
            .register(0, new Watcher("only", new ArrayList<>()));
        List<TeleportEvents.Listener> shown = listeners.teleports()
            .listeners();

        assertThrows(UnsupportedOperationException.class, () -> shown.clear());
    }

    @Test
    void homesKeepTheirOwnOrder() {
        Listeners listeners = new Listeners();
        List<String> order = new ArrayList<>();
        listeners.homes()
            .register(5, new HomeWatcher("late", order));
        listeners.homes()
            .register(0, new HomeWatcher("early", order));

        for (HomeEvents.Listener listener : listeners.homes()
            .listeners()) {
            listener.changed(java.util.Collections.<UUID>emptySet());
        }

        assertEquals(java.util.Arrays.asList("early", "late"), order);
    }

    @Test
    void clearEmptiesBothRegistries() {
        Listeners listeners = new Listeners();
        listeners.teleports()
            .register(0, new Watcher("gone", new ArrayList<>()));
        listeners.homes()
            .register(0, new HomeWatcher("gone", new ArrayList<>()));

        listeners.clear();

        assertTrue(
            listeners.teleports()
                .listeners()
                .isEmpty());
        assertTrue(
            listeners.homes()
                .listeners()
                .isEmpty());
    }

    private static final class Watcher implements TeleportEvents.Listener {

        private final String name;
        private final List<String> order;

        Watcher(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public EssentialsEvents.Kind kind() {
            return EssentialsEvents.Kind.INFORM;
        }

        @Override
        public void afterMove(TeleportJob job) {
            order.add(name);
        }
    }

    private static final class HomeWatcher implements HomeEvents.Listener {

        private final String name;
        private final List<String> order;

        HomeWatcher(String name, List<String> order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public EssentialsEvents.Kind kind() {
            return EssentialsEvents.Kind.INFORM;
        }

        @Override
        public void afterSet(UUID player, HomeRecord home, boolean overwrite) {
            order.add(name);
        }

        @Override
        public void changed(Set<UUID> players) {
            order.add(name);
        }
    }
}
