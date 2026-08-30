package com.dillon.starsectormarines.battle.air;

/**
 * Turret-loadout component for an armed air craft — the {@link MountedTurret}
 * array a craft carries. Lives in the world's {@code AIR_TURRETS} OBJECT column
 * (has-gated) keyed by air entity id: a craft with a fire-support role
 * <em>has</em> this component, a pure transport doesn't. That presence is the
 * gate the turret-fire logic reads, replacing the old
 * {@code if (turrets.length == 0)} scan over every shuttle.
 *
 * <p>Pure data; the behavior that drives it is {@link AirSystem}'s turret tick.
 */
public final class AirTurrets {

    /** The craft's mounted turrets. Non-empty — a craft with no mounts carries no component at all. */
    public final MountedTurret[] mounts;

    public AirTurrets(MountedTurret[] mounts) {
        this.mounts = mounts;
    }
}
