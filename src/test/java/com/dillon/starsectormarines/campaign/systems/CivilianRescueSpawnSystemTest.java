package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianRescueSpawnSystemTest {

    @Test
    void callFreezesSizeDerivedTermsAndItsWindow() {
        CampaignState state = new CampaignState();
        int slot = state.marketRegistry.intern("large");

        long id = CivilianRescueSpawnSystem.prepareEvent(state, 7L, slot, 6, 30, 14);

        int row = state.eventIndex(id);
        int tier = 4;
        assertEquals(7L, state.eventTriggerKey[row]);
        assertEquals(30, state.eventCreatedTick[row]);
        assertEquals(44, state.eventDeadlineTick[row]);
        assertEquals(25 * tier, state.eventSuppliesRequired[row]);
        assertEquals(15 * tier, state.eventFuelRequired[row]);
        assertEquals(100 * tier * tier, state.eventCiviliansAtRisk[row]);
    }

    @Test
    void colonyBelowSizeThreeRaisesNoCall() {
        CampaignState state = new CampaignState();
        int slot = state.marketRegistry.intern("tiny");

        assertEquals(-1L, CivilianRescueSpawnSystem.prepareEvent(state, 1L, slot, 2, 30));
        assertEquals(0, state.eventCount);
    }
}
