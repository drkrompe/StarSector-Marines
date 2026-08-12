package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateChronicleColumnsTest {

    @Test
    void learnedChainOutcomeRetainsImmutableEventSnapshot() {
        CampaignState state = new CampaignState();

        long id = state.addChronicleChainOutcome(8L, ChainState.RESOLVED,
                ChronicleBand.INTIMATE, 2L, 3L, 4, 5, 20, 22);

        assertEquals(1L, id);
        assertEquals(ChronicleEventType.CHAIN_OUTCOME,
                ChronicleEventType.fromByte(state.chronicleEventType[0]));
        assertEquals(8L, state.chronicleSourceChainId[0]);
        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(state.chronicleChainOutcome[0]));
        assertEquals(ChronicleBand.INTIMATE,
                ChronicleBand.fromByte(state.chronicleBand[0]));
        assertEquals(2L, state.chronicleActorHouseId[0]);
        assertEquals(3L, state.chronicleTargetHouseId[0]);
        assertEquals(4, state.chronicleMarketId[0]);
        assertEquals(5, state.chronicleIndustryId[0]);
        assertEquals(20, state.chronicleHappenedTick[0]);
        assertEquals(22, state.chronicleLearnedTick[0]);
    }

    @Test
    void growthInitializesUnusedChronicleAndChainSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.addChronicleChainOutcome(i, ChainState.RESOLVED, ChronicleBand.EPIC,
                    i + 1L, i + 2L, i, i + 10, i + 20, i + 21);
            state.addAutonomousChain(i + 1L, i + 2L, i, i + 10, (byte) 0,
                    ChainArchetype.CONSOLIDATE_STAKE, (short) 10, (byte) 1, i);
        }

        assertEquals(20, state.chronicleCount);
        assertEquals(-1L, state.chronicleSourceChainId[20]);
        assertEquals(-1L, state.chronicleActorHouseId[20]);
        assertEquals(-1L, state.chronicleTargetHouseId[20]);
        assertEquals(-1, state.chronicleMarketId[20]);
        assertEquals(-1, state.chronicleIndustryId[20]);
        assertEquals(-1, state.chronicleHappenedTick[20]);
        assertEquals(-1, state.chronicleLearnedTick[20]);
        assertEquals(-1, state.chainDiscoveryProcessedTick[20]);
    }

    @Test
    void legacyStateBackfillsChronicleStorageAndProcessedTick() throws Exception {
        CampaignState state = new CampaignState();
        state.chronicleId = null;
        state.chronicleEventType = null;
        state.chronicleSourceChainId = null;
        state.chronicleChainOutcome = null;
        state.chronicleBand = null;
        state.chronicleActorHouseId = null;
        state.chronicleTargetHouseId = null;
        state.chronicleMarketId = null;
        state.chronicleIndustryId = null;
        state.chronicleHappenedTick = null;
        state.chronicleLearnedTick = null;
        state.chainDiscoveryProcessedTick = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.chronicleId);
        assertNotNull(state.chronicleEventType);
        assertNotNull(state.chronicleSourceChainId);
        assertNotNull(state.chronicleChainOutcome);
        assertNotNull(state.chronicleBand);
        assertNotNull(state.chronicleActorHouseId);
        assertNotNull(state.chronicleTargetHouseId);
        assertNotNull(state.chronicleMarketId);
        assertNotNull(state.chronicleIndustryId);
        assertNotNull(state.chronicleHappenedTick);
        assertNotNull(state.chronicleLearnedTick);
        assertNotNull(state.chainDiscoveryProcessedTick);
        assertEquals(-1L, state.chronicleSourceChainId[0]);
        assertEquals(-1, state.chronicleLearnedTick[0]);
        assertEquals(-1, state.chainDiscoveryProcessedTick[0]);
        assertEquals(1L, state.addChronicleChainOutcome(1L, ChainState.FAILED,
                ChronicleBand.INTIMATE, 2L, 3L, 4, 5, 6, 7));
    }
}
