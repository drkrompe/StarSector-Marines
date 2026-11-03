package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleControllerDriftReplanTest {

    private static final float PAST_BAND = VehicleController.REPLAN_DRIFT_CELLS + 1f;

    @Test
    void leavingTheBandForcesAReplan() {
        assertTrue(VehicleController.driftForcesReplan(PAST_BAND, false));
    }

    @Test
    void aPlanMadeFromOutsideTheBandIsNotAskedForAgain() {
        assertFalse(VehicleController.driftForcesReplan(PAST_BAND, true));
    }

    @Test
    void insideTheBandForcesNothing() {
        assertFalse(VehicleController.driftForcesReplan(VehicleController.REPLAN_DRIFT_CELLS, false));
    }
}
