package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateDriftColumnsTest {

    @Test
    void growthInitializesNewDriftTicksToNever() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "House " + i);
        }

        assertEquals(-1, state.houseLastDriftTick[19]);
    }

    @Test
    void legacyStateBackfillsDriftTicksToNever() throws Exception {
        CampaignState state = new CampaignState();
        state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Legacy");
        state.houseLastDriftTick = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.houseLastDriftTick);
        assertEquals(-1, state.houseLastDriftTick[0]);
    }
}
