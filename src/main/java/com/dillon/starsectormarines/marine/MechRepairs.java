package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;

/**
 * How long putting a damaged mech right takes: a clock in campaign days, scaled by how much of the
 * chassis has to be rebuilt ({@link MechDamage#wear}). The gantry crew repairs between missions
 * for nothing but time; the clock starts at settlement ({@link MechBay#settleBattleDamage}).
 */
public final class MechRepairs {

    /** Campaign days a fully worn chassis spends in its gantry, by weight. */
    static final float LIGHT_DAYS_AT_FULL_WEAR = 3f;
    static final float MEDIUM_DAYS_AT_FULL_WEAR = 4f;
    static final float HEAVY_DAYS_AT_FULL_WEAR = 5f;
    /** No repair is shorter than this; a scratched mech still goes through the gantry. */
    static final float MINIMUM_DAYS = 0.25f;

    private MechRepairs() {
    }

    /** The clock for {@code damage} on {@code variant}, or null when there is none. */
    public static Estimate estimate(MechVariant variant, MechDamage damage) {
        if (variant == null || damage == null || damage.isEmpty()) return null;
        float wear = damage.wear(variant.bodyPlan());
        return new Estimate(wear, Math.max(MINIMUM_DAYS, wear * daysAtFullWear(variant)));
    }

    static float daysAtFullWear(MechVariant variant) {
        return switch (variant) {
            case HOUND -> LIGHT_DAYS_AT_FULL_WEAR;
            case WARDEN -> MEDIUM_DAYS_AT_FULL_WEAR;
            case BULWARK -> HEAVY_DAYS_AT_FULL_WEAR;
        };
    }

    /**
     * One repair.
     *
     * @param wear how much of the chassis is rebuilt, 0 to 1
     * @param days campaign days the repair takes before the ship's field-repair refit
     */
    public record Estimate(float wear, float days) {
    }
}
