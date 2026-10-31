package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MachineSystems;

/**
 * What a hull's fit makes of it at the drop ({@code convoy-nouns.md}, a vehicle instance is a hull and a loadout): the
 * plating it wears (its armor factor and, on a tracked hull, its skirts) and what its auxiliary compartment adds
 * ({@link MachineSystems}: a spall liner's structure, a ready rack's rounds). Every factor multiplies the hull's own
 * and every bonus adds to it.
 */
public record HullFitEffect(float armorFactor, float structureFactor, float ammoFactor, float armorRatingBonus) {
    public static final HullFitEffect NONE = new HullFitEffect(1f, 1f, 1f, 0f);

    /** The effect of {@code plating} on a hull of {@code type}; factory plating, or none, changes nothing. */
    public static HullFitEffect of(VehiclePlating plating, VehicleType type) {
        if (plating == null) return NONE;
        return new HullFitEffect(plating.armorFactor(), 1f, 1f, plating.armorRatingBonus(type));
    }

    /** This effect with what the hull's compartment adds: its liner's structure, its ready rack's rounds. */
    public HullFitEffect with(MachineSystems systems) {
        if (systems == null) return this;
        return new HullFitEffect(armorFactor, structureFactor * systems.structureFactor(),
                ammoFactor * systems.ammoFactor(), armorRatingBonus);
    }
}
