package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void aTenderStandsForEachZoneWithAHeavyAboveScattered() {
        assertEquals(0, DerelictWalkerClass.tenders(RuinsGrade.SCATTERED, 1));
        assertEquals(2, DerelictWalkerClass.tenders(RuinsGrade.WIDESPREAD, 2));
        assertTrue(DerelictWalkerClass.SMALL.fits(1), "the tender passes a one-cell door");
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

    @Test
    void aRammedDoorOpensItsDeadLeavesAndIsTornWideEnoughForTheHallFrame() {
        RuinsDelve.LeafDoor narrow = new RuinsDelve.LeafDoor(new RuinsDelve.Area(10, 5, 11, 5), true, false,
                RuinsDelve.DoorStance.ANSWERS);
        List<int[]> torn = DerelictWalkerClass.tornCells(narrow);
        assertEquals(2, torn.size(), "a jamb cell each side: four cells fit the frame");
        assertArrayEquals(new int[] {9, 5}, torn.get(0));
        assertArrayEquals(new int[] {12, 5}, torn.get(1));
        RuinsDelve.LeafDoor dead = new RuinsDelve.LeafDoor(new RuinsDelve.Area(3, 0, 3, 2), false, false,
                RuinsDelve.DoorStance.DEAD);
        List<int[]> opened = DerelictWalkerClass.tornCells(dead);
        assertEquals(2, opened.size(), "three cells already fit: only its leaves' cells open");
        assertArrayEquals(new int[] {3, 0}, opened.get(0));
        assertArrayEquals(new int[] {3, 2}, opened.get(1));
    }

    @Test
    void aDoorTornWiderStandsAcrossItsOpenedJambsAndADoorWideEnoughIsNotWidened() {
        RuinsDelve.LeafDoor narrow = new RuinsDelve.LeafDoor(new RuinsDelve.Area(10, 5, 11, 5), true, false,
                RuinsDelve.DoorStance.ANSWERS);
        assertEquals(new RuinsDelve.Area(9, 5, 12, 5), DerelictWalkerClass.tornSpan(narrow));
        RuinsDelve.LeafDoor dead = new RuinsDelve.LeafDoor(new RuinsDelve.Area(3, 0, 3, 2), false, false,
                RuinsDelve.DoorStance.DEAD);
        assertNull(DerelictWalkerClass.tornSpan(dead));
    }
}
