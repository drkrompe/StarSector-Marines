package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayRunToTest {

    private static final String NORMAL = "battle-time-normal";

    @Test
    void runsThenPausesShortAndStepsOntoTheExactTick() {
        PlayRunTo run = new PlayRunTo(100, NORMAL);
        assertEquals(NORMAL, run.frame(0, false, PlayRunTo.PAUSE, null).press());
        // The press has not shown yet: not taken for the player's hand.
        assertNull(run.frame(0, false, PlayRunTo.PAUSE, null).press());
        assertNull(run.frame(1, false, NORMAL, null).press());
        assertEquals(PlayRunTo.PAUSE, run.frame(92, false, NORMAL, null).press());
        assertEquals(PlayRunTo.State.RUNNING, run.state());
        assertEquals(8, run.frame(92, false, PlayRunTo.PAUSE, null).steps());
        assertEquals(PlayRunTo.State.ARRIVED, run.state());
    }

    @Test
    void aTargetWithinReachOfAPausedBattleIsSteppedAtOnce() {
        PlayRunTo run = new PlayRunTo(11, NORMAL);
        assertEquals(new PlayRunTo.Next(null, 1), run.frame(10, false, PlayRunTo.PAUSE, null));
        assertEquals(PlayRunTo.State.ARRIVED, run.state());
    }

    @Test
    void thePlayerChangingSpeedEndsTheRunWhereItStands() {
        PlayRunTo run = new PlayRunTo(1000, NORMAL);
        run.frame(0, false, NORMAL, null);
        run.frame(1, false, NORMAL, null);
        assertEquals(PlayRunTo.Next.NOTHING, run.frame(2, false, PlayRunTo.PAUSE, null));
        assertEquals(PlayRunTo.Next.NOTHING, run.frame(2, false, PlayRunTo.PAUSE, null));
        assertEquals(PlayRunTo.Next.NOTHING, run.frame(2, false, PlayRunTo.PAUSE, null));
        assertEquals(PlayRunTo.State.CANCELLED, run.state());
    }

    @Test
    void aBeatStopsTheRun() {
        PlayRunTo run = new PlayRunTo(Integer.MAX_VALUE, NORMAL);
        run.frame(0, false, NORMAL, null);
        assertEquals(PlayRunTo.PAUSE, run.frame(50, false, NORMAL, "final_run at tick 49").press());
        assertEquals(PlayRunTo.State.BEAT, run.state());
    }

    @Test
    void theBattleEndingStopsTheRun() {
        PlayRunTo run = new PlayRunTo(1000, NORMAL);
        assertEquals(PlayRunTo.Next.NOTHING, run.frame(5, true, null, null));
        assertEquals(PlayRunTo.State.COMPLETE, run.state());
    }
}
