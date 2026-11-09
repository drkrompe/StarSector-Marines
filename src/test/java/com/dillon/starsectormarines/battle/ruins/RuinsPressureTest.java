package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Level;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.ReserveBand;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Situation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsPressureTest {

    /** The company in layer 2 of 4, the interior from layer 1, moving. */
    private static final Situation MOVING = new Situation(false, 2, 1, 4);
    private static final Situation DUG_IN = new Situation(true, 2, 1, 4);

    private static RuinsPressure pressure(int reserve) {
        return new RuinsPressure(reserve, null);
    }

    @Test
    void timeInsideStepsTheSiteAndEachStepSendsAPush() {
        RuinsPressure p = pressure(5);
        assertTrue(p.advance(RuinsPressure.STEP - 1f, false, MOVING).isEmpty());
        assertEquals(Level.QUIET, p.level());
        List<Push> sent = p.advance(1f, false, MOVING);
        assertEquals(1, sent.size());
        assertEquals(Level.STIRRING, p.level());
        assertEquals(4, p.reserve());
    }

    @Test
    void firingHalvesTheTimeToTheNextStep() {
        RuinsPressure p = pressure(5);
        assertEquals(1, p.advance(RuinsPressure.STEP / 2f, true, MOVING).size());
    }

    @Test
    void aLoudActCountsOnTheNextAdvanceAndCanCrossSeveralSteps() {
        RuinsPressure p = pressure(5);
        p.loud(RuinsPressure.STEP * 2f);
        assertEquals(Level.QUIET, p.level());
        assertEquals(2, p.advance(0f, false, MOVING).size());
        assertEquals(Level.ROUSED, p.level());
    }

    @Test
    void theStirNeverFallsAndTheLevelStopsAtHunting() {
        RuinsPressure p = pressure(0);
        p.loud(RuinsPressure.STEP * 10f);
        p.advance(0f, false, MOVING);
        p.loud(-500f);
        assertEquals(RuinsPressure.STEP * 10f, p.stir());
        assertEquals(Level.HUNTING, p.level());
    }

    @Test
    void pushesGrowAtHuntingAndStopWhenTheReserveIsSpent() {
        RuinsPressure p = pressure(4);
        p.loud(RuinsPressure.STEP * 6f);
        List<Push> sent = p.advance(0f, false, MOVING);
        assertEquals(List.of(1, 1, 2), sent.stream().map(Push::squads).toList(), "one, one, then two at hunting");
        assertEquals(0, p.reserve());
        assertEquals(ReserveBand.SPENT, p.reserveBand());
        p.loud(RuinsPressure.STEP);
        assertTrue(p.advance(0f, false, MOVING).isEmpty());
    }

    @Test
    void theReserveReadsFullThenThinnedThenSpent() {
        RuinsPressure p = pressure(2);
        assertEquals(ReserveBand.FULL, p.reserveBand());
        p.advance(RuinsPressure.STEP, false, MOVING);
        assertEquals(ReserveBand.THINNED, p.reserveBand());
    }

    @Test
    void aMovingCompanyIsHuntedFromTheLayerAhead() {
        Push push = RuinsPressure.aim(1, 1, MOVING);
        assertEquals(Aim.HUNT, push.aim());
        assertEquals(3, push.layer());
    }

    @Test
    void aDugInCompanyHasTheWayBackTakenBehindIt() {
        Push push = RuinsPressure.aim(1, 1, DUG_IN);
        assertEquals(Aim.WAY_BACK, push.aim());
        assertEquals(1, push.layer());
    }

    @Test
    void nothingComesUpBehindTheFirstLayerInsideSoTheEntryStaysTheCompanys() {
        Push push = RuinsPressure.aim(1, 1, new Situation(true, 1, 1, 4));
        assertEquals(Aim.HUNT, push.aim());
        assertEquals(2, push.layer());
    }

    @Test
    void atTheLastLayerTheHuntComesUpInTheCompanysOwnLayer() {
        assertEquals(3, RuinsPressure.aim(1, 1, new Situation(false, 3, 1, 4)).layer());
    }

    @Test
    void theBreachThrowsTheWholeReserveOnceAndStopsTheStir() {
        RuinsPressure p = pressure(5);
        p.advance(RuinsPressure.STEP, false, MOVING);
        Push push = p.breach(4);
        assertEquals(4, push.squads());
        assertEquals(Aim.HUNT, push.aim());
        assertEquals(3, push.layer());
        assertNull(p.breach(4));
        p.loud(RuinsPressure.STEP * 3f);
        assertTrue(p.advance(RuinsPressure.STEP, true, MOVING).isEmpty());
        assertEquals(Level.STIRRING, p.level());
    }

    @Test
    void stillnessNeedsTheWholeWindowWithinItsCells() {
        RuinsPressure.Stillness still = new RuinsPressure.Stillness(45f, 5f, 8f);
        for (int t = 0; t < 45; t += 5) still.sample(t, 10f, 10f);
        assertFalse(still.dugIn(), "not yet a whole window");
        still.sample(45f, 14f, 10f);
        assertTrue(still.dugIn());
        still.sample(50f, 30f, 10f);
        assertFalse(still.dugIn(), "moved off");
    }

    @Test
    void stillnessForgetsAMoveOnceItLeavesTheWindow() {
        RuinsPressure.Stillness still = new RuinsPressure.Stillness(45f, 5f, 8f);
        still.sample(0f, 0f, 0f);
        for (int t = 5; t <= 50; t += 5) still.sample(t, 40f, 40f);
        assertTrue(still.dugIn());
    }
}
