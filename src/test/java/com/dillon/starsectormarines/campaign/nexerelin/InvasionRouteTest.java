package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fork setting, Nexerelin's presence and the entry point choose where an Invade goes. */
class InvasionRouteTest {

    @Test
    void onlyASupportedInvasionUnderNexerelinWithTheForkOnAsks() {
        assertEquals(InvasionRoute.FORK, InvasionRoute.choose(true, true, true));
        assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(false, true, true));
        assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(true, false, true));
        assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(true, true, false));
    }

    @Test
    void aSaveAsksUntilTheSettingIsTurnedOff() {
        CampaignState state = new CampaignState();
        assertTrue(InvasionForkSetting.asks(state));
        InvasionForkSetting.setAsks(state, false);
        assertFalse(InvasionForkSetting.asks(state));
        state.groundCombatPreference = 1;
        assertTrue(InvasionForkSetting.asks(state), "a retired always-fight answer asks");
        assertTrue(InvasionForkSetting.asks(null));
    }

    @Test
    void anInvasionSettlesOnceAndAnOlderOneNever() {
        CampaignState state = new CampaignState();
        int backedOutOf = NexInvasionLedger.issue(state);
        int fought = NexInvasionLedger.issue(state);
        assertFalse(NexInvasionLedger.claim(state, fought + 1), "a serial never issued cannot settle");
        assertTrue(NexInvasionLedger.claim(state, fought));
        assertFalse(NexInvasionLedger.claim(state, fought), "replaying Results settles nothing");
        assertFalse(NexInvasionLedger.claim(state, backedOutOf), "an older briefing settles nothing");
        assertFalse(NexInvasionLedger.claim(null, 1));
    }

    @Test
    void theMissionKeyCarriesTheSerialAndMarket() {
        assertEquals(new NexInvasionMissionKey(7, "jangala"),
                NexInvasionMissionKey.parse(NexInvasionMissionKey.encode(7, "jangala")));
        assertEquals(new NexInvasionMissionKey(3, "mod:market"),
                NexInvasionMissionKey.parse(NexInvasionMissionKey.encode(3, "mod:market")));
        assertEquals(null, NexInvasionMissionKey.parse("polity-defence:1:2"));
        assertEquals(null, NexInvasionMissionKey.parse("nex-invasion:x:jangala"));
        assertEquals(null, NexInvasionMissionKey.parse("nex-invasion:4:"));
        assertEquals(null, NexInvasionMissionKey.parse(null));
    }
}
