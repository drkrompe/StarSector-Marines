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
    void dyingCreepGreysToTheDeadTone() {
        float[] out = new float[3], living = {1f, .5f, 0f}, dead = {.2f, .2f, .2f};
        CreepClimb.tone(living, dead, 255, 1f, out);
        assertEquals(1f, out[0], 1e-6f);
        CreepClimb.tone(living, dead, 0, 1f, out);
        assertEquals(.2f, out[0], 1e-6f);
        assertEquals(.2f, out[2], 1e-6f);
    }

    @Test
    void nothingIsPaintedAboveWhatOfTheWallStands() {
        float[] highest = {0f};
        int[] quads = {0};
        CreepClimb.paint(255, 255, 3f, .2f, 77, CreepPalette.active(), 1f, true, (t0, t1, z0, z1, r, g, b) -> {
            highest[0] = Math.max(highest[0], z1);
            quads[0]++;
            assertTrue(t0 >= 0f && t1 <= 1f && z0 >= 0f, "on the face");
        });
        assertTrue(quads[0] > 0, "an old creep paints the face");
        assertTrue(highest[0] <= .2f + 1e-6f, "a cut-away stub keeps its creep under its top: " + highest[0]);
    }
}
