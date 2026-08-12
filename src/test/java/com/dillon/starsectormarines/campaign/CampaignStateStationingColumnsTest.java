package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateStationingColumnsTest {

    @Test
    void stationingColumnsGrowWithContractTable() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.addContract(1L, -1L, -1L,
                    ContractType.GARRISON, ContractState.OFFERED,
                    i, -1, i + 5, (byte) 0, -1, 0, -1,
                    0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        }

        assertEquals(20, state.contractCount);
        assertEquals(0, state.contractMarinesCommitted[19]);
        assertEquals(-1, state.contractLastRetainerTick[19]);
        assertEquals(-1, state.contractLastTrainingTick[19]);
        assertEquals(-1L, state.contractSourceContractId[19]);
        assertEquals(-1, state.contractLastDefaultCheckTick[19]);
        assertEquals(0, state.contractPhaseAttempts[19]);
    }

    @Test
    void legacySaveBackfillsStationingColumns() throws Exception {
        CampaignState state = new CampaignState();
        state.contractMarinesCommitted = null;
        state.contractLastRetainerTick = null;
        state.contractLastTrainingTick = null;
        state.contractSourceContractId = null;
        state.contractLastDefaultCheckTick = null;
        state.contractPhaseAttempts = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.contractMarinesCommitted);
        assertNotNull(state.contractLastRetainerTick);
        assertNotNull(state.contractLastTrainingTick);
        assertNotNull(state.contractSourceContractId);
        assertNotNull(state.contractLastDefaultCheckTick);
        assertNotNull(state.contractPhaseAttempts);
        assertEquals(state.contractId.length, state.contractMarinesCommitted.length);
        assertEquals(state.contractId.length, state.contractLastRetainerTick.length);
        assertEquals(-1, state.contractLastRetainerTick[0]);
        assertEquals(-1, state.contractLastTrainingTick[0]);
        assertEquals(-1L, state.contractSourceContractId[0]);
        assertEquals(-1, state.contractLastDefaultCheckTick[0]);
    }
}
