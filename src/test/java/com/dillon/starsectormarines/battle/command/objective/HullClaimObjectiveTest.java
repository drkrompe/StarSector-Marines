package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a Long Survey chapter's stakes are down ({@code long-survey-arc.md}, Chapters). */
class HullClaimObjectiveTest {

    @Test
    void aStakeIsDownScrappedStrandedOrGoneAndStandsAtEveryOtherStage() {
        for (SurveyStake.Stage stage : List.of(SurveyStake.Stage.PLANTED, SurveyStake.Stage.RAISED,
                SurveyStake.Stage.ESTABLISHED)) {
            assertFalse(HullClaimObjective.down(new SurveyStake(1, 0, stage)), stage + " stands");
        }
        assertTrue(HullClaimObjective.down(scrapped(1)));
        assertTrue(HullClaimObjective.down(null), "a stake the claim no longer holds is down");
        SurveyStake cut = new SurveyStake(1, 0, SurveyStake.Stage.ESTABLISHED);
        cut.strand();
        assertTrue(HullClaimObjective.down(cut), "cutting its chain brings a stake down without fighting it");
    }

    @Test
    void theNamedStakesMustAllBeDownWhateverElseStands() {
        SurveyStake planted = new SurveyStake(1, 0, SurveyStake.Stage.PLANTED);
        SurveyStake scrapped = scrapped(2);
        SurveyStake other = new SurveyStake(3, 0, SurveyStake.Stage.ESTABLISHED);
        Map<Integer, SurveyStake> claim = Map.of(1, planted, 2, scrapped, 3, other);
        assertFalse(HullClaimObjective.allDown(claim, List.of(1, 2)));
        assertTrue(HullClaimObjective.allDown(claim, List.of(2)), "an unnamed stake standing does not hold it up");
        assertTrue(HullClaimObjective.allDown(claim, List.of(2, 9)));
    }

    private static SurveyStake scrapped(int point) {
        SurveyStake stake = new SurveyStake(point, 0, SurveyStake.Stage.PLANTED);
        stake.kill();
        return stake;
    }
}
