package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateChainColumnsTest {

    @Test
    void playerBackedChainRetainsPatronAsActor() {
        CampaignState state = new CampaignState();

        long id = state.addChain(4L, 9L, (byte) 2, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 12, (byte) 3, 40);

        int row = state.chainIndex(id);
        assertEquals(4L, state.chainPatron[row]);
        assertEquals(4L, state.chainActorHouseId[row]);
        assertEquals(9L, state.chainTarget[row]);
        assertEquals(-1, state.chainMarketId[row]);
        assertEquals(-1, state.chainIndustryId[row]);
        assertEquals(ChainState.ACTIVE, ChainState.fromByte(state.chainState[row]));
        assertEquals(-1, state.chainLastAdvanceTick[row]);
        assertEquals(-1, state.chainResolvedTick[row]);
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[row]));
        assertEquals(0, state.chainPlayerContribution[row]);
        assertEquals(-1, state.chainPlayerLastContributionTick[row]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.chainPlayerConsequenceState[row]));
        assertEquals(-1, state.chainPlayerConsequenceAppliedTick[row]);
    }

    @Test
    void autonomousChainRetainsActorAndLocationWithoutPatron() {
        CampaignState state = new CampaignState();

        long id = state.addAutonomousChain(4L, 9L, 2, 7, (byte) 1,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 10, (byte) 2, 30);

        int row = state.chainIndex(id);
        assertEquals(-1L, state.chainPatron[row]);
        assertEquals(4L, state.chainActorHouseId[row]);
        assertEquals(9L, state.chainTarget[row]);
        assertEquals(2, state.chainMarketId[row]);
        assertEquals(7, state.chainIndustryId[row]);
        assertEquals(ChainState.ACTIVE, ChainState.fromByte(state.chainState[row]));
        assertEquals(-1, state.chainLastAdvanceTick[row]);
        assertEquals(-1, state.chainResolvedTick[row]);
    }

    @Test
    void growthInitializesUnusedSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.addAutonomousChain(i + 1L, i + 101L, i, i + 10, (byte) 1,
                    ChainArchetype.CONSOLIDATE_STAKE, (short) 10, (byte) 2, i);
        }

        assertEquals(20, state.chainCount);
        assertEquals(20L, state.chainActorHouseId[19]);
        assertEquals(-1L, state.chainPatron[20]);
        assertEquals(-1L, state.chainActorHouseId[20]);
        assertEquals(-1, state.chainMarketId[20]);
        assertEquals(-1, state.chainIndustryId[20]);
        assertEquals(-1, state.chainLastAdvanceTick[20]);
        assertEquals(-1, state.chainResolvedTick[20]);
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[20]));
        assertEquals(0, state.chainPlayerContribution[20]);
        assertEquals(-1, state.chainPlayerLastContributionTick[20]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.chainPlayerConsequenceState[20]));
        assertEquals(-1, state.chainPlayerConsequenceAppliedTick[20]);
    }

    @Test
    void legacyStateBackfillsLifecycleAndInfersPlayerActor() throws Exception {
        CampaignState state = new CampaignState();
        state.addChain(4L, 9L, (byte) 2, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 12, (byte) 3, 40);
        state.chainActorHouseId = null;
        state.chainMarketId = null;
        state.chainIndustryId = null;
        state.chainState = null;
        state.chainLastAdvanceTick = null;
        state.chainResolvedTick = null;
        state.chainPlayerAllegiance = null;
        state.chainPlayerContribution = null;
        state.chainPlayerLastContributionTick = null;
        state.chainPlayerConsequenceState = null;
        state.chainPlayerConsequenceAppliedTick = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.chainActorHouseId);
        assertNotNull(state.chainMarketId);
        assertNotNull(state.chainIndustryId);
        assertNotNull(state.chainState);
        assertNotNull(state.chainLastAdvanceTick);
        assertNotNull(state.chainResolvedTick);
        assertNotNull(state.chainPlayerAllegiance);
        assertNotNull(state.chainPlayerContribution);
        assertNotNull(state.chainPlayerLastContributionTick);
        assertNotNull(state.chainPlayerConsequenceState);
        assertNotNull(state.chainPlayerConsequenceAppliedTick);
        assertEquals(4L, state.chainActorHouseId[0]);
        assertEquals(-1, state.chainMarketId[0]);
        assertEquals(-1, state.chainIndustryId[0]);
        assertEquals(ChainState.ACTIVE, ChainState.fromByte(state.chainState[0]));
        assertEquals(-1, state.chainLastAdvanceTick[0]);
        assertEquals(-1, state.chainResolvedTick[0]);
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[0]));
        assertEquals(0, state.chainPlayerContribution[0]);
        assertEquals(-1, state.chainPlayerLastContributionTick[0]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.chainPlayerConsequenceState[0]));
        assertEquals(-1, state.chainPlayerConsequenceAppliedTick[0]);
    }
}
