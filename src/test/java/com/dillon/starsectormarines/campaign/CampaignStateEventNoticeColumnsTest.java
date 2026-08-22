package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateEventNoticeColumnsTest {

    @Test
    void noticeAckColumnsGrowWithContractTable() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.addContract(1L, -1L, -1L,
                    ContractType.GARRISON, ContractState.OFFERED,
                    i, -1, i + 5, (byte) 0, -1, 0, -1,
                    0, 1_000, (byte) 25, (byte) 25, (byte) 100);
        }

        assertEquals(20, state.contractCount);
        assertEquals(0L, state.contractNoticeAckKey[19]);
        assertEquals(0, state.contractNoticeAckStage[19]);
        assertEquals(state.contractId.length, state.contractNoticeAckKey.length);
        assertEquals(state.contractId.length, state.contractNoticeAckStage.length);
    }

    @Test
    void aReusedRowStartsUnacknowledged() {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON, ContractState.OFFERED,
                0, -1, 5, (byte) 0, -1, 0, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractNoticeAckKey[0] = 900L;
        state.contractNoticeAckStage[0] = 2;

        // A later append must not inherit the previous occupant's presentation history.
        state.contractCount = 0;
        state.addContract(2L, -1L, -1L, ContractType.GARRISON, ContractState.OFFERED,
                0, -1, 5, (byte) 0, -1, 0, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);

        assertEquals(0L, state.contractNoticeAckKey[0]);
        assertEquals(0, state.contractNoticeAckStage[0]);
    }

    @Test
    void legacySaveBackfillsNoticeAckColumns() throws Exception {
        CampaignState state = new CampaignState();
        state.contractNoticeAckKey = null;
        state.contractNoticeAckStage = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.contractNoticeAckKey);
        assertNotNull(state.contractNoticeAckStage);
        assertEquals(state.contractId.length, state.contractNoticeAckKey.length);
        assertEquals(state.contractId.length, state.contractNoticeAckStage.length);
        // A save predating the popup has shown nothing, so every live event still pops.
        assertEquals(0L, state.contractNoticeAckKey[0]);
        assertEquals(0, state.contractNoticeAckStage[0]);
    }
}
