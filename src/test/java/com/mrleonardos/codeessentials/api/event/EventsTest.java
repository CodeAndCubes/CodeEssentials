package com.mrleonardos.codeessentials.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;

class EventsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static final Point TARGET = Point.of(0, 0.5D, 64.0D, 0.5D);

    @Test
    void allowIsSilentAndDenyExplainsItself() {
        EssentialsEvents.Decision allow = EssentialsEvents.Decision.allow();
        EssentialsEvents.Decision deny = EssentialsEvents.Decision.deny("чужой регион");

        assertTrue(allow.allowed());
        assertFalse(
            allow.reason()
                .isPresent());
        assertEquals("allow", allow.toString());
        assertFalse(deny.allowed());
        assertEquals(
            "чужой регион",
            deny.reason()
                .get());
        assertEquals("deny: чужой регион", deny.toString());
        assertThrows(NullPointerException.class, () -> EssentialsEvents.Decision.deny(null));
    }

    @Test
    void decisionsCompareByAnswerAndReason() {
        EssentialsEvents.Decision deny = EssentialsEvents.Decision.deny("нельзя");

        assertEquals(deny, EssentialsEvents.Decision.deny("нельзя"));
        assertEquals(
            deny.hashCode(),
            EssentialsEvents.Decision.deny("нельзя")
                .hashCode());
        assertFalse(deny.equals(EssentialsEvents.Decision.deny("другая причина")));
        assertFalse(deny.equals(EssentialsEvents.Decision.allow()));
        assertFalse(deny.equals("deny"));
        assertEquals(EssentialsEvents.Decision.allow(), EssentialsEvents.Decision.allow());
        assertEquals(2, EssentialsEvents.Kind.values().length);
    }

    @Test
    void listenerThatDeclaredNothingLetsEverythingThrough() {
        TeleportEvents.Listener watcher = new TeleportEvents.Listener() {

            @Override
            public EssentialsEvents.Kind kind() {
                return EssentialsEvents.Kind.INFORM;
            }
        };
        TeleportJob job = TeleportJob.starting(
            1L,
            TeleportRequest.builder(PLAYER, TARGET, TeleportCause.HOME)
                .build());

        assertEquals(EssentialsEvents.Kind.INFORM, watcher.kind());
        assertTrue(
            watcher.beforeStart(job)
                .allowed());
        assertTrue(
            watcher.beforeMove(job)
                .allowed());
        watcher.afterMove(job);
        watcher.cancelled(job);
    }

    @Test
    void homeListenerThatDeclaredNothingLetsEverythingThrough() {
        HomeEvents.Listener watcher = new HomeEvents.Listener() {

            @Override
            public EssentialsEvents.Kind kind() {
                return EssentialsEvents.Kind.ENFORCE;
            }
        };
        HomeRecord home = HomeRecord.of("home", TARGET, 1L);

        assertEquals(EssentialsEvents.Kind.ENFORCE, watcher.kind());
        assertTrue(
            watcher.beforeSet(PLAYER, home, false)
                .allowed());
        watcher.afterSet(PLAYER, home, true);
        watcher.changed(Collections.singleton(PLAYER));
    }
}
