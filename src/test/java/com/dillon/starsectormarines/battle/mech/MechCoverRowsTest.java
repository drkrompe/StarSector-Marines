package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.BodyPlan;
import com.dillon.starsectormarines.battle.combat.CoverProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cover by section: what each level of cover hides of a mech, read from its plan's rows. */
class MechCoverRowsTest {

    private static final MechBodyPlan[] CHASSIS = {MechBodyPlan.HOUND, MechBodyPlan.BULWARK, MechBodyPlan.SIROCCO};

    @Test
    void everyMechBandCarriesCoverRows() {
        for (MechBodyPlan plan : CHASSIS) {
            for (BodyPart band : plan.bodyPlan().parts()) {
                assertTrue(band.hasCoverRows(), plan + " " + band);
            }
        }
    }

    @Test
    void mechRowsReadFromTheGroundWhileAManKeepsTheCatchBand() {
        for (MechBodyPlan plan : CHASSIS) assertTrue(plan.bodyPlan().coverFromGround(), plan.id());
        assertFalse(BodyPlan.INFANTRY.coverFromGround());
    }

    @Test
    void aBermHidesTheLegsAndLeavesTheShouldersExposed() {
        MechBodyPlan hound = MechBodyPlan.HOUND;
        for (int level = 1; level <= 3; level++) {
            float legs = CoverProfile.edge(level, hound.legsBand());
            assertTrue(legs > CoverProfile.edge(level, hound.torsoBand()), "level " + level);
            assertEquals(0f, CoverProfile.edge(level, hound.shoulderBand()), "level " + level);
            assertEquals(0f, CoverProfile.edge(level, hound.headBand()), "level " + level);
            assertEquals(0f, CoverProfile.occupant(level, hound.shoulderBand()), "level " + level);
            assertEquals(0f, CoverProfile.crossed(level, hound.headBand()), "level " + level);
        }
        assertTrue(CoverProfile.edge(2, hound.legsBand()) >= 0.85f, "a medium berm hides most of the legs");
        assertEquals(1f, CoverProfile.edge(3, hound.legsBand()), "a full wall hides all of them");
    }

    @Test
    void noCoverHidesNothing() {
        for (BodyPart band : MechBodyPlan.BULWARK.bodyPlan().parts()) {
            assertEquals(0f, CoverProfile.edge(0, band));
            assertEquals(0f, CoverProfile.occupant(0, band));
            assertEquals(0f, CoverProfile.crossed(0, band));
        }
    }

    @Test
    void coverHidesMoreAtEachLevelButLessOfAMechThanOfAMan() {
        for (MechBodyPlan plan : CHASSIS) {
            float previous = 0f;
            for (int level = 1; level <= 3; level++) {
                float mech = CoverProfile.expectedEdgeInterception(plan.bodyPlan(), level);
                assertTrue(mech > previous, plan + " level " + level);
                assertTrue(mech < CoverProfile.expectedEdgeInterception(BodyPlan.INFANTRY, level),
                        plan + " level " + level + ": a mech stands taller than the cover a man fits behind");
                previous = mech;
            }
        }
    }

    @Test
    void theLowestHeightsAreTheLegsBand() {
        MechBodyPlan hound = MechBodyPlan.HOUND;
        assertSame(hound.legsBand(), hound.bodyPlan().atHeight(0.05f));
        assertSame(hound.shoulderBand(), hound.bodyPlan().atHeight(0.85f));
    }
}
