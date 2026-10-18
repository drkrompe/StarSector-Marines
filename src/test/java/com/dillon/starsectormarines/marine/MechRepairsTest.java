package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The repair bill and clock scale with how much of the chassis has to be rebuilt. */
class MechRepairsTest {

    @Test void aWholeMechNeedsNoRepair() {
        assertNull(MechRepairs.estimate(MechVariant.BULWARK, null));
    }

    @Test void fullWearCostsHalfTheChassisBillAndTheWholeClock() {
        MechRepairs.Estimate full = MechRepairs.estimate(MechVariant.BULWARK, wrecked());

        assertEquals(1f, full.wear(), 1e-5f);
        assertEquals(MechRepairs.HEAVY_DAYS_AT_FULL_WEAR, full.days(), 1e-4f);
        // Bulwark chassis bill: 140 supplies, 110 machinery, 260 metals, 36 rare metals.
        assertEquals(70, quantity(full.bill(), Commodities.SUPPLIES));
        assertEquals(55, quantity(full.bill(), Commodities.HEAVY_MACHINERY));
        assertEquals(130, quantity(full.bill(), Commodities.METALS));
        assertEquals(18, quantity(full.bill(), Commodities.RARE_METALS));
    }

    @Test void billAndClockScaleWithWear() {
        // Torso armor stripped: wear = torso share 0.46 x armor weight 0.25 = 0.115.
        MechDamage stripped = MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.TORSO, 1f, 0f).build());
        MechRepairs.Estimate estimate = MechRepairs.estimate(MechVariant.BULWARK, stripped);

        assertEquals(0.115f, estimate.wear(), 1e-5f);
        assertEquals(0.115f * MechRepairs.HEAVY_DAYS_AT_FULL_WEAR, estimate.days(), 1e-4f);
        assertEquals((int) Math.ceil(140 * 0.5f * estimate.wear()), quantity(estimate.bill(), Commodities.SUPPLIES));
        assertEquals((int) Math.ceil(36 * 0.5f * estimate.wear()), quantity(estimate.bill(), Commodities.RARE_METALS));
    }

    @Test void aScratchStillCostsSomethingOnEveryLineAndAMinimumStay() {
        MechDamage scratched = MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.HEAD, 1f, 0.99f).build());
        MechRepairs.Estimate estimate = MechRepairs.estimate(MechVariant.BULWARK, scratched);

        assertEquals(MechRepairs.MINIMUM_DAYS, estimate.days(), 1e-6f);
        for (FabricationCost.Line line : estimate.bill().lines()) assertEquals(1, line.quantity());
    }

    @Test void lighterChassisSpendLessTimeInTheGantry() {
        assertTrue(MechRepairs.estimate(MechVariant.HOUND, wrecked()).days()
                < MechRepairs.estimate(MechVariant.BULWARK, wrecked()).days());
    }

    private static MechDamage wrecked() {
        MechDamageTest.ReportFixture fixture = MechDamageTest.ReportFixture.whole();
        for (MechSection section : MechSection.values()) fixture.section(section, 0f, 0f);
        return MechDamage.of(fixture.build());
    }

    private static int quantity(FabricationCost cost, String commodityId) {
        return cost.lines().stream().filter(line -> line.commodityId().equals(commodityId))
                .mapToInt(FabricationCost.Line::quantity).sum();
    }
}
