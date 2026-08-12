package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateThroneClaimColumnsTest {

    @Test
    void preparationIsSourceChainUniqueAndSnapshotsIdentity() {
        CampaignState state = new CampaignState();

        long first = state.prepareThroneClaim(7L, 3L, 11, 12, 5, 90);
        long repeated = state.prepareThroneClaim(7L, 99L, 21, 22, 15, 190);

        assertEquals(first, repeated);
        assertEquals(1, state.throneClaimCount);
        int row = state.throneClaimIndex(first);
        assertEquals(7L, state.throneClaimSourceChainId[row]);
        assertEquals(3L, state.throneClaimHouseId[row]);
        assertEquals(11, state.throneClaimSourceFactionId[row]);
        assertEquals(12, state.throneClaimResultFactionId[row]);
        assertEquals(5, state.throneClaimMarketId[row]);
        assertEquals(ThroneClaimState.PREPARED,
                ThroneClaimState.fromByte(state.throneClaimState[row]));
        assertEquals(90, state.throneClaimPreparedTick[row]);
        assertEquals(-1, state.throneClaimAppliedTick[row]);
    }

    @Test
    void growthInitializesUnusedIdentityAndTickSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.prepareThroneClaim(i + 1L, i + 101L, i, i + 1, i + 2, i + 3);
        }

        assertEquals(-1L, state.throneClaimSourceChainId[20]);
        assertEquals(-1L, state.throneClaimHouseId[20]);
        assertEquals(-1, state.throneClaimSourceFactionId[20]);
        assertEquals(-1, state.throneClaimResultFactionId[20]);
        assertEquals(-1, state.throneClaimMarketId[20]);
        assertEquals(-1, state.throneClaimPreparedTick[20]);
        assertEquals(-1, state.throneClaimAppliedTick[20]);
    }

    @Test
    void legacyStateBackfillsColumnsAndRebuildsIndexAndSequence() throws Exception {
        CampaignState state = new CampaignState();
        long existing = state.prepareThroneClaim(7L, 3L, 11, 12, 5, 90);
        state.throneClaimIndexById = null;
        Field nextId = CampaignState.class.getDeclaredField("nextThroneClaimId");
        nextId.setAccessible(true);
        nextId.setLong(state, 0L);

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.throneClaimIndexById);
        assertEquals(0, state.throneClaimIndex(existing));
        assertEquals(2L, state.prepareThroneClaim(8L, 4L, 11, 13, 6, 91));
    }

    @Test
    void legacyEmptyStateBackfillsAbsentTable() throws Exception {
        CampaignState state = new CampaignState();
        state.throneClaimId = null;
        state.throneClaimSourceChainId = null;
        state.throneClaimHouseId = null;
        state.throneClaimSourceFactionId = null;
        state.throneClaimResultFactionId = null;
        state.throneClaimMarketId = null;
        state.throneClaimState = null;
        state.throneClaimPreparedTick = null;
        state.throneClaimAppliedTick = null;
        state.throneClaimIndexById = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.throneClaimId);
        assertEquals(-1L, state.throneClaimSourceChainId[0]);
        assertEquals(-1L, state.throneClaimHouseId[0]);
        assertEquals(-1, state.throneClaimSourceFactionId[0]);
        assertEquals(-1, state.throneClaimResultFactionId[0]);
        assertEquals(-1, state.throneClaimMarketId[0]);
        assertEquals(-1, state.throneClaimPreparedTick[0]);
        assertEquals(-1, state.throneClaimAppliedTick[0]);
        assertNotNull(state.throneClaimIndexById);
    }
}
