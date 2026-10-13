package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealRoadExitTest {
    @Test void onlyMauledLivingElementsChooseWithdrawal() {
        assertFalse(WaterUnderSealRoadExit.withdraws(0));
        assertTrue(WaterUnderSealRoadExit.withdraws(1));
        assertTrue(WaterUnderSealRoadExit.withdraws(2));
        assertFalse(WaterUnderSealRoadExit.withdraws(3));
        assertFalse(WaterUnderSealRoadExit.withdraws(6));
    }
    @Test void LossOrSeparationAloneCannotCountAsPhysicalDeparture() {
        assertFalse(WaterUnderSealRoadExit.atExit(151.28f,101.30f));
        assertFalse(WaterUnderSealRoadExit.atExit(168.9f,88));
        assertFalse(WaterUnderSealRoadExit.atExit(173,100));
        assertTrue(WaterUnderSealRoadExit.atExit(173.5f,88.5f));
        assertFalse(WaterUnderSealRoadExit.atExit(Float.NaN,88));
        assertFalse(WaterUnderSealRoadExit.atExit(176,88));
    }
}
