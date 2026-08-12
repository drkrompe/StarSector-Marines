package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCivilianRescueSpawnerTest {

    @Test
    void debugSpawnUsesProductionTermsAndReservedTrigger() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");

        long event = DebugCivilianRescueSpawner.spawn(state, market, 6, 42);

        int row = state.eventIndex(event);
        assertTrue(row >= 0);
        assertTrue(state.eventTriggerKey[row] >= 1L << 62);
        assertEquals(42, state.eventCreatedTick[row]);
        assertEquals(45, state.eventDeadlineTick[row]);
        assertEquals(100, state.eventSuppliesRequired[row]);
        assertEquals(60, state.eventFuelRequired[row]);
        assertEquals(1_600, state.eventCiviliansAtRisk[row]);
    }

    @Test
    void activeRescueBlocksDebugOverlapButTerminalAllowsAnother() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long first = DebugCivilianRescueSpawner.spawn(state, market, 4, 42);

        assertEquals(-1L, DebugCivilianRescueSpawner.spawn(
                state, market, 4, 42));
        state.eventState[state.eventIndex(first)] =
                CampaignEventState.REFUSED.toByte();

        long second = DebugCivilianRescueSpawner.spawn(state, market, 4, 42);
        assertTrue(second > first);
        assertEquals(2, state.eventCount);
    }

    @Test
    void invalidMarketOrSizeDoesNotAppend() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");

        assertEquals(-1L, DebugCivilianRescueSpawner.spawn(
                state, 99, 4, 42));
        assertEquals(-1L, DebugCivilianRescueSpawner.spawn(
                state, market, 2, 42));
        assertEquals(-1L, DebugCivilianRescueSpawner.spawn(
                state, market, Integer.MAX_VALUE, 42));
        assertEquals(0, state.eventCount);
    }
}
