package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;

/**
 * The engine a vehicle is set down with (mechs-nouns.md, a vehicle is a mech for parts): one of the three engines a
 * mech runs, as the numbers the drop reads, so the battle never sees an inventory object. It works by the mech's rule:
 * the engine's {@code speedFraction} along the hull's speed band ({@link VehicleType#REFIT_SPEED_FACTOR}), so a heavy
 * hull stays slower than a light one whatever it runs, and the military engine runs hotter, costing the hull's
 * reservoir its dissipation penalty as it costs a mech's. A vehicle has no output budget.
 */
public enum VehicleEngine {
    /** The industrial engine every hull is built with: its own speed. */
    STOCK(MechInternal.INDUSTRIAL_ENGINE),
    /** The uprated engine: half the band, a tenth more top speed and acceleration. */
    UPRATED(MechInternal.UPRATED_ENGINE),
    /** The military engine: the whole band, a fifth more, and half a flux a second less dissipation. */
    MILITARY(MechInternal.MILITARY_ENGINE);

    private final MechInternal part;

    VehicleEngine(MechInternal part) {
        this.part = part;
    }

    /** The engine part this is. */
    public MechInternal part() { return part; }

    /** What this engine multiplies the hull's top speed and acceleration by: its place on the hull's band. */
    public float speedFactor() {
        return MechFrameBudget.band(1f, VehicleType.REFIT_SPEED_FACTOR, part.speedFraction);
    }

    /** What this engine adds to the hull reservoir's dissipation: the military engine's running-flux penalty. */
    public float dissipation() { return part.dissipation; }

    /** The engine {@code engine} is, or {@link #STOCK} for none or a part that is no engine. */
    public static VehicleEngine of(MechInternal engine) {
        for (VehicleEngine tier : values()) if (tier.part == engine) return tier;
        return STOCK;
    }
}
