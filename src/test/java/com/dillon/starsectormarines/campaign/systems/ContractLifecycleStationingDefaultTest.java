package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractLifecycleStationingDefaultTest {

    @Test
    void deposedPatronDefaultsOnlyStationingContracts() {
        CampaignState state = new CampaignState();
        long patron = state.addHouse(0, 0, HouseFlavor.CORPORATE, HouseRank.TIER_2,
                HouseStatus.DEPOSED, PatronArchetype.ESTABLISHED, "Patron");
        state.addContract(patron, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                1, 100, -1, (byte) 0, 1, 0, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.addContract(patron, 2L, -1L, ContractType.STRIKE, ContractState.ACTIVE,
                1, -1, -1, (byte) 1, -1, 0, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);

        new ContractLifecycleSystem().tick(state, 2);

        assertEquals(ContractState.DEFAULTED, ContractState.fromByte(state.contractState[0]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[1]));
    }
}
