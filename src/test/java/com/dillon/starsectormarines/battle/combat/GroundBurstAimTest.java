package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GroundBurstAimTest {

    @Test
    void insideRangeTheDiskMatchesPointAimAndPastItGrowsFasterThanDistance() {
        float atRange = GroundBurstAim.errorRadius(0.55f, 0f, 18f, 18f);
        assertEquals(PointAim.BASE_ERROR_CELLS + PointAim.ACCURACY_ERROR_CELLS * 0.45f, atRange, 1e-5f);
        assertEquals(atRange * 0.5f, GroundBurstAim.errorRadius(0.55f, 0f, 18f, 9f), 1e-5f);
        float doubled = GroundBurstAim.errorRadius(0.55f, 0f, 18f, 36f);
        assertEquals(atRange * 2f * (1f + GroundBurstAim.OVER_RANGE_GROWTH), doubled, 1e-4f);
        assertTrue(doubled > atRange * 2f, "beyond range costs more than distance alone");
    }

    @Test
    void samplesStayInsideTheirDisk() {
        Random rng = new Random(7L);
        float radius = GroundBurstAim.errorRadius(0.4f, 0.9f, 20f, 30f);
        for (int i = 0; i < 200; i++) {
            GroundBurstAim.Offset o = GroundBurstAim.sample(0.4f, 0.9f, 20f, 30f, rng);
            assertTrue(Math.hypot(o.dx(), o.dy()) <= radius + 1e-4f);
        }
    }
}
