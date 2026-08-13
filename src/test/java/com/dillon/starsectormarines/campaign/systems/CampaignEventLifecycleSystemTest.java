package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;
import com.dillon.starsectormarines.campaign.DefectorAsylumOutcome;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignEventLifecycleSystemTest {

    @Test
    void pendingChoiceExpiresAfterDeadlineButNotOnDeadline() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long eventId = CivilianRescueEvent.prepare(
                state, 1L, market, 20, 21, 10, 5, 100);
        int row = state.eventIndex(eventId);
        CampaignEventLifecycleSystem system = new CampaignEventLifecycleSystem();

        system.tick(state, 21);
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(state.eventState[row]));

        system.tick(state, 22);
        assertEquals(CampaignEventState.EXPIRED,
                CampaignEventState.fromByte(state.eventState[row]));
        assertEquals(-1, state.eventDecisionTick[row]);
        assertEquals(-1, state.eventResolvedTick[row]);
    }

    @Test
    void committedDefectorOpensFollowupAndDeadlineDefaultsToProtection() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long actor = house(state, market, "Actor");
        long target = house(state, market, "Target");
        long chain = state.addAutonomousChain(actor, target, market, 7,
                (byte) 0, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 45, (byte) 32, 1);
        long event = DefectorAsylumEvent.prepare(state, 2L, chain,
                actor, target, market, 20, 23, 10, 5, 20_000);
        int row = state.eventIndex(event);
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        state.eventDecisionTick[row] = 20;
        state.eventFollowupTick[row] = 30;
        state.eventFollowupDeadlineTick[row] = 33;
        CampaignEventLifecycleSystem system = new CampaignEventLifecycleSystem();

        system.tick(state, 29);
        assertEquals(CampaignEventState.COMMITTED,
                CampaignEventState.fromByte(state.eventState[row]));
        system.tick(state, 30);
        assertEquals(CampaignEventState.PENDING_FOLLOWUP,
                CampaignEventState.fromByte(state.eventState[row]));
        system.tick(state, 33);
        assertEquals(CampaignEventState.PENDING_FOLLOWUP,
                CampaignEventState.fromByte(state.eventState[row]));
        system.tick(state, 34);

        assertEquals(CampaignEventState.RESOLVED,
                CampaignEventState.fromByte(state.eventState[row]));
        assertEquals(DefectorAsylumOutcome.PROTECTED,
                DefectorAsylumOutcome.fromByte(state.eventDefectorOutcome[row]));
        assertEquals(34, state.eventResolvedTick[row]);
        system.tick(state, 35);
        assertEquals(34, state.eventResolvedTick[row]);
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
