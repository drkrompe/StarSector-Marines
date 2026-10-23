package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechSectionFxSystemTest {

    @Test
    void shoulderAnchorsSitEitherSideAndTurnWithTheTorso() {
        assertTrue(MechSectionFxSystem.anchorX(MechSection.LEFT_SHOULDER, 0f, 2f) < 0f);
        assertTrue(MechSectionFxSystem.anchorX(MechSection.RIGHT_SHOULDER, 0f, 2f) > 0f);
        assertEquals(0f, MechSectionFxSystem.anchorX(MechSection.LEGS, 37f, 2f), 1e-6f);
        assertEquals(0f, MechSectionFxSystem.anchorY(MechSection.LEGS, 37f, 2f), 1e-6f);
        // A quarter turn carries the left shoulder from -x onto -y.
        assertEquals(0f, MechSectionFxSystem.anchorX(MechSection.LEFT_SHOULDER, 90f, 2f), 1e-5f);
        assertTrue(MechSectionFxSystem.anchorY(MechSection.LEFT_SHOULDER, 90f, 2f) < 0f);
    }
}
