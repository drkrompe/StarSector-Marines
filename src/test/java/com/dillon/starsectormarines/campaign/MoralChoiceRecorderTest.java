package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoralChoiceRecorderTest {

    @Test
    void recordsAllAxesAndImmutableSourceExactlyOnce() {
        CampaignState state = new CampaignState();

        assertEquals(MoralChoiceRecorder.Result.RECORDED,
                MoralChoiceRecorder.record(state,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT, 7L,
                        5, -6, 7, -20, 90, 91));

        assertEquals(5, state.moralMercy);
        assertEquals(-6, state.moralIntegrity);
        assertEquals(7, state.moralStewardship);
        assertEquals(-20, state.moralInstitutionalism);
        assertEquals(1, state.moralChoiceCount);
        assertEquals(5, state.moralChoiceMercyDelta[0]);
        assertEquals(-6, state.moralChoiceIntegrityDelta[0]);
        assertEquals(7, state.moralChoiceStewardshipDelta[0]);
        assertEquals(-20, state.moralChoiceInstitutionalismDelta[0]);
        assertEquals(90, state.moralChoiceHappenedTick[0]);
        assertEquals(91, state.moralChoiceRecordedTick[0]);
        assertTrue(MoralChoiceRecorder.hasSource(state,
                MoralChoiceSource.CIVIL_WAR_CLAIMANT, 7L));

        assertEquals(MoralChoiceRecorder.Result.ALREADY_RECORDED,
                MoralChoiceRecorder.record(state,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT, 7L,
                        -100, 100, -100, 100, 92, 93));
        assertEquals(1, state.moralChoiceCount);
        assertEquals(5, state.moralMercy);
        assertEquals(-20, state.moralInstitutionalism);
    }

    @Test
    void clampingStoresOnlyTheDeltaActuallyApplied() {
        CampaignState state = new CampaignState();
        state.moralMercy = 95;
        state.moralIntegrity = -95;
        state.moralStewardship = 100;
        state.moralInstitutionalism = -100;

        MoralChoiceRecorder.record(state,
                MoralChoiceSource.CIVIL_WAR_INCUMBENT, 8L,
                20, -20, 20, -20, 50, 50);

        assertEquals(100, state.moralMercy);
        assertEquals(-100, state.moralIntegrity);
        assertEquals(100, state.moralStewardship);
        assertEquals(-100, state.moralInstitutionalism);
        assertEquals(5, state.moralChoiceMercyDelta[0]);
        assertEquals(-5, state.moralChoiceIntegrityDelta[0]);
        assertEquals(0, state.moralChoiceStewardshipDelta[0]);
        assertEquals(0, state.moralChoiceInstitutionalismDelta[0]);
    }

    @Test
    void rejectsMissingIdentityInvalidTimeAndEmptyMeaning() {
        CampaignState state = new CampaignState();

        assertEquals(MoralChoiceRecorder.Result.INVALID,
                MoralChoiceRecorder.record(state, MoralChoiceSource.NONE,
                        1L, 1, 0, 0, 0, 1, 1));
        assertEquals(MoralChoiceRecorder.Result.INVALID,
                MoralChoiceRecorder.record(state,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                        -1L, 1, 0, 0, 0, 1, 1));
        assertEquals(MoralChoiceRecorder.Result.INVALID,
                MoralChoiceRecorder.record(state,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                        1L, 0, 0, 0, 0, 1, 1));
        assertEquals(MoralChoiceRecorder.Result.INVALID,
                MoralChoiceRecorder.record(state,
                        MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                        1L, 1, 0, 0, 0, 2, 1));
        assertFalse(MoralChoiceRecorder.hasSource(state,
                MoralChoiceSource.CIVIL_WAR_CLAIMANT, 1L));
        assertEquals(0, state.moralChoiceCount);
    }

    @Test
    void sourceNamespacesAreIndependent() {
        CampaignState state = new CampaignState();

        MoralChoiceRecorder.record(state,
                MoralChoiceSource.CIVIL_WAR_CLAIMANT, 7L,
                0, 0, 0, -5, 10, 10);
        MoralChoiceRecorder.record(state,
                MoralChoiceSource.CIVIL_WAR_INCUMBENT, 7L,
                0, 0, 0, 5, 10, 10);

        assertEquals(2, state.moralChoiceCount);
        assertEquals(0, state.moralInstitutionalism);
    }
}
