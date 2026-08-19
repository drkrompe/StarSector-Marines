package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugDefectorAsylumSpawnerTest {

    @Test
    void debugSpawnUsesEligibleDiscoveredSourceAndProductionTerms() {
        Fixture fixture = new Fixture(10, (byte) 2);

        long event = DebugDefectorAsylumSpawner.spawn(fixture.state, 20);

        int row = fixture.state.eventIndex(event);
        assertTrue(row >= 0);
        assertEquals(CampaignEventType.DEFECTOR_ASYLUM,
                CampaignEventType.fromByte(fixture.state.eventType[row]));
        assertTrue(fixture.state.eventTriggerKey[row] >= 1L << 61);
        assertEquals(fixture.chain, fixture.state.eventSourceChainId[row]);
        assertEquals(20, fixture.state.eventCreatedTick[row]);
        assertEquals(23, fixture.state.eventDeadlineTick[row]);
        assertEquals(30, fixture.state.eventSuppliesRequired[row]);
        assertEquals(15, fixture.state.eventFuelRequired[row]);
        assertEquals(60_000, fixture.state.eventCreditsOffered[row]);
    }

    @Test
    void debugSpawnHonorsDiscoveryAgeOpenGateAndSourceReplay() {
        Fixture tooNew = new Fixture(18, (byte) 0);
        assertEquals(-1L, DebugDefectorAsylumSpawner.spawn(
                tooNew.state, 20));

        Fixture rescueBlocked = new Fixture(10, (byte) 0);
        CivilianRescueEvent.prepare(rescueBlocked.state, 1L,
                rescueBlocked.market, 20, 23, 10, 5, 100);
        assertEquals(-1L, DebugDefectorAsylumSpawner.spawn(
                rescueBlocked.state, 20));

        Fixture replay = new Fixture(10, (byte) 0);
        long first = DebugDefectorAsylumSpawner.spawn(replay.state, 20);
        int row = replay.state.eventIndex(first);
        replay.state.eventState[row] = CampaignEventState.REFUSED.toByte();
        assertEquals(-1L, DebugDefectorAsylumSpawner.spawn(
                replay.state, 21));
    }

    @Test
    void committedEventCanBeRebasedToImmediateFollowupChoice() {
        Fixture fixture = new Fixture(10, (byte) 0);
        long event = DebugDefectorAsylumSpawner.spawn(fixture.state, 20);
        int row = fixture.state.eventIndex(event);
        fixture.state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        fixture.state.eventDecisionTick[row] = 20;
        fixture.state.eventFollowupTick[row] = 30;
        fixture.state.eventFollowupDeadlineTick[row] = 33;

        assertEquals(event, DebugDefectorAsylumSpawner.advanceCommitted(
                fixture.state, 22));
        assertEquals(CampaignEventState.PENDING_FOLLOWUP,
                CampaignEventState.fromByte(fixture.state.eventState[row]));
        assertEquals(22, fixture.state.eventFollowupTick[row]);
        assertEquals(25, fixture.state.eventFollowupDeadlineTick[row]);
        assertEquals(-1L, DebugDefectorAsylumSpawner.advanceCommitted(
                fixture.state, 23));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long actor = house(state, market, "Actor");
        final long target = house(state, market, "Target");
        final long chain;

        Fixture(int discoveredDay, byte tier) {
            chain = state.addAutonomousChain(actor, target, market, 7,
                    tier, ChainArchetype.CONSOLIDATE_STAKE,
                    (short) 45, (byte) 32, 1);
            state.chainDiscoveredTick[state.chainIndex(chain)] = discoveredDay;
        }
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
