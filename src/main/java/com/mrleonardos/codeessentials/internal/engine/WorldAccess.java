package com.mrleonardos.codeessentials.internal.engine;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;

public interface WorldAccess {

    Optional<BlockView> view(int dimension);

    Optional<Point> position(UUID player);
}
