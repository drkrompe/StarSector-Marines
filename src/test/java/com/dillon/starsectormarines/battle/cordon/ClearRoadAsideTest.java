package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R1's dozer pulling aside once the road is open ({@link ClearRoadBeat#aside}). */
class ClearRoadAsideTest {

    /** A straight road east along y = 50. */
    private static List<int[]> road() {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x <= 100; x++) out.add(new int[]{x, 50});
        return out;
    }

    @Test
    void pullsAsideSquareToTheRoadOnItsRightFirst() {
        assertArrayEquals(new int[]{28, 56}, ClearRoadBeat.aside(road(), 20, (x, y) -> true));
    }

    @Test
    void triesTheOtherSideWhenOneIsWalled() {
        assertArrayEquals(new int[]{28, 44}, ClearRoadBeat.aside(road(), 20, (x, y) -> y < 50));
    }

    @Test
    void triesFartherOutBeforeAnotherPlaceAlongTheRoad() {
        assertArrayEquals(new int[]{28, 62}, ClearRoadBeat.aside(road(), 20, (x, y) -> Math.abs(y - 50) >= 12));
    }

    @Test
    void movesAlongTheRoadWhenNothingBesideTheFirstPlaceIsReachable() {
        assertArrayEquals(new int[]{34, 56}, ClearRoadBeat.aside(road(), 20, (x, y) -> x != 28));
    }

    @Test
    void neverPicksACellOnOrBesideAnotherStretchOfTheRoad() {
        // The road doubles back four cells south of itself: the right side's six cells out lie on its return.
        List<int[]> road = road();
        for (int x = 100; x >= 0; x--) road.add(new int[]{x, 56});
        int[] at = ClearRoadBeat.aside(road, 20, (x, y) -> true);
        for (int[] c : road) {
            boolean beside = Math.abs(c[0] - at[0]) < ClearRoadBeat.OFF_ROAD && Math.abs(c[1] - at[1]) < ClearRoadBeat.OFF_ROAD;
            if (beside) throw new AssertionError("aside " + at[0] + "," + at[1] + " is on the road");
        }
    }

    @Test
    void noneWhereNothingIsReachable() {
        assertNull(ClearRoadBeat.aside(road(), 20, (x, y) -> false));
    }

    @Test
    void aDozerNoNearerItsAsidePlaceForAWhileIsWedgedAndTriesTheNext() {
        assertFalse(ClearRoadBeat.asideStalled(9f, ClearRoadBeat.ASIDE_STALL_TICKS - 1, true));
        assertTrue(ClearRoadBeat.asideStalled(9f, ClearRoadBeat.ASIDE_STALL_TICKS, true));
        // There: nothing to retry.
        assertFalse(ClearRoadBeat.asideStalled(2f, 10 * ClearRoadBeat.ASIDE_STALL_TICKS, true));
        // Its order done, it stopped as near as it stops: not sent on.
        assertFalse(ClearRoadBeat.asideStalled(9f, 10 * ClearRoadBeat.ASIDE_STALL_TICKS, false));
    }

    @Test
    void pushesThroughToAPlaceOnlyWhereNoneIsReachedOverOpenGround() {
        // Open ground reaches only the second place along; the blade would reach the first.
        assertArrayEquals(new int[]{34, 56}, ClearRoadBeat.aside(road(), 20, (x, y) -> x == 34, (x, y) -> true));
        assertArrayEquals(new int[]{28, 56}, ClearRoadBeat.aside(road(), 20, (x, y) -> false, (x, y) -> true));
        assertNull(ClearRoadBeat.aside(road(), 20, (x, y) -> false, (x, y) -> false));
    }
}
