package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinOutlineTest {

    @Test
    void standingRunsAreShortAndNeverMeetRoundTheLoop() {
        for (long seed = 0; seed < 200; seed++) {
            char[] fate = WastelandRubbleFiller.outlineFates(36, new Random(seed));
            int run = 0, standing = 0;
            for (int i = 0; i < fate.length; i++) {
                run = fate[i] == 'W' ? run + 1 : 0;
                if (fate[i] == 'W') standing++;
                assertTrue(run <= WastelandRubbleFiller.MAX_STANDING_RUN, "seed " + seed + " ran " + run);
            }
            assertTrue(!(fate[0] == 'W' && fate[fate.length - 1] == 'W'), "seed " + seed + " closes the loop");
            assertTrue(standing < fate.length, "seed " + seed + " left nothing open");
        }
    }

    @Test
    void theOutlineGoesOnceRoundTheRectangle() {
        // A 6x5 rectangle has 2*6 + 2*3 cells on its outline.
        assertEquals(18, WastelandRubbleFiller.outline(0, 0, 5, 4).size());
    }
}
