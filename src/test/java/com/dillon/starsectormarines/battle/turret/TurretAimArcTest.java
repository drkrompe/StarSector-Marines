package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretAimArcTest {

    @Test
    void aMountWithNoArcSetReachesEveryBearing() {
        TurretAim.State s = new TurretAim.State();
        for (float bearing = -180f; bearing <= 180f; bearing += 30f) {
            assertTrue(TurretAim.withinArc(s, bearing));
        }
    }

    @Test
    void aLimitedArcAdmitsOnlyBearingsNearItsCentre() {
        TurretAim.State s = new TurretAim.State();
        s.arcCentreDeg = 90f;
        s.arcHalfWidthDeg = 75f;
        assertTrue(TurretAim.withinArc(s, 90f));
        assertTrue(TurretAim.withinArc(s, 20f));
        assertTrue(TurretAim.withinArc(s, 160f));
        assertFalse(TurretAim.withinArc(s, 0f), "dead ahead is outside a port beam gun's arc");
        assertFalse(TurretAim.withinArc(s, -90f), "the far beam is behind the hull");
    }

    @Test
    void theArcWrapsThroughTheSeamAtMinusAndPlus180() {
        TurretAim.State s = new TurretAim.State();
        s.arcCentreDeg = 170f;
        s.arcHalfWidthDeg = 30f;
        assertTrue(TurretAim.withinArc(s, -170f), "-170 is 20 degrees from 170 across the seam");
        assertFalse(TurretAim.withinArc(s, -120f));
    }
}
