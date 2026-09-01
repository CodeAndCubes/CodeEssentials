package com.mrleonardos.codeessentials.internal.service;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

public interface SpotCheck {

    SafeSpotResult check(Point point);
}
