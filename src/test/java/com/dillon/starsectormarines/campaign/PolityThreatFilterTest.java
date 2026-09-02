package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.RaidStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityThreatFilterTest {

    @Test
    void onlyALiveRaidOnAnUnsettledUnpostedPlayerColonyIsFightable() {
        assertTrue(PolityThreatFilter.fightable(true, RaidStatus.LIVE, false, false));
    }

    /** Somebody else's world is somebody else's problem; a Garrison contract is the other path. */
    @Test
    void aMarketThePlayerDoesNotOwnIsNeverFightable() {
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.LIVE, false, false));
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.LANDED, false, false));
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.REPELLED, false, false));
    }

    /** A landed raid vanilla already resolved, and a repelled one, are both over. */
    @Test
    void onlyALiveRaidIsStillOffered() {
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LANDED, false, false));
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.REPELLED, false, false));
    }

    @Test
    void aRaidAlreadyFoughtIsNotOfferedAgain() {
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LIVE, true, false));
    }

    /** The posted detachment answers it through the ordinary stationing response. */
    @Test
    void aRaidAPostingAlreadyAnswersIsNotOfferedSeparately() {
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LIVE, false, true));
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LIVE, true, true));
    }

    @Test
    void anUnknownStatusIsNotFightable() {
        assertFalse(PolityThreatFilter.fightable(true, null, false, false));
    }
}
