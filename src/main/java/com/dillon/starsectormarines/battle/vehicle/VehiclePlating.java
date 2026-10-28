package com.dillon.starsectormarines.battle.vehicle;

/**
 * The plating a company vehicle is set down in (mechs-nouns.md, the armor point): the same three
 * tiers a mech's plating comes in, as the numbers the drop reads. The factory plate is the hull's own
 * and changes nothing; the campaign layer maps a fitted plating part to one of these, so the battle
 * never sees an inventory object.
 */
public enum VehiclePlating {
    /** The hull's own plate. */
    FACTORY(1f, 0f),
    /** A quarter more armor capacity on every plate and the turret. */
    COMPOSITE(1.25f, 0f),
    /** Two fifths more armor capacity, and on tracked hulls skirted: +2 armor rating. */
    HEAVY(1.4f, 2f);

    private final float armorFactor;
    private final float trackedRatingBonus;

    VehiclePlating(float armorFactor, float trackedRatingBonus) {
        this.armorFactor = armorFactor;
        this.trackedRatingBonus = trackedRatingBonus;
    }

    /** What this plating multiplies every plate's capacity by. */
    public float armorFactor() { return armorFactor; }

    /** The armor rating this plating adds to {@code type}: the skirts only a tracked hull can carry. */
    public float armorRatingBonus(VehicleType type) {
        return type != null && type.isTank() ? trackedRatingBonus : 0f;
    }
}
