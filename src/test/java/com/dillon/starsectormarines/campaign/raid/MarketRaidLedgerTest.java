package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketRaidLedgerTest {

    @Test
    void aFoughtRaidIsTakenOnce() {
        CampaignState state = new CampaignState();
        int serial = MarketRaidLedger.issue(state);

        assertTrue(MarketRaidLedger.record(state, serial, true));
        assertEquals(MarketRaidLedger.Pending.WON, MarketRaidLedger.take(state, serial));
        assertEquals(MarketRaidLedger.Pending.NONE, MarketRaidLedger.take(state, serial));
    }

    @Test
    void aLostRaidIsTakenAsLost() {
        CampaignState state = new CampaignState();
        int serial = MarketRaidLedger.issue(state);

        MarketRaidLedger.record(state, serial, false);
        assertEquals(MarketRaidLedger.Pending.LOST, MarketRaidLedger.take(state, serial));
    }

    @Test
    void aBriefingBackedOutOfCommitsNothing() {
        CampaignState state = new CampaignState();
        int serial = MarketRaidLedger.issue(state);

        assertEquals(MarketRaidLedger.Pending.NONE, MarketRaidLedger.take(state, serial));
    }

    @Test
    void aReplayedOrStaleResultRecordsNothing() {
        CampaignState state = new CampaignState();
        int first = MarketRaidLedger.issue(state);
        int second = MarketRaidLedger.issue(state);

        assertTrue(MarketRaidLedger.record(state, second, true));
        assertFalse(MarketRaidLedger.record(state, second, false), "a replayed result");
        assertFalse(MarketRaidLedger.record(state, first, false), "an older briefing's result");
        assertFalse(MarketRaidLedger.record(state, second + 1, true), "a serial never issued");
        assertEquals(MarketRaidLedger.Pending.NONE, MarketRaidLedger.take(state, first));
        assertEquals(MarketRaidLedger.Pending.WON, MarketRaidLedger.take(state, second));
    }

    @Test
    void theMissionKeyCarriesTheSerialAndAMarketIdWithColons() {
        String id = MarketRaidMissionKey.encode(7, "mod:market");
        assertEquals(new MarketRaidMissionKey(7, "mod:market"), MarketRaidMissionKey.parse(id));
        assertEquals(null, MarketRaidMissionKey.parse("nex-invasion:7:jangala"));
    }
}
