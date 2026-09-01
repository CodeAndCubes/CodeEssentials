package com.mrleonardos.codeessentials.internal.engine;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.CancelReason;
import com.mrleonardos.codeessentials.api.teleport.TeleportJob;

public interface Mover {

    interface Report {

        void done(Point spot);

        void failed(CancelReason reason);
    }

    void move(TeleportJob job, Point landing, Report report);
}
