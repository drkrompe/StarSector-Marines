package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsExpeditionLedgerTest {

    @Test
    void aWonExpeditionIsTakenOnce() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);

        assertTrue(RuinsExpeditionLedger.record(state, serial, RuinsExpeditionLedger.Pending.WON));
        assertEquals(RuinsExpeditionLedger.Pending.WON, RuinsExpeditionLedger.take(state, serial));
        assertEquals(RuinsExpeditionLedger.Pending.NONE, RuinsExpeditionLedger.take(state, serial));
    }

    @Test
    void aLostExpeditionIsTakenAsLost() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);

        RuinsExpeditionLedger.record(state, serial, RuinsExpeditionLedger.Pending.LOST);
        assertEquals(RuinsExpeditionLedger.Pending.LOST, RuinsExpeditionLedger.take(state, serial));
    }

    @Test
    void aPulledOutExpeditionIsTakenAsPulledOutOnce() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);

        assertTrue(RuinsExpeditionLedger.record(state, serial, RuinsExpeditionLedger.Pending.PULLED_OUT));
        assertEquals(RuinsExpeditionLedger.Pending.PULLED_OUT, RuinsExpeditionLedger.take(state, serial));
        assertEquals(RuinsExpeditionLedger.Pending.NONE, RuinsExpeditionLedger.take(state, serial));
    }

    @Test
    void aBriefingBackedOutOfCommitsNothing() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);

        assertEquals(RuinsExpeditionLedger.Pending.NONE, RuinsExpeditionLedger.take(state, serial));
    }

    @Test
    void aReplayedOrStaleResultRecordsNothing() {
        CampaignState state = new CampaignState();
        int first = RuinsExpeditionLedger.issue(state);
        int second = RuinsExpeditionLedger.issue(state);

        assertTrue(RuinsExpeditionLedger.record(state, second, RuinsExpeditionLedger.Pending.WON));
        assertFalse(RuinsExpeditionLedger.record(state, second, RuinsExpeditionLedger.Pending.LOST), "a replayed result");
        assertFalse(RuinsExpeditionLedger.record(state, first, RuinsExpeditionLedger.Pending.LOST), "an older briefing's result");
        assertFalse(RuinsExpeditionLedger.record(state, second + 1, RuinsExpeditionLedger.Pending.WON), "a serial never issued");
        assertEquals(RuinsExpeditionLedger.Pending.NONE, RuinsExpeditionLedger.take(state, first));
        assertEquals(RuinsExpeditionLedger.Pending.WON, RuinsExpeditionLedger.take(state, second));
    }

    @Test
    void theMissionKeyCarriesTheSerialAndAMarketIdWithColons() {
        String id = RuinsExpeditionMissionKey.encode(7, "mod:market");
        assertEquals(new RuinsExpeditionMissionKey(7, "mod:market"), RuinsExpeditionMissionKey.parse(id));
        assertNull(RuinsExpeditionMissionKey.parse("market-raid:7:jangala"));
    }
}
