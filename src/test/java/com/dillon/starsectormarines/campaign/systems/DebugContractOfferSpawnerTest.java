package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugContractOfferSpawnerTest {

    @Test
    void spawnsProductionShapedStationingOffer() {
        CampaignState state = state(HouseRank.TIER_2);

        long id = DebugContractOfferSpawner.spawn(state, 0, ContractType.GARRISON, 42);

        int row = state.contractIndex(id);
        assertTrue(row >= 0);
        assertEquals(-1L, state.contractTargetHouseId[row]);
        assertEquals(0, state.contractPhasesTotal[row] & 0xFF);
        assertEquals(0, state.contractBasePayout[row]);
        assertEquals(25, state.contractSalvageBaseline[row] & 0xFF);
    }

    @Test
    void escortUsesTierPayoutAndRequiresTarget() {
        CampaignState state = state(HouseRank.TIER_2);
        state.addHouse(2, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Target");

        long id = DebugContractOfferSpawner.spawn(state, 0, ContractType.ESCORT, 42);

        int row = state.contractIndex(id);
        assertTrue(row >= 0);
        assertEquals(45_000, state.contractBasePayout[row]);
        assertEquals(10, state.contractSalvageBaseline[row] & 0xFF);
        assertTrue(state.contractTargetHouseId[row] >= 0L);
    }

    @Test
    void rejectsRankGateUnsupportedTypeAndOpenOfferDuplicate() {
        CampaignState tierOne = state(HouseRank.TIER_1);
        assertEquals(-1L, DebugContractOfferSpawner.spawn(
                tierOne, 0, ContractType.CADRE, 1));
        assertEquals(-1L, DebugContractOfferSpawner.spawn(
                tierOne, 0, ContractType.PLANETARY_ASSAULT, 1));

        CampaignState tierTwo = state(HouseRank.TIER_2);
        assertTrue(DebugContractOfferSpawner.spawn(
                tierTwo, 0, ContractType.GARRISON, 1) >= 0L);
        assertEquals(-1L, DebugContractOfferSpawner.spawn(
                tierTwo, 0, ContractType.CADRE, 1));
    }

    @Test
    void spawnsProductionShapedPlanetaryAssaultForTierThree() {
        CampaignState state = state(HouseRank.TIER_3);
        state.addHouse(2, 1, HouseFlavor.FEUDAL, HouseRank.TIER_2,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Target");

        long id = DebugContractOfferSpawner.spawn(
                state, 0, ContractType.PLANETARY_ASSAULT, 42);

        int row = state.contractIndex(id);
        assertTrue(row >= 0);
        assertEquals(180_000, state.contractBasePayout[row]);
        assertEquals(80, state.contractSalvageBaseline[row] & 0xFF);
        assertEquals(4, state.contractPhasesTotal[row] & 0xFF);
    }

    private static CampaignState state(HouseRank rank) {
        CampaignState state = new CampaignState();
        state.addHouse(1, 1, HouseFlavor.CORPORATE, rank,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Patron");
        return state;
    }
}
