package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseConsolidationSystemTest {

    @Test
    void activeHouseWithOnlyTombstonedStakeHistoryBecomesDormant() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 2, 9, (short) 0);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.DORMANT,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(house)]));
    }

    @Test
    void anyPositiveStakeAnywhereKeepsHouseActive() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 2, 9, (short) 1);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.ACTIVE,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(house)]));
    }

    @Test
    void neverSeededHouseAndNonActiveStatusesRemainUntouched() {
        CampaignState state = new CampaignState();
        long neverSeeded = house(state, HouseStatus.ACTIVE);
        long deposed = house(state, HouseStatus.DEPOSED);
        state.addStake(deposed, 1, 7, (short) 0);

        new HouseConsolidationSystem().tick(state, 20);

        assertEquals(HouseStatus.ACTIVE,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(neverSeeded)]));
        assertEquals(HouseStatus.DEPOSED,
                HouseStatus.fromByte(state.houseStatus[state.houseIndex(deposed)]));
    }

    @Test
    void repeatedTicksDoNotChangeDormantIdentity() {
        CampaignState state = new CampaignState();
        long house = house(state, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        HouseConsolidationSystem system = new HouseConsolidationSystem();

        system.tick(state, 20);
        system.tick(state, 21);

        assertEquals(1, state.houseCount);
        assertEquals(house, state.houseId[0]);
        assertEquals(0, state.houseIndex(house));
        assertEquals(HouseStatus.DORMANT, HouseStatus.fromByte(state.houseStatus[0]));
    }

    private static long house(CampaignState state, HouseStatus status) {
        return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                status, PatronArchetype.NEWCOMER, "House");
    }
}
