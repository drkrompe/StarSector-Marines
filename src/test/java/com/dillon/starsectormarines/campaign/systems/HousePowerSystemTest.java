package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HousePowerSystemTest {

    @Test
    void aggregatesEveryStakeOwnedByEachHouse() {
        CampaignState state = new CampaignState();
        long first = house(state, "First");
        long second = house(state, "Second");
        state.addStake(first, 1, 1, (short) 110);
        state.addStake(first, 1, 2, (short) 75);
        state.addStake(second, 1, 1, (short) 50);

        new HousePowerSystem().tick(state, 1);

        assertEquals(185, state.housePower[state.houseIndex(first)]);
        assertEquals(50, state.housePower[state.houseIndex(second)]);
    }

    @Test
    void rebuildClearsStalePowerAndIgnoresOrphanStake() {
        CampaignState state = new CampaignState();
        long first = house(state, "First");
        state.housePower[0] = 999;
        state.addStake(404L, 1, 1, (short) 200);

        HousePowerSystem system = new HousePowerSystem();
        system.tick(state, 1);
        assertEquals(0, state.housePower[0]);

        state.addStake(first, 1, 1, (short) 60);
        system.tick(state, 2);
        assertEquals(60, state.housePower[0]);
    }

    private static long house(CampaignState state, String name) {
        return state.addHouse(1, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, name);
    }
}
