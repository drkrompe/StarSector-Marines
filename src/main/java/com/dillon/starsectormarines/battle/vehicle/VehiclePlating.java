package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;

/**
 * The plating a vehicle is set down in (mechs-nouns.md, the armor point): one of the three plating parts a mech wears,
 * as the numbers the drop reads, so the battle never sees an inventory object. It works by the mech's rule: the
 * plating's {@code armorFraction} along the hull's armor band ({@link VehicleType#REFIT_ARMOR_FACTOR}). Heavy plating
 * also skirts a tracked hull.
 */
public enum VehiclePlating {
    /** The factory plating every hull is built with: its own plate. */
    FACTORY(MechInternal.FACTORY_PLATING, 0f),
    /** Composite plating: half the band, a fifth more armor capacity on every plate and the turret. */
    COMPOSITE(MechInternal.COMPOSITE_PLATING, 0f),
    /** Heavy plating: the whole band, two fifths more armor capacity, and on tracked hulls skirted: +2 armor rating. */
    HEAVY(MechInternal.HEAVY_PLATING, 2f);

    private final MechInternal part;
    private final float trackedRatingBonus;

    VehiclePlating(MechInternal part, float trackedRatingBonus) {
        this.part = part;
        this.trackedRatingBonus = trackedRatingBonus;
    }

    /** The plating part this is. */
    public MechInternal part() { return part; }

    /** What this plating multiplies every plate's capacity by: its place on the hull's armor band. */
    public float armorFactor() {
        return MechFrameBudget.band(1f, VehicleType.REFIT_ARMOR_FACTOR, part.armorFraction);
    }

    /** The armor rating this plating adds to {@code type}: the skirts only a tracked hull can carry. */
    public float armorRatingBonus(VehicleType type) {
        return type != null && type.isTank() ? trackedRatingBonus : 0f;
    }

    /** The plating {@code plating} is, or {@link #FACTORY} for none or a part that is no plating. */
    public static VehiclePlating of(MechInternal plating) {
        for (VehiclePlating tier : values()) if (tier.part == plating) return tier;
        return FACTORY;
    }
}
