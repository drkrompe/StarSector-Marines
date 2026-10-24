package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * What putting a damaged mech right costs: a material bill in the fabrication currency and a
 * clock in campaign days, both scaled by how much of the chassis has to be rebuilt
 * ({@link MechDamage#wear}). The boat precedent ({@code boat-condition-and-repairs.md}) sets
 * the scale: at full wear a repair costs half the chassis's own bill.
 */
public final class MechRepairs {

    /** Fraction of the chassis bill a fully worn chassis costs to rebuild. */
    static final float BILL_AT_FULL_WEAR = 0.5f;
    /** Campaign days a fully worn chassis spends in its gantry, by weight. */
    static final float LIGHT_DAYS_AT_FULL_WEAR = 3f;
    static final float MEDIUM_DAYS_AT_FULL_WEAR = 4f;
    static final float HEAVY_DAYS_AT_FULL_WEAR = 5f;
    /** No repair is shorter than this; a scratched mech still goes through the gantry. */
    static final float MINIMUM_DAYS = 0.25f;

    private MechRepairs() {
    }

    /** The bill and clock for {@code mech}'s recorded damage, or null when it is whole. */
    public static Estimate estimate(CampaignMech mech) {
        if (mech == null || mech.damage() == null) return null;
        return estimate(mech.variant(), mech.damage());
    }

    public static Estimate estimate(MechVariant variant, MechDamage damage) {
        if (variant == null || damage == null || damage.isEmpty()) return null;
        float wear = damage.wear(variant.bodyPlan());
        float days = Math.max(MINIMUM_DAYS, wear * daysAtFullWear(variant));
        return new Estimate(wear, days, bill(variant, wear));
    }

    static float daysAtFullWear(MechVariant variant) {
        return switch (variant) {
            case HOUND -> LIGHT_DAYS_AT_FULL_WEAR;
            case WARDEN -> MEDIUM_DAYS_AT_FULL_WEAR;
            case BULWARK -> HEAVY_DAYS_AT_FULL_WEAR;
        };
    }

    /** Every line of the standard-fit bill, scaled by wear and rounded up; never less than one. */
    static FabricationCost bill(MechVariant variant, float wear) {
        FabricationCost standard = MechFabricationCatalog.standardFitCost(variant);
        if (standard == null) throw new IllegalStateException("no chassis bill for " + variant);
        List<FabricationCost.Line> lines = new ArrayList<>();
        for (FabricationCost.Line line : standard.lines()) {
            int quantity = Math.max(1, (int) Math.ceil(line.quantity() * BILL_AT_FULL_WEAR * wear));
            lines.add(new FabricationCost.Line(line.commodityId(), quantity));
        }
        return new FabricationCost(lines);
    }

    /**
     * One repair order.
     *
     * @param wear how much of the chassis is rebuilt, 0 to 1
     * @param days campaign days the mech spends in its gantry once the order is paid
     * @param bill the materials the order spends, before the workshop's own quote
     */
    public record Estimate(float wear, float days, FabricationCost bill) {
    }
}
