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
        assertEquals(ChronicleConfidence.CONFIRMED,
                ChronicleConfidence.fromByte(state.chronicleConfidence[0]));
        assertEquals(2L, state.chronicleActorHouseId[0]);
        assertEquals(3L, state.chronicleTargetHouseId[0]);
        assertEquals(4, state.chronicleMarketId[0]);
        assertEquals(5, state.chronicleIndustryId[0]);
        assertEquals(-1, state.chronicleSourceFactionId[0]);
        assertEquals(-1, state.chronicleResultFactionId[0]);
        assertEquals(20, state.chronicleHappenedTick[0]);
        assertEquals(22, state.chronicleLearnedTick[0]);
    }

    @Test
    void activeRumorRetainsRumorTypeAndConfidence() {
        CampaignState state = new CampaignState();

        long id = state.addChronicleChainRumor(8L, ChronicleBand.INTIMATE,
                2L, 3L, 4, 5, 20, 22);

        assertEquals(1L, id);
        assertEquals(ChronicleEventType.ACTIVE_CHAIN_RUMOR,
                ChronicleEventType.fromByte(state.chronicleEventType[0]));
        assertEquals(ChainState.ACTIVE,
                ChainState.fromByte(state.chronicleChainOutcome[0]));
        assertEquals(ChronicleConfidence.RUMOR,
                ChronicleConfidence.fromByte(state.chronicleConfidence[0]));
        assertEquals(20, state.chronicleHappenedTick[0]);
        assertEquals(22, state.chronicleLearnedTick[0]);
    }

    @Test
    void houseDormancyRetainsConfirmedHouseAndMarketSnapshot() {
        CampaignState state = new CampaignState();

        long id = state.addChronicleHouseDormancy(
                ChronicleBand.EPIC, 2L, 4, 20, 20);

        assertEquals(1L, id);
        assertEquals(ChronicleEventType.HOUSE_DORMANT,
                ChronicleEventType.fromByte(state.chronicleEventType[0]));
        assertEquals(ChronicleConfidence.CONFIRMED,
                ChronicleConfidence.fromByte(state.chronicleConfidence[0]));
        assertEquals(-1L, state.chronicleSourceChainId[0]);
        assertEquals(2L, state.chronicleActorHouseId[0]);
        assertEquals(-1L, state.chronicleTargetHouseId[0]);
        assertEquals(4, state.chronicleMarketId[0]);
        assertEquals(-1, state.chronicleIndustryId[0]);
        assertEquals(20, state.chronicleHappenedTick[0]);
        assertEquals(20, state.chronicleLearnedTick[0]);
    }

    @Test
    void appliedThroneClaimRetainsFactionIdentitySnapshot() {
        CampaignState state = new CampaignState();

        long id = state.addChronicleThroneClaimApplied(8L, ChronicleBand.EPIC,
                2L, 3L, 4, 5, 6, 20, 22);

        assertEquals(1L, id);
        assertEquals(ChronicleEventType.THRONE_CLAIM_APPLIED,
                ChronicleEventType.fromByte(state.chronicleEventType[0]));
        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(state.chronicleChainOutcome[0]));
        assertEquals(4, state.chronicleSourceFactionId[0]);
        assertEquals(5, state.chronicleResultFactionId[0]);
        assertEquals(6, state.chronicleMarketId[0]);
        assertEquals(-1, state.chronicleIndustryId[0]);
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
        assertEquals(-1, state.chronicleSourceFactionId[20]);
        assertEquals(-1, state.chronicleResultFactionId[20]);
        assertEquals(-1, state.chronicleHappenedTick[20]);
        assertEquals(-1, state.chronicleLearnedTick[20]);
        assertEquals(-1, state.chainDiscoveryProcessedTick[20]);
        assertEquals(-1, state.chainLastDiscoveryCheckTick[20]);
        assertEquals(-1, state.chainDiscoveredTick[20]);
    }

    @Test
    void legacyStateBackfillsChronicleStorageAndProcessedTick() throws Exception {
        CampaignState state = new CampaignState();
        state.chronicleId = null;
        state.chronicleEventType = null;
        state.chronicleSourceChainId = null;
        state.chronicleChainOutcome = null;
        state.chronicleBand = null;
        state.chronicleConfidence = null;
        state.chronicleActorHouseId = null;
        state.chronicleTargetHouseId = null;
        state.chronicleMarketId = null;
        state.chronicleIndustryId = null;
        state.chronicleSourceFactionId = null;
        state.chronicleResultFactionId = null;
        state.chronicleHappenedTick = null;
        state.chronicleLearnedTick = null;
        state.chainDiscoveryProcessedTick = null;
        state.chainLastDiscoveryCheckTick = null;
        state.chainDiscoveredTick = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.chronicleId);
        assertNotNull(state.chronicleEventType);
        assertNotNull(state.chronicleSourceChainId);
        assertNotNull(state.chronicleChainOutcome);
        assertNotNull(state.chronicleBand);
        assertNotNull(state.chronicleConfidence);
        assertNotNull(state.chronicleActorHouseId);
        assertNotNull(state.chronicleTargetHouseId);
        assertNotNull(state.chronicleMarketId);
        assertNotNull(state.chronicleIndustryId);
        assertNotNull(state.chronicleSourceFactionId);
        assertNotNull(state.chronicleResultFactionId);
        assertNotNull(state.chronicleHappenedTick);
        assertNotNull(state.chronicleLearnedTick);
        assertNotNull(state.chainDiscoveryProcessedTick);
        assertNotNull(state.chainLastDiscoveryCheckTick);
        assertNotNull(state.chainDiscoveredTick);
        assertEquals(-1L, state.chronicleSourceChainId[0]);
        assertEquals(-1, state.chronicleSourceFactionId[0]);
        assertEquals(-1, state.chronicleResultFactionId[0]);
        assertEquals(-1, state.chronicleLearnedTick[0]);
        assertEquals(-1, state.chainDiscoveryProcessedTick[0]);
        assertEquals(-1, state.chainLastDiscoveryCheckTick[0]);
        assertEquals(-1, state.chainDiscoveredTick[0]);
        assertEquals(ChronicleConfidence.CONFIRMED,
                ChronicleConfidence.fromByte(state.chronicleConfidence[0]));
        assertEquals(1L, state.addChronicleChainOutcome(1L, ChainState.FAILED,
                ChronicleBand.INTIMATE, 2L, 3L, 4, 5, 6, 7));
    }
}
