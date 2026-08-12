package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractMissionTargetTest {

    @Test
    void extractionTargetsItsOwnStationingMarket() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.EXTRACTION, ContractState.OFFERED,
                1, -1, -1, (byte) 1, -1, 12, -1,
                1_000, 0, (byte) 25, (byte) 25, (byte) 100);

        assertEquals(12, MissionGenerator.targetMarketSlot(state, 0));
    }

    @Test
    void ordinaryMissionTargetsTheTargetHouseMarket() {
        CampaignState state = new CampaignState();
        long target = state.addHouse(22, 1, HouseFlavor.CORPORATE, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Target");
        state.addContract(1L, target, -1L, ContractType.STRIKE, ContractState.OFFERED,
                1, -1, -1, (byte) 1, -1, 12, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);

        assertEquals(22, MissionGenerator.targetMarketSlot(state, 0));
    }
}
