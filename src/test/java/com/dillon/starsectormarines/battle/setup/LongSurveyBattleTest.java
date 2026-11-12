package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.derelict.StakeTargets;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Long Survey's stakes as The Line's lane targets ({@code long-survey-arc.md}, The Line as a city push). */
class LongSurveyBattleTest {

    @Test
    void theStandingStakesAreLaneTargetsInStrandingOrderEstablishedOnesAnyLanes() {
        List<StakeTargets.Target> standing = List.of(
                new StakeTargets.Target(2, 20, 30, SurveyStake.Stage.ESTABLISHED, StakeTargets.By.NODE, 11L, 3),
                new StakeTargets.Target(4, 40, 10, SurveyStake.Stage.PLANTED, StakeTargets.By.PYLON, 12L, 1));

        assertEquals(List.of(new KnownBore("stake-2", true, 20, 30, true), new KnownBore("stake-4", false, 40, 10, true)),
                LongSurveyBattle.stakeTargets(standing));
    }

    @Test
    void theHallFrameStandsInTheChamberNearestTheForgeHallElseInIt() {
        Area forge = new Area(0, 0, 10, 10);
        Area near = new Area(12, 0, 20, 10), far = new Area(40, 0, 50, 10);
        assertEquals(near, LongSurveyBattle.hallPost(List.of(far, forge, near), forge));
        assertEquals(forge, LongSurveyBattle.hallPost(List.of(forge), forge), "an interior with no other hall");
    }
}
