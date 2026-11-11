package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndGameBranchesTest {

    /** Morrow's ship through all three chapters of A Working Ship, ending with the departure. */
    private static ShepherdRecoveryProgress departed() {
        var p = new ShepherdRecoveryProgress();
        String id = p.missionId();
        p.begin(id, "Asharu");
        p.settle(id, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true);
        id = p.missionId();
        p.begin(id, "Asharu");
        p.settleColdStart(id, "Asharu", true, ShepherdRecoveryProgress.COLD_PAYMENT, 0, true, true, true, true);
        id = p.missionId();
        p.begin(id, "Asharu");
        p.settleWorkingPassage(id, "Asharu", true, 0, 0, true, false, false, "ship", 15);
        assertTrue(p.departed());
        return p;
    }

    @Test
    void theBranchesOpenOnlyWhenMorrowHasDeparted() {
        var state = new CampaignState();
        assertFalse(EndGameBranches.open(null));
        assertFalse(EndGameBranches.open(state));
        state.shepherdRecovery = new ShepherdRecoveryProgress();
        assertFalse(EndGameBranches.open(state));
        state.shepherdRecovery = departed();
        assertTrue(EndGameBranches.open(state));
    }

    @Test
    void takingUpEitherArcNeverClosesTheOther() {
        var state = new CampaignState();
        state.shepherdRecovery = departed();
        var survey = new LongSurveyProgress();
        String fall = survey.missionId(LongSurveyProgress.Chapter.THE_FALL, false);
        survey.begin(fall);
        state.longSurvey = survey;
        assertTrue(EndGameBranches.open(state));
        survey.settle(fall, true, LongSurveyProgress.Chapter.THE_FALL.fee);
        var bloom = new WellhollowBloomProgress();
        String breach = bloom.missionId(WellhollowBloomProgress.Chapter.COLONY_BREACH, false);
        bloom.begin(breach);
        state.wellhollowBloom = bloom;
        assertTrue(EndGameBranches.open(state));
    }
}
