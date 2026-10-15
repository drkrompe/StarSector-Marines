package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preference, Nexerelin's presence and the entry point choose where an Invade goes. */
class InvasionRouteTest {

    @Test
    void thePreferenceChoosesForASupportedInvasionUnderNexerelin() {
        assertEquals(InvasionRoute.FORK, InvasionRoute.choose(GroundCombatPreference.ASK, true, true));
        assertEquals(InvasionRoute.MARINE_OPERATIONS,
                InvasionRoute.choose(GroundCombatPreference.MARINE_OPERATIONS, true, true));
        assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(GroundCombatPreference.NEXERELIN, true, true));
        assertEquals(InvasionRoute.FORK, InvasionRoute.choose(null, true, true), "no preference asks");
    }

    @Test
    void anUnsupportedEntryPointOrNoNexerelinIsAlwaysNexerelins() {
        for (GroundCombatPreference preference : GroundCombatPreference.values()) {
            assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(preference, true, false));
            assertEquals(InvasionRoute.NEXERELIN, InvasionRoute.choose(preference, false, true));
        }
    }

    @Test
    void aSaveStartsAtAskAndKeepsWhatIsChosen() {
        CampaignState state = new CampaignState();
        assertEquals(GroundCombatPreference.ASK, GroundCombatPreferenceLedger.read(state));
        GroundCombatPreferenceLedger.write(state, GroundCombatPreference.NEXERELIN);
        assertEquals(GroundCombatPreference.NEXERELIN, GroundCombatPreferenceLedger.read(state));
        state.groundCombatPreference = 99;
        assertEquals(GroundCombatPreference.ASK, GroundCombatPreferenceLedger.read(state), "unreadable asks");
        assertEquals(GroundCombatPreference.ASK, GroundCombatPreferenceLedger.read(null));
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
