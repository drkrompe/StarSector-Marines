package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.StakeFootprint.Part;
import com.dillon.starsectormarines.battle.derelict.StakeFootprint.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StakeFootprintTest {

    @Test
    void noTwoPartsOverlapAndEveryPartStaysInsideTheFootprint() {
        FallenHullSite.Rect footprint = StakeFootprint.footprint(0, 0);
        List<Slot> slots = StakeFootprint.SLOTS;
        assertEquals(Part.values().length, slots.size(), "a slot for every part");
        for (int i = 0; i < slots.size(); i++) {
            FallenHullSite.Rect a = slots.get(i).at(0, 0);
            assertTrue(footprint.contains(a.left(), a.bottom()) && footprint.contains(a.right(), a.top()),
                    slots.get(i).part() + " inside the footprint");
            for (int j = i + 1; j < slots.size(); j++) {
                FallenHullSite.Rect b = slots.get(j).at(0, 0);
                boolean overlap = a.left() <= b.right() && b.left() <= a.right() && a.bottom() <= b.top()
                        && b.bottom() <= a.top();
                assertFalse(overlap, slots.get(i).part() + " and " + slots.get(j).part());
            }
        }
        assertArrayEquals(new int[] {0, 0}, StakeFootprint.slot(Part.SPIRE).centre(0, 0), "the spire is the centre");
        assertEquals(-StakeFootprint.HALF, StakeFootprint.slot(Part.STORE).bottom(), "the store on the edge");
    }

    @Test
    void aStageGrowsByFillingMoreSlotsNeverLosingOne() {
        assertTrue(StakeFootprint.parts(SurveyStake.Stage.RAISED)
                .containsAll(StakeFootprint.parts(SurveyStake.Stage.PLANTED)));
        assertTrue(StakeFootprint.parts(SurveyStake.Stage.ESTABLISHED)
                .containsAll(StakeFootprint.parts(SurveyStake.Stage.RAISED)));
        assertEquals(Part.values().length, StakeFootprint.parts(SurveyStake.Stage.ESTABLISHED).size());
    }

    @Test
    void aStakeSeatsWhereItsWholeFootprintIsClearNearestItsPoint() {
        // Clear ground only east of x = 3, in a strip just the footprint's height: it seats at its first clear centre.
        BiPredicate<Integer, Integer> strip = (x, y) -> x > 3 && Math.abs(y) <= StakeFootprint.HALF;
        int[] seat = StakeFootprint.seat(0, 0, 10, strip);
        assertArrayEquals(new int[] {9, 0}, seat);
        for (int[] c : StakeFootprint.cells(seat[0], seat[1])) assertTrue(strip.test(c[0], c[1]), "the footprint is clear");
        assertEquals(StakeFootprint.SIDE * StakeFootprint.SIDE, StakeFootprint.cells(0, 0).size());
        assertNull(StakeFootprint.seat(0, 0, 3, strip), "nowhere clear within the search");
    }
}
