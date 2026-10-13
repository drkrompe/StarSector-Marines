package com.dillon.starsectormarines.battle.decision.goap.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a route-threat commit that has no firing cell gives way to the order instead of planting. */
class StaleCommitWalksOnTest {

    @Test
    void anOrderedSquadWalksOnWhenNothingHasBeenSeenRecently() {
        // The live defect: nine marines stood still for a belief's whole lifetime with the player clicking at them.
        assertTrue(AbstractZoneAction.staleCommitWalksOn(true, false, false));
    }

    @Test
    void freshContactStillPlants() {
        assertFalse(AbstractZoneAction.staleCommitWalksOn(true, false, true));
    }

    @Test
    void aHardHoldStillPlants() {
        assertFalse(AbstractZoneAction.staleCommitWalksOn(true, true, false));
    }

    @Test
    void anAutonomousAdvanceKeepsPlantingAgainstWhatItRemembers() {
        assertFalse(AbstractZoneAction.staleCommitWalksOn(false, false, false));
    }

    @Test
    void theSwitchRestoresTheFreeze() {
        AbstractZoneAction.setStaleCommitFallThroughForEvidence(false);
        try {
            assertFalse(AbstractZoneAction.staleCommitWalksOn(true, false, false));
        } finally {
            AbstractZoneAction.setStaleCommitFallThroughForEvidence(true);
        }
    }
}
