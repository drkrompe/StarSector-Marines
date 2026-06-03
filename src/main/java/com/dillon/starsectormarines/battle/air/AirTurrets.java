package com.dillon.starsectormarines.battle.air;

/**
 * Turret-loadout component for an armed air craft — the {@link MountedTurret}
 * array a craft carries. Lives in a {@code ComponentStore<AirTurrets>} keyed by
 * air entity id: a craft with a fire-support role <em>has</em> this component,
 * a pure transport doesn't. That presence is the gate the turret-fire and
 * hover-loiter logic reads, replacing the old {@code if (turrets.length == 0)}
 * scan over every shuttle.
 *
 * <p>Mostly data; {@link #allDry()} is a small pure query over the mounts (the
 * hover-loiter exit trigger), kept here the way {@code Entity#isAlive()} sits on
 * the unit handle.
 */
public final class AirTurrets {

    /** The craft's mounted turrets. Non-empty — a craft with no mounts carries no component at all. */
    public final MountedTurret[] mounts;

    public AirTurrets(MountedTurret[] mounts) {
        this.mounts = mounts;
    }

    /** True once every mount has fired its magazine dry — one of the HOVER_STATION exit triggers. */
    public boolean allDry() {
        for (MountedTurret t : mounts) {
            if (!t.ammoDry()) return false;
        }
        return true;
    }
}
