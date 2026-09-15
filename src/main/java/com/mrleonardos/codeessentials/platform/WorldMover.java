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
        Shift shift = new Shift();
        CancelReason refusal;
        try {
            refusal = apply(player, landing, shift);
        } catch (RuntimeException failure) {
            if (shift.started) {
                log.error(
                    "Teleport {} of {} for {} broke after the player had already been shifted, the landing stands: {}",
                    Long.valueOf(job.id()),
                    player,
                    job.cause()
                        .key(),
                    failure.toString(),
                    failure);
                report.done(landing);
                return;
            }
            log.error(
                "Teleport {} of {} for {} broke on the way and the slot is free again: {}",
                Long.valueOf(job.id()),
                player,
                job.cause()
                    .key(),
                failure.toString(),
                failure);
            report.failed(CancelReason.TIMEOUT);
            return;
        }
        if (refusal != null) {
            report.failed(refusal);
            return;
        }
        report.done(landing);
    }

    private CancelReason apply(UUID player, Point landing, Shift shift) {
        if (!moves.online(player)) {
            return CancelReason.DISCONNECTED;
        }
        if (!moves.world(landing.dimension())) {
            return CancelReason.DIMENSION_MISSING;
        }
        moves.dismount(player);
        moves.closeScreen(player);
        if (!moves.chunks(landing)) {
            return CancelReason.CHUNK_MISSING;
        }
        if (moves.dimensionOf(player) != landing.dimension()) {
            shift.started = true;
            moves.transfer(player, landing);
        }
        shift.started = true;
        moves.place(player, landing);
        moves.settle(player);
        return null;
    }

    private static final class Shift {

        private boolean started;
    }
}
