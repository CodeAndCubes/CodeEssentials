package com.mrleonardos.codeessentials.platform;

import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;
import com.mrleonardos.codeessentials.internal.engine.Mover;

final class WorldMover implements Mover {

    interface Moves {

        boolean online(UUID player);

        boolean world(int dimension);

        boolean chunks(Point landing);

        int dimensionOf(UUID player);

        void dismount(UUID player);

        void closeScreen(UUID player);

        void transfer(UUID player, Point landing);

        void place(UUID player, Point landing);

        void settle(UUID player);
    }

    private final Moves moves;
    private final Logger log;

    WorldMover(Moves moves, Logger log) {
        this.moves = moves;
        this.log = log;
    }

    @Override
    public void move(TeleportJob job, Point landing, Report report) {
        UUID player = job.player();
        try {
            if (!moves.online(player)) {
                report.failed(CancelReason.DISCONNECTED);
                return;
            }
            if (!moves.world(landing.dimension())) {
                report.failed(CancelReason.DIMENSION_MISSING);
                return;
            }
            moves.dismount(player);
            moves.closeScreen(player);
            if (!moves.chunks(landing)) {
                report.failed(CancelReason.CHUNK_MISSING);
                return;
            }
            if (moves.dimensionOf(player) != landing.dimension()) {
                moves.transfer(player, landing);
            }
            moves.place(player, landing);
            moves.settle(player);
            report.done(landing);
        } catch (RuntimeException failure) {
            log.error("Teleport {} broke on the way and the slot is free again: {}", job, failure.toString(), failure);
            report.failed(CancelReason.TIMEOUT);
        }
    }
}
