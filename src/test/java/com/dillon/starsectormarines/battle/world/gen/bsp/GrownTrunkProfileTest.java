package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan.Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Profile#of} is the single density control, and every other knob is
 * derived from it. These pin the derivation, not the maps it produces: what
 * went wrong before was three hand-tuned profiles that disagreed with each
 * other, and the guard against that is arithmetic on one function.
 */
class GrownTrunkProfileTest {

    private static final float[] LADDER = { 0f, 0.2f, 0.4f, 0.55f, 0.75f, 1f };

    @Test
    void densityRaisesEveryDerivedKnobTogether() {
        for (int i = 1; i < LADDER.length; i++) {
            Profile lo = Profile.of(LADDER[i - 1]);
            Profile hi = Profile.of(LADDER[i]);
            assertTrue(hi.junctionBudget >= lo.junctionBudget,
                    "junction budget fell from density " + LADDER[i - 1] + " to " + LADDER[i]);
            assertTrue(hi.branchChance >= lo.branchChance,
                    "branch chance fell from density " + LADDER[i - 1] + " to " + LADDER[i]);
            assertTrue(hi.fourWayChance >= lo.fourWayChance,
                    "four-way chance fell from density " + LADDER[i - 1] + " to " + LADDER[i]);
            assertTrue(hi.frontageDepth >= lo.frontageDepth,
                    "frontage depth fell from density " + LADDER[i - 1] + " to " + LADDER[i]);
        }
    }

    /**
     * The one knob that must NOT scale with density. A sparse profile with
     * longer arms lays a thin ribbon of frontage across the whole map instead
     * of making a smaller settlement, which measured as denser than the profile
     * above it on some seeds.
     */
    @Test
    void armLengthIsIndependentOfDensity() {
        Profile base = Profile.of(0f);
        for (float d : LADDER) {
            Profile p = Profile.of(d);
            assertEquals(base.armLenLoFrac, p.armLenLoFrac, 0f, "arm lo frac moved at density " + d);
            assertEquals(base.armLenHiFrac, p.armLenHiFrac, 0f, "arm hi frac moved at density " + d);
        }
    }

    @Test
    void fullDensityBuildsEverywhereAndZeroDensityDoesNot() {
        assertEquals(Integer.MAX_VALUE, Profile.of(1f).frontageDepth,
                "a full-density map should have no hinterland at all");
        assertTrue(Profile.of(0f).frontageDepth < Integer.MAX_VALUE,
                "an empty-density map must still bound how far building reaches from a road");
    }

    @Test
    void densityIsClampedRatherThanExtrapolated() {
        assertEquals(Profile.of(0f).junctionBudget, Profile.of(-5f).junctionBudget);
        assertEquals(Profile.of(1f).junctionBudget, Profile.of(7f).junctionBudget);
        assertEquals(Profile.of(1f).frontageDepth, Profile.of(7f).frontageDepth);
    }

    @Test
    void namedProfilesSitOnTheLadderInOrder() {
        assertTrue(Profile.hamlet().junctionBudget < Profile.town().junctionBudget);
        assertTrue(Profile.town().junctionBudget < Profile.city().junctionBudget);
        assertEquals(Integer.MAX_VALUE, Profile.city().frontageDepth);
    }
}
