package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTableCompactorTest {

    @Test
    void removingTerminalRowsKeepsEveryStationingColumnAligned() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.STRIKE, ContractState.COMPLETED,
                1, -1, -1, (byte) 1, -1, 10, 11,
                12, 0, (byte) 60, (byte) 50, (byte) 105);
        long keptId = state.addContract(2L, -1L, -1L, ContractType.CADRE, ContractState.ACTIVE,
                20, 200, -1, (byte) 0, 7, 21, 22,
                0, 2_300, (byte) 5, (byte) 4, (byte) 101);
        state.contractPhasesDone[1] = 3;
        state.contractMarinesCommitted[1] = 88;
        state.contractLastRetainerTick[1] = 50;
        state.contractLastTrainingTick[1] = 51;
        state.contractSourceContractId[1] = 99L;

        assertEquals(1, ContractTableCompactor.removeTerminal(state));

        assertEquals(1, state.contractCount);
        assertEquals(0, state.contractIndex(keptId));
        assertEquals(keptId, state.contractId[0]);
        assertEquals(ContractType.CADRE, ContractType.fromByte(state.contractType[0]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertEquals(20, state.contractAcceptedTick[0]);
        assertEquals(200, state.contractExpiresTick[0]);
        assertEquals(3, state.contractPhasesDone[0]);
        assertEquals(7, state.contractCaptainId[0]);
        assertEquals(21, state.contractMarketId[0]);
        assertEquals(22, state.contractIndustryId[0]);
        assertEquals(2_300, state.contractRetainerPerMonth[0]);
        assertEquals(88, state.contractMarinesCommitted[0]);
        assertEquals(50, state.contractLastRetainerTick[0]);
        assertEquals(51, state.contractLastTrainingTick[0]);
        assertEquals(99L, state.contractSourceContractId[0]);
        assertEquals(5, state.contractSalvageBaseline[0] & 0xFF);
        assertEquals(4, state.contractSalvageNegotiated[0] & 0xFF);
        assertEquals(101, state.contractCashMultiplier[0] & 0xFF);
    }

    @Test
    void defaultedStationingRowWithPersonnelCannotBeCompactedAway() {
        CampaignState state = new CampaignState();
        long id = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.DEFAULTED, 1, 30, -1, (byte) 0, 4, 5, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 50;

        assertEquals(0, ContractTableCompactor.removeTerminal(state));
        assertEquals(0, state.contractIndex(id));
        assertEquals(1, state.contractCount);
    }
}
