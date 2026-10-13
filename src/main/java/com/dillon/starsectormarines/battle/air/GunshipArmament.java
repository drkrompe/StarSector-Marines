package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * The guns a gunship carries, and where they sit on the hull.
 *
 * <p>An ordinary armed craft takes its mounts from the hull's own {@code weaponSlots}
 * ({@link AirArmament}). A Mudskipper's hull publishes one small turret facing aft, which is a
 * transport's door gun and nothing a gunship could be built on, so this is the one place the
 * mounts are authored by hand: a concrete non-standard craft, which is the exception the air
 * model allows to reading the hull. Positions are fractions of the hull's drawn length so they
 * stay on the hull whatever the render scale resolves to.
 *
 * <p>Two Heavy MGs a side. Each is a <em>broadside</em> mount: it rests on its own beam and fires
 * only within an arc about it, so of the four guns the two on the flank that faces the ring's
 * centre work and the other two are along for the weight. That is the whole of "fires out the
 * side"; nothing else about the weapon is special-cased.
 */
public final class GunshipArmament {

    /** The gun every broadside mount carries. */
    static final String GUN = TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID;
    /** Guns on each flank. */
    static final int GUNS_PER_SIDE = 2;
    /** Half-width of each gun's field of fire about its beam, degrees. Wide enough to cover the ring's far side, short of the nose and tail. */
    public static final float ARC_HALF_WIDTH_DEG = 75f;

    /** Lateral offset of the mounts from the centreline, as a fraction of drawn length. */
    private static final float SIDE_OFFSET = 0.24f;
    /** Fore-and-aft spacing of the two guns on a flank, as a fraction of drawn length either side of midships. */
    private static final float FORE_AFT_OFFSET = 0.14f;
    /** Beam bearing off the nose: +90 is port, -90 starboard. */
    private static final float PORT_BEAM_DEG = 90f;

    private GunshipArmament() {}

    /**
     * The mounts for a gunship of {@code type} whose hull currently points {@code facingDeg}.
     * Starboard guns first, then port, each flank fore to aft.
     */
    public static MountedTurret[] mounts(ShuttleType type, float facingDeg) {
        float length = HullFootprintResolver.visualLengthCells(type.renderHullId());
        MountedTurret[] out = new MountedTurret[GUNS_PER_SIDE * 2];
        int i = 0;
        for (int side = 0; side < 2; side++) {
            boolean port = side == 1;
            float rest = port ? PORT_BEAM_DEG : -PORT_BEAM_DEG;
            for (int k = 0; k < GUNS_PER_SIDE; k++) {
                float fore = (k == 0 ? 1f : -1f) * FORE_AFT_OFFSET * length;
                float lateral = (port ? -1f : 1f) * SIDE_OFFSET * length;
                MountedTurret turret = new MountedTurret(
                        new TurretMount(GUN, lateral, fore), rest, ARC_HALF_WIDTH_DEG);
                turret.facingDegrees = turret.restBearingDeg(facingDeg);
                out[i++] = turret;
            }
        }
        return out;
    }
}
