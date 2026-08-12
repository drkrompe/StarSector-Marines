package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseAmbitionSystemTest {

    @Test
    void noneHouseConsolidatesItsStrongestHomeIndustry() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 40);
        state.addStake(house, 1, 9, (short) 80);

        new HouseAmbitionSystem().tick(state, 20);

        int row = state.houseIndex(house);
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(9L, state.houseAmbitionTarget[row]);
    }

    @Test
    void tiesChooseLowestIndustrySlotDeterministically() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 9, (short) 60);
        state.addStake(house, 1, 7, (short) 60);

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(7L, state.houseAmbitionTarget[state.houseIndex(house)]);
    }

    @Test
    void offMarketAndTombstonedStakesDoNotCreateAmbition() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 2, 7, (short) 100);
        state.addStake(house, 1, 9, (short) 0);

        new HouseAmbitionSystem().tick(state, 20);

        int row = state.houseIndex(house);
        assertEquals(HouseAmbition.NONE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(-1L, state.houseAmbitionTarget[row]);
    }

    @Test
    void existingAmbitionAndInactiveHouseRemainUntouched() {
        CampaignState state = new CampaignState();
        long active = house(state, 1, HouseStatus.ACTIVE);
        long dormant = house(state, 1, HouseStatus.DORMANT);
        state.addStake(active, 1, 7, (short) 100);
        state.addStake(dormant, 1, 9, (short) 100);
        int activeRow = state.houseIndex(active);
        state.houseAmbition[activeRow] = HouseAmbition.PROMOTE.toByte();
        state.houseAmbitionTarget[activeRow] = 123L;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.PROMOTE,
                HouseAmbition.fromByte(state.houseAmbition[activeRow]));
        assertEquals(123L, state.houseAmbitionTarget[activeRow]);
        assertEquals(HouseAmbition.NONE, HouseAmbition.fromByte(
                state.houseAmbition[state.houseIndex(dormant)]));
    }

    private static long house(CampaignState state, int market, HouseStatus status) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                status, PatronArchetype.NEWCOMER, "House");
    }
}
