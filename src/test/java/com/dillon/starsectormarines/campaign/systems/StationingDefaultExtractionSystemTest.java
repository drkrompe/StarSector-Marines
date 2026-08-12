package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationingDefaultExtractionSystemTest {

    @Test
    void defaultedStationingSpawnsOneLinkedRecoveryOffer() {
        CampaignState state = stationing(ContractState.DEFAULTED);
        StationingDefaultExtractionSystem system = new StationingDefaultExtractionSystem();

        system.tick(state, 40);
        system.tick(state, 41);

        assertEquals(2, state.contractCount);
        int row = 1;
        assertEquals(ContractType.EXTRACTION, ContractType.fromByte(state.contractType[row]));
        assertEquals(ContractState.OFFERED, ContractState.fromByte(state.contractState[row]));
        assertEquals(state.contractId[0], state.contractSourceContractId[row]);
        assertEquals(-1L, state.contractTargetHouseId[row]);
        assertEquals(1, state.contractPhasesTotal[row] & 0xFF);
        assertEquals(7, state.contractMarketId[row]);
        assertEquals(2_200, state.contractBasePayout[row]);
        assertEquals(25, state.contractSalvageBaseline[row] & 0xFF);
        assertEquals(40, state.contractAcceptedTick[row]);
    }

    @Test
    void ignoresNonDefaultedAndEmptyAssignments() {
        CampaignState active = stationing(ContractState.ACTIVE);
        CampaignState empty = stationing(ContractState.DEFAULTED);
        empty.contractMarinesCommitted[0] = 0;
        empty.contractCaptainId[0] = -1;

        StationingDefaultExtractionSystem system = new StationingDefaultExtractionSystem();
        system.tick(active, 40);
        system.tick(empty, 40);

        assertEquals(1, active.contractCount);
        assertEquals(1, empty.contractCount);
    }

    private static CampaignState stationing(ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, contractState,
                10, 100, -1, (byte) 0, 3, 7, -1,
                0, 2_200, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        state.contractCaptainId[0] = 3;
        return state;
    }
}
