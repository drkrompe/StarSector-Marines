package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonTownspeopleTest {

    @Test
    void aTruckIsSentOnlyAsManyAsItHasSeatsNobodyIsWalkingTo() {
        assertEquals(6, CordonTownspeople.wanted(6, 0));
        assertEquals(2, CordonTownspeople.wanted(6, 4));
        assertEquals(0, CordonTownspeople.wanted(1, 3));
    }

    @Test
    void theNearestStandingAreSentFirst() {
        List<float[]> standing = List.of(new float[]{10, 10}, new float[]{2, 0}, new float[]{5, 5}, new float[]{1, 1});
        assertEquals(List.of(3, 1), CordonTownspeople.nearest(standing, 0, 0, 2));
        assertEquals(List.of(3, 1, 2, 0), CordonTownspeople.nearest(standing, 0, 0, 9));
        assertEquals(List.of(), CordonTownspeople.nearest(standing, 0, 0, 0));
    }

    @Test
    void aTownspersonBoardsOnlyBesideTheTruck() {
        assertTrue(CordonTownspeople.aboard(13f, 10f, 10f, 10f));
        assertFalse(CordonTownspeople.aboard(15f, 10f, 10f, 10f));
    }

    @Test
    void theCrowdHuddlesNearestTheMiddleOfItsStagingArea() {
        List<int[]> cells = List.of(new int[]{0, 0}, new int[]{4, 0}, new int[]{2, 0}, new int[]{1, 0}, new int[]{3, 0});
        List<int[]> chosen = CordonTownspeople.huddle(cells, 3);
        assertEquals(3, chosen.size());
        assertEquals(2, chosen.get(0)[0], "the middle first");
        assertEquals(List.of(), CordonTownspeople.huddle(List.of(), 3));
    }

    @Test
    void aCountedTownHoldsItsPeopleUntilTheyBoard() {
        CordonTownspeople town = CordonTownspeople.counted(36);
        assertEquals(36, town.left());
        assertEquals(0, town.onField());
        assertEquals(0, town.lost());
    }
}
