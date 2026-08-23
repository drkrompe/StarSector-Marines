package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Simulation tuning for a smoke-grenade utility special. */
public record SmokeGrenadeSpec(
        float throwRange,
        float throwDuration,
        float flightSeconds,
        float arcHeight,
        float cloudRadius,
        float cloudDuration) implements Serializable {
}
