package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugSilentColonySpawnerTest {

    @Test
    void debugSpawnUsesProductionTermsAndStableSiteLineage() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("dead_colony");

        long event = DebugSilentColonySpawner.spawn(state, market, 3, 42);

        int row = state.eventIndex(event);
        assertTrue(row >= 0);
        assertEquals(42, state.eventCreatedTick[row]);
        assertEquals(45, state.eventDeadlineTick[row]);
        assertEquals(50, state.eventSuppliesRequired[row]);
        assertEquals(25, state.eventFuelRequired[row]);
        assertEquals(10, state.eventCiviliansAtRisk[row]);
        assertTrue(state.eventColonyThreatSeed[row] >= 0L);
    }

    @Test
    void commonOpenEventGateAndOneShotSitePreventOverlap() {
        CampaignState state = new CampaignState();
        int firstMarket = state.marketRegistry.intern("dead_colony");
        int secondMarket = state.marketRegistry.intern("other_ruin");
        long first = DebugSilentColonySpawner.spawn(
                state, firstMarket, 2, 42);

        assertEquals(-1L, DebugSilentColonySpawner.spawn(
                state, secondMarket, 2, 42));
        state.eventState[state.eventIndex(first)] =
                CampaignEventState.REFUSED.toByte();
        assertEquals(first, DebugSilentColonySpawner.spawn(
                state, firstMarket, 4, 50));
        assertEquals(1, state.eventCount);
    }

    @Test
    void invalidInputsAppendNothing() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("dead_colony");

        assertEquals(-1L, DebugSilentColonySpawner.spawn(
                state, 99, 2, 42));
        assertEquals(-1L, DebugSilentColonySpawner.spawn(
                state, market, 0, 42));
        assertEquals(-1L, DebugSilentColonySpawner.spawn(
                state, market, 5, 42));
        assertEquals(0, state.eventCount);
    }
}
