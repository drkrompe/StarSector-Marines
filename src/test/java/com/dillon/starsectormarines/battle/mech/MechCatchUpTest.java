package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An attached follower runs once it has fallen well behind its slot, and keeps on until it has closed on it. */
class MechCatchUpTest {

    @Test
    void aFollowerNearItsSlotWalks() {
        assertFalse(MechRunSystem.catchesUp(false, MechRunSystem.CATCH_UP_CELLS));
        assertFalse(MechRunSystem.catchesUp(false, 0f));
    }

    @Test
    void aFollowerPastTheThresholdRuns() {
        assertTrue(MechRunSystem.catchesUp(false, MechRunSystem.CATCH_UP_CELLS + .1f));
    }

    @Test
    void aRunningFollowerKeepsOnUntilSettled() {
        assertTrue(MechRunSystem.catchesUp(true, MechRunSystem.CATCH_UP_CELLS - 1f));
        assertTrue(MechRunSystem.catchesUp(true, MechRunSystem.SETTLED_CELLS + .1f));
        assertFalse(MechRunSystem.catchesUp(true, MechRunSystem.SETTLED_CELLS));
    }
}
