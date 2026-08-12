package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GarrisonDefenseTriggerTest {

    @Test
    void armsEveryActiveGarrisonAtTargetMarket() {
        CampaignState state = new CampaignState();
        add(state, 1L, ContractType.GARRISON, ContractState.ACTIVE, 7);
        add(state, 2L, ContractType.GARRISON, ContractState.ACTIVE, 7);
        add(state, 3L, ContractType.CADRE, ContractState.ACTIVE, 7);
        add(state, 4L, ContractType.GARRISON, ContractState.ACTIVE, 8);

        int armed = GarrisonDefenseTrigger.arm(state, 99L, 7,
                GarrisonDefenseTriggerType.VANILLA_RAID, -1L, 5, 42);

        assertEquals(2, armed);
        assertEquals(ContractState.IN_PROGRESS,
                ContractState.fromByte(state.contractState[0]));
        assertEquals(ContractState.IN_PROGRESS,
                ContractState.fromByte(state.contractState[1]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[2]));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[3]));
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(state, state.contractId[0]);
        assertEquals(99L, payload.eventKey);
        assertEquals(GarrisonDefenseTriggerType.VANILLA_RAID, payload.triggerType);
        assertEquals(42, payload.triggeredDay);
        assertEquals(5, payload.attackerFactionId);
    }

    @Test
    void duplicateEventAndInvalidRivalCannotRearm() {
        CampaignState state = new CampaignState();
        add(state, 1L, ContractType.GARRISON, ContractState.ACTIVE, 7);

        assertEquals(0, GarrisonDefenseTrigger.arm(state, 10L, 7,
                GarrisonDefenseTriggerType.RIVAL_STRIKE, 1L, -1, 20));
        assertEquals(1, GarrisonDefenseTrigger.arm(state, 10L, 7,
                GarrisonDefenseTriggerType.RIVAL_STRIKE, 2L, -1, 20));
        state.contractState[0] = ContractState.ACTIVE.toByte();
        assertEquals(0, GarrisonDefenseTrigger.arm(state, 10L, 7,
                GarrisonDefenseTriggerType.RIVAL_STRIKE, 2L, -1, 21));
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void nonPendingRowsHaveNoPayload() {
        CampaignState state = new CampaignState();
        add(state, 1L, ContractType.GARRISON, ContractState.ACTIVE, 7);
        assertNull(GarrisonDefensePayload.from(state, state.contractId[0]));
    }

    private static void add(CampaignState state, long patron, ContractType type,
                            ContractState contractState, int market) {
        state.addContract(patron, -1L, -1L, type, contractState,
                10, 100, -1, (byte) 0, -1, market, -1,
                0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[state.contractCount - 1] = 80;
    }
}
