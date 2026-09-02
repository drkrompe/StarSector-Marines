package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.RaidStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityThreatFilterTest {

    @Test
    void onlyALiveRaidOnAnUnsettledPlayerColonyIsFightable() {
        assertTrue(PolityThreatFilter.fightable(true, RaidStatus.LIVE, false));
    }

    /** Somebody else's world is somebody else's problem; a Garrison contract is the other path. */
    @Test
    void aMarketThePlayerDoesNotOwnIsNeverFightable() {
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.LIVE, false));
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.LANDED, false));
        assertFalse(PolityThreatFilter.fightable(false, RaidStatus.REPELLED, false));
    }

    /** A landed raid vanilla already resolved, and a repelled one, are both over. */
    @Test
    void onlyALiveRaidIsStillOffered() {
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LANDED, false));
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.REPELLED, false));
    }

    @Test
    void aRaidAlreadyFoughtIsNotOfferedAgain() {
        assertFalse(PolityThreatFilter.fightable(true, RaidStatus.LIVE, true));
    }

    @Test
    void anUnknownStatusIsNotFightable() {
        assertFalse(PolityThreatFilter.fightable(true, null, false));
    }
}
