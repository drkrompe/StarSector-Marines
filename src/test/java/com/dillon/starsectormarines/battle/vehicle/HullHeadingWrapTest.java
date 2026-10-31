package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hull's heading can come out of a Reeds-Shepp manoeuvre a whole turn out (The Burn, seed 202: a breakthrough tank
 * at 607.86 degrees). The carrot dead ahead of it must still read as dead ahead, not a full turn off, or pursuit backs
 * the hull away from its route.
 */
class HullHeadingWrapTest {

    @Test
    void aCarrotDeadAheadOfAHeadingATurnOutIsAhead() {
        // Facing 607.86 is 247.86: east-south-east. The carrot it reversed from bore -112.14, dead ahead.
        assertEquals(0f, VehicleController.offNoseDegrees(-112.14f, 607.86f), 0.01f);
        assertEquals(0f, VehicleController.offNoseDegrees(-112.14f, 247.86f), 0.01f);
        assertEquals(180f, Math.abs(VehicleController.offNoseDegrees(67.86f, 607.86f)), 0.01f);
    }

    @Test
    void aHeadingATurnOutFoldsBackAndOneInRangeIsLeftAlone() {
        assertEquals(247.86f, GroundBody.foldFacing(607.86f), 0.01f);
        assertEquals(-207.2f, GroundBody.foldFacing(-927.2f), 0.01f);
        assertEquals(300f, GroundBody.foldFacing(300f), 0f);
        assertEquals(-300f, GroundBody.foldFacing(-300f), 0f);
    }
}
