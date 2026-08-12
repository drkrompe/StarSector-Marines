package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetaryAssaultMissionAvailabilityTest {

    @Test
    void offeredAndInProgressAssaultsRemainMissionAvailable() {
        CampaignState state = state(ContractType.PLANETARY_ASSAULT, ContractState.OFFERED);
        assertTrue(MissionGenerator.contractMissionAvailable(state, 0, 10));

        state.contractState[0] = ContractState.IN_PROGRESS.toByte();
        assertTrue(MissionGenerator.contractMissionAvailable(state, 0, 10));
    }

    @Test
    void ordinaryInProgressAndTerminalContractsDoNotReemit() {
        CampaignState strike = state(ContractType.STRIKE, ContractState.IN_PROGRESS);
        CampaignState completed = state(ContractType.PLANETARY_ASSAULT, ContractState.COMPLETED);

        assertFalse(MissionGenerator.contractMissionAvailable(strike, 0, 10));
        assertFalse(MissionGenerator.contractMissionAvailable(completed, 0, 10));
    }

    @Test
    void inProgressAssaultWaitsUntilPersistedReadyDay() {
        CampaignState state = state(ContractType.PLANETARY_ASSAULT,
                ContractState.IN_PROGRESS);
        state.contractNextPhaseReadyTick[0] = 13;

        assertFalse(MissionGenerator.contractMissionAvailable(state, 0, 12));
        assertTrue(MissionGenerator.contractMissionAvailable(state, 0, 13));
    }

    private static CampaignState state(ContractType type, ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, type, contractState,
                1, -1, 20, (byte) 4, -1, 1, -1,
                180_000, 0, (byte) 80, (byte) 80, (byte) 100);
        return state;
    }
}
