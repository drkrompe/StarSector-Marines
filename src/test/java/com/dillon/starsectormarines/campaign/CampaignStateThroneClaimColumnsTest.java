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
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[row]));
        assertEquals(0, state.throneClaimPlayerContribution[row]);
        assertEquals(-1, state.throneClaimPlayerLastContributionTick[row]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.throneClaimPlayerConsequenceState[row]));
        assertEquals(-1, state.throneClaimPlayerConsequenceAppliedTick[row]);
        assertEquals(ThroneClaimState.PREPARED,
                ThroneClaimState.fromByte(state.throneClaimState[row]));
        assertEquals(90, state.throneClaimPreparedTick[row]);
        assertEquals(-1, state.throneClaimAppliedTick[row]);
        assertEquals(ThroneClaimConsequenceState.PENDING,
                ThroneClaimConsequenceState.fromByte(
                        state.throneClaimConsequenceState[row]));
        assertEquals(-1, state.throneClaimConsequenceAppliedTick[row]);
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
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[20]));
        assertEquals(0, state.throneClaimPlayerContribution[20]);
        assertEquals(-1, state.throneClaimPlayerLastContributionTick[20]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.throneClaimPlayerConsequenceState[20]));
        assertEquals(-1, state.throneClaimPlayerConsequenceAppliedTick[20]);
        assertEquals(-1, state.throneClaimPreparedTick[20]);
        assertEquals(-1, state.throneClaimAppliedTick[20]);
        assertEquals(ThroneClaimConsequenceState.PENDING,
                ThroneClaimConsequenceState.fromByte(
                        state.throneClaimConsequenceState[20]));
        assertEquals(-1, state.throneClaimConsequenceAppliedTick[20]);
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
    void preparationSnapshotsPlayerAttributionFromSourceChain() {
        CampaignState state = new CampaignState();
        long chain = state.addAutonomousChain(3L, 4L, 5, -1,
                HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 180, (byte) 128, 1);
        int chainRow = state.chainIndex(chain);
        state.chainPlayerAllegiance[chainRow] = CivilWarAllegiance.CLAIMANT.toByte();
        state.chainPlayerContribution[chainRow] = 45;
        state.chainPlayerLastContributionTick[chainRow] = 80;

        long claim = state.prepareThroneClaim(chain, 3L, 11, 12, 5, 90);
        int claimRow = state.throneClaimIndex(claim);

        assertEquals(CivilWarAllegiance.CLAIMANT,
                CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[claimRow]));
        assertEquals(45, state.throneClaimPlayerContribution[claimRow]);
        assertEquals(80, state.throneClaimPlayerLastContributionTick[claimRow]);

        state.chainPlayerAllegiance[chainRow] = CivilWarAllegiance.INCUMBENT.toByte();
        state.chainPlayerContribution[chainRow] = 99;
        assertEquals(CivilWarAllegiance.CLAIMANT,
                CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[claimRow]));
        assertEquals(45, state.throneClaimPlayerContribution[claimRow]);
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
        state.throneClaimPlayerAllegiance = null;
        state.throneClaimPlayerContribution = null;
        state.throneClaimPlayerLastContributionTick = null;
        state.throneClaimPlayerConsequenceState = null;
        state.throneClaimPlayerConsequenceAppliedTick = null;
        state.throneClaimState = null;
        state.throneClaimPreparedTick = null;
        state.throneClaimAppliedTick = null;
        state.throneClaimConsequenceState = null;
        state.throneClaimConsequenceAppliedTick = null;
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
        assertNotNull(state.throneClaimPlayerAllegiance);
        assertNotNull(state.throneClaimPlayerContribution);
        assertNotNull(state.throneClaimPlayerLastContributionTick);
        assertNotNull(state.throneClaimPlayerConsequenceState);
        assertNotNull(state.throneClaimPlayerConsequenceAppliedTick);
        assertEquals(CivilWarAllegiance.NONE,
                CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[0]));
        assertEquals(0, state.throneClaimPlayerContribution[0]);
        assertEquals(-1, state.throneClaimPlayerLastContributionTick[0]);
        assertEquals(CivilWarPlayerConsequenceState.PENDING,
                CivilWarPlayerConsequenceState.fromByte(
                        state.throneClaimPlayerConsequenceState[0]));
        assertEquals(-1, state.throneClaimPlayerConsequenceAppliedTick[0]);
        assertEquals(-1, state.throneClaimPreparedTick[0]);
        assertEquals(-1, state.throneClaimAppliedTick[0]);
        assertNotNull(state.throneClaimConsequenceState);
        assertEquals(ThroneClaimConsequenceState.PENDING,
                ThroneClaimConsequenceState.fromByte(
                        state.throneClaimConsequenceState[0]));
        assertEquals(-1, state.throneClaimConsequenceAppliedTick[0]);
        assertNotNull(state.throneClaimIndexById);
    }
}
