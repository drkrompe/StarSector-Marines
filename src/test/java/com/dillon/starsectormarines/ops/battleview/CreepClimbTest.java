package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepClimbTest {

    @Test
    void creepClimbsHigherTheLongerItHoldsAWallAndNoHigherThanItsReach() {
        assertEquals(0f, CreepClimb.reach(CreepClimb.FIRST - 1, 8f), 0f, "no creep yet");
        float young = CreepClimb.reach(60, 8f), old = CreepClimb.reach(255, 8f);
        assertTrue(young > 0f && young < old, "young " + young + " below old " + old);
        assertEquals(CreepClimb.CLIMB_METERS / 8f, old, 1e-6f, "full coverage climbs its metres");
        assertEquals(1f, CreepClimb.reach(255, 1.6f), 0f, "a garden wall is overgrown to its top");
    }

    @Test
    void aYoungWallTakesVeinsAndAnOldOneACrust() {
        assertEquals(0f, CreepClimb.crust(CreepClimb.CRUST_FROM), 0f);
        assertEquals(1f, CreepClimb.crust(255), 0f);
        assertTrue(CreepClimb.crust(200) > 0f && CreepClimb.crust(200) < 1f);
    }

    @Test
    void dyingCreepFadesFromTheLivingStripToTheDeadOne() {
        float[] living = {0f}, dead = {0f};
        CreepClimb.Sink sink = (t0, t1, z0, z1, row, u0, u1, v0, v1, light, alpha) -> {
            if (row == CreepClimb.CRUST) living[0] = alpha;
            if (row == CreepClimb.DEAD_CRUST) dead[0] = alpha;
        };
        CreepClimb.paint(255, 255, 3f, 1f, 4, 1f, true, sink);
        assertEquals(1f, living[0], 0f);
        assertEquals(0f, dead[0], 0f, "living creep draws no dead strip");
        living[0] = dead[0] = 0f;
        CreepClimb.paint(255, 0, 3f, 1f, 4, 1f, true, sink);
        assertEquals(0f, living[0], 0f, "dead creep draws no living strip");
        assertEquals(1f, dead[0], 0f);
    }

    @Test
    void nothingIsPaintedAboveWhatOfTheWallStandsAndThePlatesKeepTheGroundsSize() {
        float[] highest = {0f};
        int[] patches = {0};
        CreepClimb.paint(255, 255, 3f, .2f, 77, 1f, true, (t0, t1, z0, z1, row, u0, u1, v0, v1, light, alpha) -> {
            highest[0] = Math.max(highest[0], z1);
            patches[0]++;
            assertTrue(t0 >= 0f && t1 <= 1f && z0 >= 0f, "on the face");
            // As many cells of the strip as the creep stands metres (a cell a metre), so the plates are the ground's.
            assertEquals(z1 * 3f, v1 - v0, 1e-5f, "strip cells shown");
            assertEquals(1f, u1 - u0, 0f, "a cell along");
            assertEquals(CreepClimb.along(77), u0, 0f, "walls' cells side by side run on along the strip");
        });
        assertTrue(patches[0] > 0, "an old creep paints the face");
        assertTrue(highest[0] <= .2f + 1e-6f, "a cut-away stub keeps its creep under its top: " + highest[0]);
    }

    @Test
    void anOldCrustComesOverTheTopFromTheOuterSideOnly() {
        int[] patches = {0};
        CreepClimb.paintTop(CreepClimb.CRUST_FROM, 255, 5, 0f, 0f, 1f, 0f, 0f, -1f, 1f,
                (x0, y0, x1, y1, x2, y2, x3, y3, row, u0, u1, vOut, vIn, light, alpha) -> patches[0]++);
        assertEquals(0, patches[0], "a young claim has not come over the top");
        // A wall's north side (outward -y) at y 0: the crust lies in from it, toward +y, no further than a wall.
        CreepClimb.paintTop(255, 255, 5, 0f, 0f, 1f, 0f, 0f, -1f, 1f,
                (x0, y0, x1, y1, x2, y2, x3, y3, row, u0, u1, vOut, vIn, light, alpha) -> {
                    patches[0]++;
                    for (float y : new float[]{y0, y1, y2, y3}) assertTrue(y >= 0f && y <= 1f, "in from the side: " + y);
                    assertTrue(y2 > y1 && y3 > y0, "inward");
                    assertTrue(vOut > vIn, "the lip toward the middle");
                });
        assertTrue(patches[0] > 0, "an old crust comes over the top");
    }
}
