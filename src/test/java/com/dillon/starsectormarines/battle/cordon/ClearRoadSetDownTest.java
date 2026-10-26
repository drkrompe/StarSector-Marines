package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where R1's dozer is set down behind the gap ({@link ClearRoadBeat#startIndex}). */
class ClearRoadSetDownTest {

    @Test
    void setsDownAtThePreferredCellWhereItDrivesFromThere() {
        assertEquals(100, ClearRoadBeat.startIndex(100, 0, 111, i -> true));
    }

    @Test
    void triesTheNearestCellsBackBeforeOnWhereThePreferredOneIsBoxedIn() {
        List<Integer> tried = new ArrayList<>();
        assertEquals(91, ClearRoadBeat.startIndex(100, 0, 111, i -> {
            tried.add(i);
            return i == 91 || i == 109;
        }));
        assertEquals(List.of(100, 97, 103, 94, 106, 91), tried);
    }

    @Test
    void neverSetsDownPastTheGapOrBehindTheRoadsEntry() {
        List<Integer> tried = new ArrayList<>();
        assertEquals(-1, ClearRoadBeat.startIndex(100, 95, 104, i -> {
            tried.add(i);
            return false;
        }));
        assertEquals(List.of(100, 97, 103), tried);
    }

    @Test
    void noneWhereItDrivesFromNowhere() {
        assertEquals(-1, ClearRoadBeat.startIndex(100, 0, 111, i -> false));
    }

    @Test
    void neverInAStreetTooNarrowForItToSetOutWhicheverWayItsRouteLeaves() {
        // A street three cells wide: a route along it may be proved, but the dozer turned onto any other heading
        // there swings its blade or its tail into the walls.
        assertFalse(ClearRoadBeat.setsOutFrom(new int[]{10, 20}, ground(9, 11)));
        assertTrue(ClearRoadBeat.setsOutFrom(new int[]{10, 20}, ground(0, 39)));
    }

    /** Open ground from column {@code x0} to {@code x1}, walls either side. */
    private static NavigationGrid ground(int x0, int x1) {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
