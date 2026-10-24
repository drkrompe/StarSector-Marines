package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.allied.EscortPace.Pace;
import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.battle.command.allied.EscortPace.ALONGSIDE;
import static com.dillon.starsectormarines.battle.command.allied.EscortPace.ASSIGN_TICKS;
import static com.dillon.starsectormarines.battle.command.allied.EscortPace.FALLEN_BEHIND;
import static com.dillon.starsectormarines.battle.command.allied.EscortPace.HOLD_PATIENCE_TICKS;
import static com.dillon.starsectormarines.battle.command.allied.EscortPace.WAIT_PATIENCE_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A road hull and its escort move together: wait for it, keep to it, hold for it, and never wait for ever. */
class EscortPaceTest {

    private static final float FAR = 100f;

    @Test
    void aHullWaitsWhereItWasSetDownUntilItsEscortIsAlongside() {
        assertEquals(Pace.WAIT, EscortPace.rule(false, false, true, FAR, 0));
        assertEquals(Pace.WAIT, EscortPace.rule(false, false, true, ALONGSIDE + 1, WAIT_PATIENCE_TICKS - 1));
        assertEquals(Pace.GO, EscortPace.rule(false, false, true, ALONGSIDE, 5));
    }

    @Test
    void aHullWithNoEscortFreeSetsOutAloneOnceOneHadTimeToBeAssigned() {
        assertEquals(Pace.WAIT, EscortPace.rule(false, false, false, 0f, ASSIGN_TICKS - 1));
        assertEquals(Pace.ALONE, EscortPace.rule(false, false, false, 0f, ASSIGN_TICKS));
    }

    @Test
    void anEscortThatNeverComesIsGivenUpOn() {
        assertEquals(Pace.ALONE, EscortPace.rule(false, false, true, FAR, WAIT_PATIENCE_TICKS));
        assertEquals(Pace.ALONE, EscortPace.rule(true, true, true, FAR, HOLD_PATIENCE_TICKS));
    }

    @Test
    void aMovingHullHoldsWhenItsEscortFallsBehindAndGoesOnWhenItIsAlongsideAgain() {
        assertEquals(Pace.GO, EscortPace.rule(true, false, true, FALLEN_BEHIND, 0));
        assertEquals(Pace.HOLD, EscortPace.rule(true, false, true, FALLEN_BEHIND + 1, 0));
        // Between the two it keeps holding: the escort must close up, not merely stop falling further behind.
        assertEquals(Pace.HOLD, EscortPace.rule(true, true, true, ALONGSIDE + 1, 10));
        assertEquals(Pace.GO, EscortPace.rule(true, true, true, ALONGSIDE, 10));
    }

    @Test
    void aHullWhoseEscortIsGoneDrivesOn() {
        assertEquals(Pace.GO, EscortPace.rule(true, false, false, 0f, 0));
        assertEquals(Pace.GO, EscortPace.rule(true, true, false, 0f, 0));
    }

    @Test
    void thePairKeepsToTheSlowerOnesPace() {
        assertEquals(3.4f * EscortPace.PACE, EscortPace.cap(3.4f, 4.0f), 1e-6f);
        assertEquals(2.4f * EscortPace.PACE, EscortPace.cap(2.4f, 4.0f), 1e-6f);
    }
}
