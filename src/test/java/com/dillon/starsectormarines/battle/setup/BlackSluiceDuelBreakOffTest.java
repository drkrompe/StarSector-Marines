package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When Voss orders the run: inside its window, the same for the same order tick, and spread across order ticks. */
class BlackSluiceDuelBreakOffTest {

    @Test
    void theRunIsOrderedInsideItsWindowAndTheSameEveryTime() {
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int tick = 0; tick < 20_000; tick += 7) {
            float s = BlackSluiceDuel.breakOffSeconds(tick);
            assertTrue(s >= BlackSluiceDuel.BREAK_OFF_EARLIEST_SECONDS && s <= BlackSluiceDuel.BREAK_OFF_LATEST_SECONDS,
                    "tick " + tick + " gave " + s);
            assertEquals(s, BlackSluiceDuel.breakOffSeconds(tick), 0f);
            least = Math.min(least, s);
            most = Math.max(most, s);
        }
        float window = BlackSluiceDuel.BREAK_OFF_LATEST_SECONDS - BlackSluiceDuel.BREAK_OFF_EARLIEST_SECONDS;
        assertTrue(least < BlackSluiceDuel.BREAK_OFF_EARLIEST_SECONDS + 0.05f * window, "reaches the early end: " + least);
        assertTrue(most > BlackSluiceDuel.BREAK_OFF_LATEST_SECONDS - 0.05f * window, "reaches the late end: " + most);
    }
}
