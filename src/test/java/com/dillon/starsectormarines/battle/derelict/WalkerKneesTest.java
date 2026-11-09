package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkerKneesTest {

    @Test
    void aBracedFrontHidesItsKneesWhileFlanksAndTheUndersideExposeThem() {
        assertEquals(0f, WalkerKnees.share(0f, true, false), "braced, the front legs are behind its plate");
        assertTrue(WalkerKnees.share(0f, false, false) > 0f, "free, a front shot can find a knee");
        assertTrue(WalkerKnees.share(90f, true, false) > WalkerKnees.share(180f, true, false),
                "the flank shows the knees more than the rear");
        assertTrue(WalkerKnees.share(0f, true, true) > WalkerKnees.share(90f, true, false),
                "from underneath the knees are the easiest target");
    }

    @Test
    void aKneeTakesTheHitInsteadOfTheTorsoOnTheShootersSide() {
        WalkerKnees knees = new WalkerKnees();
        assertEquals(0f, knees.hit(80f, false, false, 0f, 40f), "a knee took it");
        assertEquals(WalkerKnees.KNEE_STRUCTURE - 40f, knees.structure(0), 1e-4f, "front left, nearest 80 degrees");
        assertEquals(1f, knees.hit(80f, false, false, 0.99f, 40f), "a roll past the share hits the torso");
        assertEquals(WalkerKnees.KNEE_STRUCTURE, knees.structure(3), "the far side is untouched");
    }

    @Test
    void oneLegLostLimpsAndCannotBraceOnAFrontLegTwoLostDownIt() {
        WalkerKnees knees = new WalkerKnees();
        knees.hit(45f, false, false, 0f, WalkerKnees.KNEE_STRUCTURE);
        assertTrue(knees.lost(0));
        assertEquals(WalkerKnees.LIMP_SPEED_SHARE, knees.speedShare());
        assertFalse(knees.canBrace(), "a front leg gone, it cannot plant");
        assertFalse(knees.grips(0), "no grip on that side");
        assertTrue(knees.grips(3));
        assertFalse(knees.downed());
        assertEquals(1, knees.nearest(45f), "the next shot from there finds the nearest standing knee");
        knees.hit(45f, false, false, 0f, WalkerKnees.KNEE_STRUCTURE);
        assertTrue(knees.downed());
        assertEquals(0f, knees.speedShare(), "downed, it cannot walk");
        assertFalse(knees.grips(3), "downed, no leg grips");
    }

    @Test
    void aRearLegLostStillLetsItBrace() {
        WalkerKnees knees = new WalkerKnees();
        knees.hit(180f, false, false, 0f, WalkerKnees.KNEE_STRUCTURE);
        assertEquals(1, knees.lostCount());
        assertTrue(knees.canBrace());
    }

    @Test
    void theKneeRollIsFixedByWhoStruckWhomAndInRange() {
        float a = DerelictSecurityDirector.roll(7L, 9L, 3);
        assertEquals(a, DerelictSecurityDirector.roll(7L, 9L, 3));
        for (int n = 0; n < 200; n++) {
            float r = DerelictSecurityDirector.roll(7L, 9L, n);
            assertTrue(r >= 0f && r < 1f, "roll " + r);
        }
    }
}
