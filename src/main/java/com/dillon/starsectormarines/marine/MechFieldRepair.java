package com.dillon.starsectormarines.marine;

/**
 * What the company ship's Mech Lab field-repair refit is worth between missions: how much of a
 * free repair's clock it takes off, and what a replacement weapon assembly costs to
 * fabricate (the spares rack, which is what refitting a mount lost in battle comes to). Read when
 * an order is quoted, never frozen; {@link #NEUTRAL} off the founding hull.
 */
public record MechFieldRepair(float repairDaysMult, float weaponBillMult) {

    public static final MechFieldRepair NEUTRAL = new MechFieldRepair(1f, 1f);

    public MechFieldRepair {
        if (!(repairDaysMult > 0f) || !Float.isFinite(repairDaysMult)
                || !(weaponBillMult > 0f) || !Float.isFinite(weaponBillMult)) {
            throw new IllegalArgumentException("Field repair multipliers must be finite and positive");
        }
    }

    /** A repair's clock in the gantry; never below the shortest repair there is. */
    public float repairDays(float days) {
        return Math.max(MechRepairs.MINIMUM_DAYS, days * repairDaysMult);
    }

    /** A weapon assembly's price with the spares rack's share off, rounded up; never below one credit. */
    public FabricationCost weaponBill(FabricationCost base) {
        if (base == null || weaponBillMult == 1f) return base;
        return base.scaled(weaponBillMult);
    }
}
