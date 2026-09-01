package com.mrleonardos.codeessentials.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportCause;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.api.teleport.TeleportRequest;
import com.mrleonardos.codeessentials.internal.engine.Mover;

class WorldMoverTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final Point NETHER = Point.of(-1, 8.5D, 64.0D, 8.5D, 90.0F, 0.0F);
    private static final Point OVERWORLD = Point.of(0, 100.5D, 70.0D, -30.5D);

    @Test
    void crossWorldMoveKeepsTheOrderOfOperations() {
        Steps steps = new Steps().at(0);
        Answer answer = move(steps, NETHER);

        assertEquals(
            Arrays.asList(
                "online",
                "world -1",
                "dismount",
                "closeScreen",
                "chunks -1",
                "dimensionOf",
                "transfer -1",
                "place -1",
                "settle"),
            steps.done);
        assertEquals(NETHER, answer.landing);
    }

    @Test
    void moveInsideOneWorldSkipsTheTransfer() {
        Steps steps = new Steps().at(0);
        Answer answer = move(steps, OVERWORLD);

        assertFalse(steps.done.contains("transfer 0"), () -> steps.done.toString());
        assertTrue(steps.done.indexOf("chunks 0") < steps.done.indexOf("place 0"), () -> steps.done.toString());
        assertEquals(OVERWORLD, answer.landing);
    }

    @Test
    void anOfflinePlayerStopsBeforeTheFirstStep() {
        Steps steps = new Steps().at(0);
        steps.online = false;

        Answer answer = move(steps, OVERWORLD);

        assertEquals(Arrays.asList("online"), steps.done);
        assertEquals(CancelReason.DISCONNECTED, answer.reason);
    }

    @Test
    void aDimensionThatDoesNotComeUpFailsBeforeTheWorldIsTouched() {
        Steps steps = new Steps().at(0);
        steps.world = false;

        Answer answer = move(steps, NETHER);

        assertEquals(Arrays.asList("online", "world -1"), steps.done);
        assertEquals(CancelReason.DIMENSION_MISSING, answer.reason);
    }

    @Test
    void missingChunksFailAfterTheRiderIsOffAndTheScreenIsClosed() {
        Steps steps = new Steps().at(0);
        steps.chunks = false;

        Answer answer = move(steps, OVERWORLD);

        assertEquals(Arrays.asList("online", "world 0", "dismount", "closeScreen", "chunks 0"), steps.done);
        assertEquals(CancelReason.CHUNK_MISSING, answer.reason);
    }

    @Test
    void aBrokenStepFreesTheSlotInsteadOfThrowing() {
        Steps steps = new Steps().at(0);
        steps.breakPlace = true;

        Answer answer = move(steps, OVERWORLD);

        assertEquals(CancelReason.TIMEOUT, answer.reason);
        assertFalse(steps.done.contains("settle"), () -> steps.done.toString());
    }

    private static Answer move(Steps steps, Point landing) {
        Answer answer = new Answer();
        Mover mover = new WorldMover(steps, LogManager.getLogger("CodeEssentialsTest"));
        mover.move(job(landing), landing, answer);
        return answer;
    }

    private static TeleportJob job(Point landing) {
        return TeleportJob.starting(
            1L,
            TeleportRequest.builder(PLAYER, landing, TeleportCause.HOME)
                .build());
    }

    private static final class Answer implements Mover.Report {

        private Point landing;
        private CancelReason reason;

        @Override
        public void done(Point spot) {
            landing = spot;
        }

        @Override
        public void failed(CancelReason failure) {
            reason = failure;
        }
    }

    private static final class Steps implements WorldMover.Moves {

        private final List<String> done = new ArrayList<>();

        private boolean online = true;
        private boolean world = true;
        private boolean chunks = true;
        private boolean breakPlace;
        private int dimension;

        Steps at(int where) {
            dimension = where;
            return this;
        }

        @Override
        public boolean online(UUID player) {
            done.add("online");
            return online;
        }

        @Override
        public boolean world(int wanted) {
            done.add("world " + wanted);
            return world;
        }

        @Override
        public boolean chunks(Point landing) {
            done.add("chunks " + landing.dimension());
            return chunks;
        }

        @Override
        public int dimensionOf(UUID player) {
            done.add("dimensionOf");
            return dimension;
        }

        @Override
        public void dismount(UUID player) {
            done.add("dismount");
        }

        @Override
        public void closeScreen(UUID player) {
            done.add("closeScreen");
        }

        @Override
        public void transfer(UUID player, Point landing) {
            done.add("transfer " + landing.dimension());
        }

        @Override
        public void place(UUID player, Point landing) {
            if (breakPlace) {
                throw new IllegalStateException("the network handler is gone");
            }
            done.add("place " + landing.dimension());
        }

        @Override
        public void settle(UUID player) {
            done.add("settle");
        }
    }
}
