package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The repair clock scales with how much of the chassis has to be rebuilt. */
class MechRepairsTest {

    @Test void aWholeMechNeedsNoRepair() {
        assertNull(MechRepairs.estimate(MechVariant.BULWARK, null));
    }

    @Test void fullWearTakesTheWholeClock() {
        MechRepairs.Estimate full = MechRepairs.estimate(MechVariant.BULWARK, wrecked());

        assertEquals(1f, full.wear(), 1e-5f);
        assertEquals(MechRepairs.HEAVY_DAYS_AT_FULL_WEAR, full.days(), 1e-4f);
    }

    @Test void theClockScalesWithWear() {
        // Torso armor stripped: wear = torso share 0.46 x armor weight 0.25 = 0.115.
        MechDamage stripped = MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.TORSO, 1f, 0f).build());
        MechRepairs.Estimate estimate = MechRepairs.estimate(MechVariant.BULWARK, stripped);

        assertEquals(0.115f, estimate.wear(), 1e-5f);
        assertEquals(0.115f * MechRepairs.HEAVY_DAYS_AT_FULL_WEAR, estimate.days(), 1e-4f);
    }

    @Test void aScratchStillTakesAMinimumStay() {
        MechDamage scratched = MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.HEAD, 1f, 0.99f).build());

        assertEquals(MechRepairs.MINIMUM_DAYS, MechRepairs.estimate(MechVariant.BULWARK, scratched).days(), 1e-6f);
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
}
