package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Long Survey's stakes as The Line's lane targets ({@code long-survey-arc.md}, The Line as a city push). */
class LongSurveyBattleTest {

    @Test
    void eachSeatedStakeIsALaneTargetEstablishedOnesAnyLanesAndADownStakeNoLongerLiving() {
        SurveyStake planted = new SurveyStake(4, 0, SurveyStake.Stage.PLANTED);
        SurveyStake established = new SurveyStake(2, 0, SurveyStake.Stage.ESTABLISHED);
        SurveyStake cut = new SurveyStake(7, 2, SurveyStake.Stage.RAISED);
        cut.strand();
        SurveyStake unseated = new SurveyStake(9, 0, SurveyStake.Stage.PLANTED);
        Map<Integer, SurveyStake> claim = Map.of(4, planted, 2, established, 7, cut, 9, unseated);
        Map<Integer, int[]> seats = Map.of(4, new int[] {40, 10}, 2, new int[] {20, 30}, 7, new int[] {70, 12});

        List<KnownBore> targets = LongSurveyBattle.stakeTargets(claim, seats::get);

        assertEquals(List.of(new KnownBore("stake-2", true, 20, 30, true), new KnownBore("stake-4", false, 40, 10, true),
                new KnownBore("stake-7", false, 70, 12, false)), targets);
    }

    @Test
    void theHallFrameStandsInTheChamberNearestTheForgeHallElseInIt() {
        Area forge = new Area(0, 0, 10, 10);
        Area near = new Area(12, 0, 20, 10), far = new Area(40, 0, 50, 10);
        assertEquals(near, LongSurveyBattle.hallPost(List.of(far, forge, near), forge));
        assertEquals(forge, LongSurveyBattle.hallPost(List.of(forge), forge), "an interior with no other hall");
    }
}
