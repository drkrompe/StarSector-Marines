package com.dillon.starsectormarines.render;

import com.dillon.starsectormarines.render.FullscreenWindowRepair.Action;
import com.dillon.starsectormarines.render.FullscreenWindowRepair.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When the fullscreen window is resized back to the display mode, and when it is left alone. */
class FullscreenWindowRepairPolicyTest {

    @Test
    void matchingWindowIsLeftAlone() {
        assertEquals(Action.NONE, new Policy().observe(true, 2560, 1440, 2560, 1440));
    }

    @Test
    void unfocusedOrWindowedIsLeftAlone() {
        assertEquals(Action.NONE, new Policy().observe(false, 3840, 2160, 2560, 1440));
    }

    @Test
    void mismatchIsRepairedABoundedNumberOfTimesThenGivenUp() {
        Policy policy = new Policy();
        for (int i = 0; i < FullscreenWindowRepair.MAX_ATTEMPTS; i++) {
            assertEquals(Action.REPAIR, policy.observe(true, 3840, 2160, 2560, 1440));
        }
        assertEquals(Action.GIVE_UP, policy.observe(true, 3840, 2160, 2560, 1440));
        assertEquals(Action.NONE, policy.observe(true, 3840, 2160, 2560, 1440));
    }

    @Test
    void losingFocusRefillsTheBudget() {
        Policy policy = new Policy();
        for (int i = 0; i <= FullscreenWindowRepair.MAX_ATTEMPTS; i++) {
            policy.observe(true, 3840, 2160, 2560, 1440);
        }
        policy.observe(false, 0, 0, 0, 0);
        assertEquals(Action.REPAIR, policy.observe(true, 3840, 2160, 2560, 1440));
    }
}
