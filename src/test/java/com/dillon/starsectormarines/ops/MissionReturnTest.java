package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionReturnTest {

    @Test
    void theBoardCallsOnlyWhileThisPlanetHasWork() {
        assertFalse(MissionReturn.MISSIONS.calls(false));
        assertTrue(MissionReturn.MISSIONS.calls(true));
    }

    @Test
    void aMissionInProgressAlwaysCalls() {
        assertTrue(MissionReturn.BRIEFING.calls(false));
    }
}
