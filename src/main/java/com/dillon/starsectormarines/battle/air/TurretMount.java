package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.TurretKind;

/**
 * Static config for one hardpoint on a {@link ShuttleType} — what kind of
 * turret it carries and where on the shuttle's sprite it sits (in the
 * shuttle's local frame, +Y forward / +X right, cells). The kit selected for
 * a shuttle is an array of these; the runtime {@link MountedTurret} hangs off
 * each at battle time.
 *
 * <p>Pure data: no behavior, immutable after construction.
 */
public final class TurretMount {

    public final TurretKind kind;
    /** Lateral offset from shuttle center, cells. Positive = right side of the hull. */
    public final float localOffsetX;
    /** Longitudinal offset from shuttle center, cells. Positive = toward the nose. */
    public final float localOffsetY;

    public TurretMount(TurretKind kind, float localOffsetX, float localOffsetY) {
        this.kind = kind;
        this.localOffsetX = localOffsetX;
        this.localOffsetY = localOffsetY;
    }
}
