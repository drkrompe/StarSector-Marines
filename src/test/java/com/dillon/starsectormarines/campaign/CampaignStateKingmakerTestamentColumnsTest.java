package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateKingmakerTestamentColumnsTest {

    @Test
    void sealingIsSourceClaimUniqueAndFreezesContent() {
        CampaignState state = new CampaignState();

        long first = seal(state, 7L, 90);
        long repeated = state.sealKingmakerTestament(7L, 999L, 998L,
                997L, 9, 10, 11, (short) 99,
                -100, -100, -100, -100, 99, 190);

        assertEquals(first, repeated);
        assertEquals(1, state.kingmakerTestamentCount);
        int row = state.kingmakerTestamentIndexForClaim(7L);
        assertEquals(1L, state.kingmakerTestamentId[row]);
        assertEquals(70L, state.kingmakerTestamentSourceChainId[row]);
        assertEquals(3L, state.kingmakerTestamentClaimantHouseId[row]);
        assertEquals(4L, state.kingmakerTestamentDeposedHouseId[row]);
        assertEquals(5, state.kingmakerTestamentSourceFactionId[row]);
        assertEquals(6, state.kingmakerTestamentResultFactionId[row]);
        assertEquals(8, state.kingmakerTestamentMarketId[row]);
        assertEquals(60, state.kingmakerTestamentPlayerContribution[row]);
        assertEquals(10, state.kingmakerTestamentMercy[row]);
        assertEquals(-20, state.kingmakerTestamentIntegrity[row]);
        assertEquals(30, state.kingmakerTestamentStewardship[row]);
        assertEquals(-40, state.kingmakerTestamentInstitutionalism[row]);
        assertEquals(12, state.kingmakerTestamentMoralChoiceCount[row]);
        assertEquals(90, state.kingmakerTestamentSealedTick[row]);
        assertEquals(KingmakerTestamentState.SEALED,
                KingmakerTestamentState.fromByte(
                        state.kingmakerTestamentState[row]));
    }

    @Test
    void growthInitializesUnusedIdentityBoundaryAndTickSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            seal(state, i + 1L, i + 90);
        }

        assertEquals(-1L, state.kingmakerTestamentThroneClaimId[20]);
        assertEquals(-1L, state.kingmakerTestamentSourceChainId[20]);
        assertEquals(-1L, state.kingmakerTestamentClaimantHouseId[20]);
        assertEquals(-1L, state.kingmakerTestamentDeposedHouseId[20]);
        assertEquals(-1, state.kingmakerTestamentSourceFactionId[20]);
        assertEquals(-1, state.kingmakerTestamentResultFactionId[20]);
        assertEquals(-1, state.kingmakerTestamentMarketId[20]);
        assertEquals(-1, state.kingmakerTestamentMoralChoiceCount[20]);
        assertEquals(-1, state.kingmakerTestamentSealedTick[20]);
        assertEquals(KingmakerTestamentState.NONE,
                KingmakerTestamentState.fromByte(
                        state.kingmakerTestamentState[20]));
    }

    @Test
    void legacyStateBackfillsAbsentTableAndRebuildsSequence() throws Exception {
        CampaignState state = new CampaignState();
        seal(state, 7L, 90);
        state.kingmakerTestamentThroneClaimId = null;
        state.kingmakerTestamentSourceChainId = null;
        state.kingmakerTestamentClaimantHouseId = null;
        state.kingmakerTestamentDeposedHouseId = null;
        state.kingmakerTestamentSourceFactionId = null;
        state.kingmakerTestamentResultFactionId = null;
        state.kingmakerTestamentMarketId = null;
        state.kingmakerTestamentPlayerContribution = null;
        state.kingmakerTestamentMercy = null;
        state.kingmakerTestamentIntegrity = null;
        state.kingmakerTestamentStewardship = null;
        state.kingmakerTestamentInstitutionalism = null;
        state.kingmakerTestamentMoralChoiceCount = null;
        state.kingmakerTestamentSealedTick = null;
        state.kingmakerTestamentState = null;
        Field nextId = CampaignState.class.getDeclaredField(
                "nextKingmakerTestamentId");
        nextId.setAccessible(true);
        nextId.setLong(state, 0L);

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.kingmakerTestamentThroneClaimId);
        assertNotNull(state.kingmakerTestamentState);
        assertEquals(-1L, state.kingmakerTestamentThroneClaimId[0]);
        assertEquals(-1L, state.kingmakerTestamentDeposedHouseId[0]);
        assertEquals(-1, state.kingmakerTestamentMoralChoiceCount[0]);
        assertEquals(-1, state.kingmakerTestamentSealedTick[0]);
        assertEquals(2L, seal(state, 8L, 91));
    }

    @Test
    void legacyEmptyStateBackfillsAbsentTable() throws Exception {
        CampaignState state = new CampaignState();
        state.kingmakerTestamentId = null;
        state.kingmakerTestamentThroneClaimId = null;
        state.kingmakerTestamentSourceChainId = null;
        state.kingmakerTestamentClaimantHouseId = null;
        state.kingmakerTestamentDeposedHouseId = null;
        state.kingmakerTestamentSourceFactionId = null;
        state.kingmakerTestamentResultFactionId = null;
        state.kingmakerTestamentMarketId = null;
        state.kingmakerTestamentPlayerContribution = null;
        state.kingmakerTestamentMercy = null;
        state.kingmakerTestamentIntegrity = null;
        state.kingmakerTestamentStewardship = null;
        state.kingmakerTestamentInstitutionalism = null;
        state.kingmakerTestamentMoralChoiceCount = null;
        state.kingmakerTestamentSealedTick = null;
        state.kingmakerTestamentState = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.kingmakerTestamentId);
        assertEquals(-1L, state.kingmakerTestamentThroneClaimId[0]);
        assertEquals(-1, state.kingmakerTestamentSourceFactionId[0]);
        assertEquals(-1, state.kingmakerTestamentMoralChoiceCount[0]);
        assertEquals(KingmakerTestamentState.NONE,
                KingmakerTestamentState.fromByte(
                        state.kingmakerTestamentState[0]));
    }

    private static long seal(CampaignState state, long claimId, int day) {
        return state.sealKingmakerTestament(claimId, 70L, 3L, 4L,
                5, 6, 8, (short) 60,
                10, -20, 30, -40, 12, day);
    }
}
