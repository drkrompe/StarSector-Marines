package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingIncidentSystemTest {

    @Test
    void dueCadreIncidentArmsExactlyOnce() {
        CampaignState state = state(ContractType.CADRE, ContractState.ACTIVE, 10, 100);
        int due = StationingIncidentSystem.nextIncidentDay(state.contractId[0], 10);
        state.contractNextIncidentTick[0] = due;
        StationingIncidentSystem system = new StationingIncidentSystem();

        system.tick(state, due - 1);
        assertEquals(0, state.contractIncidentPending[0]);
        assertEquals(due, state.contractNextIncidentTick[0]);

        system.tick(state, due);
        assertEquals(1, state.contractIncidentPending[0]);
        assertEquals(due, state.contractNextIncidentTick[0]);
        StationingIncidentType armed = StationingIncidentType.fromByte(
                state.contractIncidentType[0]);
        assertTrue(armed != StationingIncidentType.NONE);

        system.tick(state, due + 20);
        assertEquals(1, state.contractIncidentPending[0]);
        assertEquals(due, state.contractNextIncidentTick[0]);
        assertEquals(armed, StationingIncidentType.fromByte(state.contractIncidentType[0]));
    }

    @Test
    void legacyCadreBackfillsDeterministicSchedule() {
        CampaignState first = state(ContractType.CADRE, ContractState.ACTIVE, 20, 100);
        CampaignState second = state(ContractType.CADRE, ContractState.ACTIVE, 20, 100);

        new StationingIncidentSystem().tick(first, 21);
        new StationingIncidentSystem().tick(second, 21);

        assertEquals(first.contractNextIncidentTick[0], second.contractNextIncidentTick[0]);
        assertTrue(first.contractNextIncidentTick[0] >= 44);
        assertTrue(first.contractNextIncidentTick[0] <= 56);
    }

    @Test
    void garrisonAndCadreEndingBeforeDueDoNotArm() {
        CampaignState state = state(ContractType.GARRISON, ContractState.ACTIVE, 10, 100);
        state(ContractType.CADRE, ContractState.ACTIVE, 10, 30, state);

        StationingIncidentSystem system = new StationingIncidentSystem();
        system.tick(state, 50);

        assertEquals(0, state.contractIncidentPending[0]);
        assertEquals(-1, state.contractNextIncidentTick[0]);
        assertEquals(0, state.contractIncidentPending[1]);
        assertEquals(Integer.MAX_VALUE, state.contractNextIncidentTick[1]);
    }

    @Test
    void terminalCadreClearsUnconsumedTrigger() {
        CampaignState state = state(ContractType.CADRE, ContractState.COMPLETED, 10, 100);
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.DEFECTOR_LEAD.toByte();
        state.contractNextIncidentTick[0] = 40;

        new StationingIncidentSystem().tick(state, 50);

        assertEquals(0, state.contractIncidentPending[0]);
        assertEquals(-1, state.contractNextIncidentTick[0]);
        assertEquals(StationingIncidentType.NONE,
                StationingIncidentType.fromByte(state.contractIncidentType[0]));
    }

    private static CampaignState state(ContractType type, ContractState contractState,
                                       int accepted, int expires) {
        return state(type, contractState, accepted, expires, new CampaignState());
    }

    private static CampaignState state(ContractType type, ContractState contractState,
                                       int accepted, int expires, CampaignState state) {
        state.addContract(1L, -1L, -1L, type, contractState,
                accepted, expires, -1, (byte) 0, -1, 1, -1,
                0, 1_000, (byte) 5, (byte) 5, (byte) 100);
        return state;
    }
}
