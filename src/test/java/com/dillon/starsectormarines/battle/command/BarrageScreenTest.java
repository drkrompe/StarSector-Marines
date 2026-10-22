package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrageScreenTest {

    /** Advancing east: lines at x=130 (ticks 1200-2700) and x=155 (2700-3900), standing 18 short. */
    private final BarrageScreen screen = new BarrageScreen(false, 1,
            new int[]{130, 155}, new int[]{1200, 2700}, new int[]{2700, 3900}, 18);

    @Test
    void theCompanyFormsShortOfTheFirstLineAndComesUpAsEachLifts() {
        assertEquals(112, screen.holdAlong(0), "before the guns open it forms short of the first line");
        assertEquals(112, screen.holdAlong(2699));
        assertEquals(137, screen.holdAlong(2700), "the first line lifts and the company comes up behind the second");
        assertEquals(Integer.MIN_VALUE, screen.holdAlong(3900), "the barrage done, nothing holds it");
    }

    @Test
    void groundPastTheHoldIsTheGunsUntilTheyAreDone() {
        assertTrue(screen.beyond(150, 1500));
        assertFalse(screen.beyond(100, 1500));
        assertFalse(screen.beyond(150, 4000));
    }

    @Test
    void aCompanyWithFormUpGroundWaitsThereUntilItsTimeIsUp() {
        BarrageScreen held = new BarrageScreen(false, 1, new int[]{130, 155}, new int[]{1200, 2700},
                new int[]{2700, 3900}, 18, 60, 2700);
        assertEquals(60, held.holdAlong(1500));
        assertEquals(137, held.holdAlong(2700));
    }

    @Test
    void aWestwardAdvanceHoldsOnTheOtherSide() {
        BarrageScreen west = new BarrageScreen(false, -1, new int[]{200}, new int[]{0}, new int[]{100}, 10);
        assertEquals(210, west.holdAlong(0));
        assertTrue(west.beyond(205, 0));
        assertFalse(west.beyond(215, 0));
    }
}
