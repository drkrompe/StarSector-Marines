package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
