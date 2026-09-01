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

class ProductionMissionTierTest {

    @Test
    void projectionReadsPersistedTierInsteadOfTargetRisk() {
        CampaignState state = state(HouseRank.TIER_1, ContractType.STRIKE);
        state.contractOperationTier[0] = OperationTier.VETERAN.toPersistedByte();

        assertEquals(OperationTier.VETERAN,
                MissionGenerator.tierForContract(state, 0, MissionType.RAID));
    }

    @Test
    void legacyProjectionUsesPatronRankAndStillHonoursTypeFloor() {
        CampaignState ordinary = state(HouseRank.TIER_2, ContractType.STRIKE);
        ordinary.contractOperationTier[0] = 0;
        CampaignState assault = state(HouseRank.TIER_3, ContractType.PLANETARY_ASSAULT);
        assault.contractOperationTier[0] = 0;

        assertEquals(OperationTier.ESTABLISHED,
                MissionGenerator.tierForContract(ordinary, 0, MissionType.RAID));
        assertEquals(OperationTier.REINFORCED,
                MissionGenerator.tierForContract(assault, 0, MissionType.CONQUEST));
    }

    private static CampaignState state(HouseRank rank, ContractType type) {
        CampaignState state = new CampaignState();
        long patron = state.addHouse(1, 1, HouseFlavor.CORPORATE,
                rank, HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Patron");
        state.addContract(patron, -1L, -1L, type, ContractState.OFFERED,
                1, -1, 10, (byte) 1, -1, 1, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);
        return state;
    }
}
