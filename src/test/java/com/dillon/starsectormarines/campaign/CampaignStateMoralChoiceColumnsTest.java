package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateMoralChoiceColumnsTest {

    @Test
    void appendSnapshotsSourceDeltasAndTicks() {
        CampaignState state = new CampaignState();

        long id = state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                77L, (short) 1, (short) -2, (short) 3, (short) -20,
                90, 91);

        assertEquals(1L, id);
        assertEquals(1, state.moralChoiceCount);
        assertEquals(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                MoralChoiceSource.fromByte(state.moralChoiceSourceType[0]));
        assertEquals(77L, state.moralChoiceSourceId[0]);
        assertEquals(1, state.moralChoiceMercyDelta[0]);
        assertEquals(-2, state.moralChoiceIntegrityDelta[0]);
        assertEquals(3, state.moralChoiceStewardshipDelta[0]);
        assertEquals(-20, state.moralChoiceInstitutionalismDelta[0]);
        assertEquals(90, state.moralChoiceHappenedTick[0]);
        assertEquals(91, state.moralChoiceRecordedTick[0]);
        assertEquals(0, state.moralMercy);
        assertEquals(0, state.moralIntegrity);
        assertEquals(0, state.moralStewardship);
        assertEquals(0, state.moralInstitutionalism);
    }

    @Test
    void growthInitializesUnusedSourceAndTickSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_INCUMBENT,
                    i + 1L, (short) 0, (short) 0, (short) 0, (short) 5,
                    i, i + 1);
        }

        assertEquals(20, state.moralChoiceCount);
        assertEquals(MoralChoiceSource.NONE,
                MoralChoiceSource.fromByte(state.moralChoiceSourceType[20]));
        assertEquals(-1L, state.moralChoiceSourceId[20]);
        assertEquals(0, state.moralChoiceMercyDelta[20]);
        assertEquals(0, state.moralChoiceInstitutionalismDelta[20]);
        assertEquals(-1, state.moralChoiceHappenedTick[20]);
        assertEquals(-1, state.moralChoiceRecordedTick[20]);
    }

    @Test
    void legacyStateBackfillsLedgerAndRebuildsSequence() throws Exception {
        CampaignState state = new CampaignState();
        state.appendMoralChoice(MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                7L, (short) 0, (short) 0, (short) 0, (short) -5,
                10, 11);
        state.moralChoiceSourceType = null;
        state.moralChoiceSourceId = null;
        state.moralChoiceMercyDelta = null;
        state.moralChoiceIntegrityDelta = null;
        state.moralChoiceStewardshipDelta = null;
        state.moralChoiceInstitutionalismDelta = null;
        state.moralChoiceHappenedTick = null;
        state.moralChoiceRecordedTick = null;
        Field nextId = CampaignState.class.getDeclaredField("nextMoralChoiceId");
        nextId.setAccessible(true);
        nextId.setLong(state, 0L);

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.moralChoiceSourceType);
        assertNotNull(state.moralChoiceSourceId);
        assertNotNull(state.moralChoiceMercyDelta);
        assertNotNull(state.moralChoiceIntegrityDelta);
        assertNotNull(state.moralChoiceStewardshipDelta);
        assertNotNull(state.moralChoiceInstitutionalismDelta);
        assertNotNull(state.moralChoiceHappenedTick);
        assertNotNull(state.moralChoiceRecordedTick);
        assertEquals(-1L, state.moralChoiceSourceId[0]);
        assertEquals(-1, state.moralChoiceHappenedTick[0]);
        assertEquals(2L, state.appendMoralChoice(
                MoralChoiceSource.CIVIL_WAR_INCUMBENT, 8L,
                (short) 0, (short) 0, (short) 0, (short) 5,
                12, 13));
    }

    @Test
    void legacyEmptyStateBackfillsAbsentLedger() throws Exception {
        CampaignState state = new CampaignState();
        state.moralChoiceId = null;
        state.moralChoiceSourceType = null;
        state.moralChoiceSourceId = null;
        state.moralChoiceMercyDelta = null;
        state.moralChoiceIntegrityDelta = null;
        state.moralChoiceStewardshipDelta = null;
        state.moralChoiceInstitutionalismDelta = null;
        state.moralChoiceHappenedTick = null;
        state.moralChoiceRecordedTick = null;

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNotNull(state.moralChoiceId);
        assertEquals(MoralChoiceSource.NONE,
                MoralChoiceSource.fromByte(state.moralChoiceSourceType[0]));
        assertEquals(-1L, state.moralChoiceSourceId[0]);
        assertEquals(-1, state.moralChoiceHappenedTick[0]);
        assertEquals(-1, state.moralChoiceRecordedTick[0]);
    }
}
