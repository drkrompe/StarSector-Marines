package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictWalkerClassTest {

    @Test
    void eachSizeIsTheWalkerItsWaysAdmit() {
        assertEquals(1, DerelictWalkerClass.MEDIUM.narrowestWay(), "a one-cell door");
        assertEquals(2, DerelictWalkerClass.HEAVY.narrowestWay(), "a corridor");
        assertEquals(3, DerelictWalkerClass.LARGE.narrowestWay(), "a hall join");
        assertFalse(DerelictWalkerClass.LARGE.fits(2), "never a corridor");
    }

    @Test
    void eachFrameKnowsItsSize() {
        assertEquals(DerelictWalkerClass.MEDIUM, DerelictWalkerFrame.ROUNDS.size());
        assertEquals(DerelictWalkerClass.HEAVY, DerelictWalkerFrame.VAULT.size());
        assertEquals(DerelictWalkerClass.LARGE, DerelictWalkerFrame.HALL.size());
        assertTrue(DerelictWalkerFrame.HALL.chassis.automated());
        assertTrue(DerelictWalkerFrame.ROUNDS.chassis.automated());
    }

    @Test
    void onlyTheLargeForcesDoorsAndHallFramesComeOnlyAtTheLargerGrades() {
        assertTrue(DerelictWalkerClass.LARGE.forcesDoors());
        assertFalse(DerelictWalkerClass.HEAVY.forcesDoors());
        assertEquals(0, DerelictWalkerClass.hallFrames(RuinsGrade.WIDESPREAD));
        assertEquals(2, DerelictWalkerClass.hallFrames(RuinsGrade.VAST));
        assertEquals(1, DerelictWalkerClass.roundsWalkers(RuinsGrade.SCATTERED, 1));
        assertEquals(3, DerelictWalkerClass.roundsWalkers(RuinsGrade.EXTENSIVE, 3));
    }

    @Test
    void aHallFrameForcesADeadDoorOnlyByStandingAgainstItLongEnoughAtAStretch() {
        DerelictWalkerClass.Ram ram = new DerelictWalkerClass.Ram();
        assertFalse(ram.tick(DerelictWalkerClass.FORCE_SECONDS - 1f, true));
        assertFalse(ram.tick(0.5f, false), "backing off starts again");
        assertEquals(0f, ram.progress());
        assertFalse(ram.tick(DerelictWalkerClass.FORCE_SECONDS - 1f, true));
        assertTrue(ram.tick(1f, true));
        assertTrue(ram.forced());
        assertFalse(ram.tick(1f, true), "forced once");
    }
}
