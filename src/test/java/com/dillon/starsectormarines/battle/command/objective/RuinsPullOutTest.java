package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut.Resolution;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsPullOutTest {

    private static final LandingPad LANDING = new LandingPad(10, 40, 4, 3, LandingPad.Approach.WEST);

    @Test
    void aConfirmedRetreatAlwaysPullsOut() {
        assertEquals(Resolution.PULLED_OUT, RuinsPullOut.resolve(false, true));
    }

    @Test
    void aBattleLostWithoutRetreatingIsLost() {
        assertEquals(Resolution.LOST, RuinsPullOut.resolve(false, false));
    }

    @Test
    void aBoardedHaulIsWon() {
        assertEquals(Resolution.WON, RuinsPullOut.resolve(true, false));
    }

    @Test
    void inPhaseAThePullOutPointIsTheLandingsOwnCircle() {
        List<LandingPad> pads = RuinsPullOut.extractionPads(List.of(LANDING), 10, 40, List.of());

        assertEquals(2, pads.size(), "the landing and the pull-out point at it");
        assertTrue(pads.stream().allMatch(pad -> pad.centerX == 10 && pad.centerY == 40));
    }

    @Test
    void inPhaseBTheEntryAndEveryHeldLiftPointGetACircleBesideTheLanding() {
        int[] court = {120, 30};
        List<LandingPad> pads = RuinsPullOut.extractionPads(List.of(LANDING), 90, 44, List.of(court));

        assertEquals(3, pads.size());
        assertTrue(pads.stream().anyMatch(pad -> pad.centerX == 10 && pad.centerY == 40), "the landing");
        assertTrue(pads.stream().anyMatch(pad -> pad.centerX == 90 && pad.centerY == 44), "the entry");
        assertTrue(pads.stream().anyMatch(pad -> pad.centerX == 120 && pad.centerY == 30), "the held court");
    }
}
