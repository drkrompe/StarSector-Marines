package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mount held to an arc fires only through it; one with no arc fires all round. */
class TurretAimArcTest {

    @Test
    void anArcedMountTakesOnlyBearingsInsideItsArc() {
        TurretAim.State stern = new TurretAim.State();
        stern.arcCenterDeg = 180f;
        stern.arcHalfDeg = 105f;

        assertTrue(stern.inArc(180f));
        assertTrue(stern.inArc(-80f), "the arc wraps through 180");
        assertFalse(stern.inArc(0f), "dead ahead is outside a stern gun's arc");
        assertTrue(new TurretAim.State().inArc(0f), "no arc is all round");
    }
}
