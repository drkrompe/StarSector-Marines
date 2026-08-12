package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanetaryAssaultResolutionTest {

    @Test
    void victoriesAdvanceAndFinalVictoryCompletes() {
        CampaignState state = assault(3);

        assertEquals(PlanetaryAssaultResolution.Result.PHASE_ADVANCED,
                PlanetaryAssaultResolution.apply(state, 0, true, 0, 0));
        assertEquals(1, state.contractPhasesDone[0] & 0xFF);
        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));

        state.contractPhaseAttempts[0] = 2;
        PlanetaryAssaultResolution.apply(state, 0, true, 1, 2);
        assertEquals(0, state.contractPhaseAttempts[0]);
        assertEquals(PlanetaryAssaultResolution.Result.CONTRACT_COMPLETED,
                PlanetaryAssaultResolution.apply(state, 0, true, 2, 0));
        assertEquals(ContractState.COMPLETED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void nonFinalDefeatRerollsCurrentPhaseWithoutAdvancing() {
        CampaignState state = assault(4);
        state.contractPhasesDone[0] = 1;

        assertEquals(PlanetaryAssaultResolution.Result.PHASE_REROLLED,
                PlanetaryAssaultResolution.apply(state, 0, false, 1, 0));
        assertEquals(1, state.contractPhasesDone[0] & 0xFF);
        assertEquals(1, state.contractPhaseAttempts[0]);
        assertEquals(ContractState.IN_PROGRESS, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void finalDefeatFailsContract() {
        CampaignState state = assault(3);
        state.contractPhasesDone[0] = 2;

        assertEquals(PlanetaryAssaultResolution.Result.CONTRACT_FAILED,
                PlanetaryAssaultResolution.apply(state, 0, false, 2, 0));
        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void rejectsInvalidOrTerminalRows() {
        CampaignState state = assault(3);
        state.contractState[0] = ContractState.COMPLETED.toByte();
        assertNull(PlanetaryAssaultResolution.apply(state, 0, true, 0, 0));
        assertNull(PlanetaryAssaultResolution.apply(state, 99, true, 0, 0));
    }

    @Test
    void duplicateAndStaleResultsCannotAdvanceAgain() {
        CampaignState state = assault(4);
        assertEquals(PlanetaryAssaultResolution.Result.PHASE_ADVANCED,
                PlanetaryAssaultResolution.apply(state, 0, true, 0, 0));

        assertNull(PlanetaryAssaultResolution.apply(state, 0, true, 0, 0));
        assertEquals(1, state.contractPhasesDone[0] & 0xFF);

        assertEquals(PlanetaryAssaultResolution.Result.PHASE_REROLLED,
                PlanetaryAssaultResolution.apply(state, 0, false, 1, 0));
        assertNull(PlanetaryAssaultResolution.apply(state, 0, false, 1, 0));
        assertEquals(1, state.contractPhaseAttempts[0]);
    }

    private static CampaignState assault(int phases) {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, ContractType.PLANETARY_ASSAULT,
                ContractState.OFFERED, 1, -1, 20, (byte) phases, -1, 1, -1,
                180_000, 0, (byte) 80, (byte) 80, (byte) 100);
        return state;
    }
}
