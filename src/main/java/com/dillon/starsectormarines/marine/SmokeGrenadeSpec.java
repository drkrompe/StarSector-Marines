package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * Simulation tuning for a smoke-grenade utility special: a thrown canister that stands a cloud of smoke on a cell.
 *
 * @param thinSeconds the last stretch of the cloud's life over which it contracts toward a core and then clears, rather than
 *                    standing at full size until the instant it goes. Zero for a cloud that stands whole and vanishes (the
 *                    hand grenade); a long-lasting screen thins, so the ground it screens shrinks before it
 *                    is gone and a planner may count on it at full size only until the thin begins.
 */
public record SmokeGrenadeSpec(
        float throwRange,
        float throwDuration,
        float flightSeconds,
        float arcHeight,
        float cloudRadius,
        float cloudDuration,
        float thinSeconds) implements Serializable {

    /** A cloud that stands whole for its duration and then goes. */
    public SmokeGrenadeSpec(float throwRange, float throwDuration, float flightSeconds, float arcHeight,
                            float cloudRadius, float cloudDuration) {
        this(throwRange, throwDuration, flightSeconds, arcHeight, cloudRadius, cloudDuration, 0f);
    }

    public SmokeGrenadeSpec {
        thinSeconds = Math.max(0f, Math.min(thinSeconds, cloudDuration));
    }

    /** Seconds the cloud holds its full radius: its life less the stretch it thins over. */
    public float coverSeconds() {
        return cloudDuration - thinSeconds;
    }
}
