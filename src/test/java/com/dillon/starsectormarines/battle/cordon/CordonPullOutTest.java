package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pull-out's rules: when it is offered, which order counts, where the zone lies and when the hold is done. */
class CordonPullOutTest {

    @Test void pullingOutIsOfferedOnceEnoughAreOutAndNoTruckIsLoading() {
        assertFalse(CordonPullOut.offered(23, 24, false), "short of the number");
        assertFalse(CordonPullOut.offered(24, 24, true), "a truck still loading at the gate");
        assertTrue(CordonPullOut.offered(24, 24, false));
    }

    @Test void anOrderCountsOnlyWhenItPointsIntoTheZone() {
        CordonPullOut zone = new CordonPullOut(40, 40, 60, 50, List.of());
        assertFalse(zone.ordered(List.of()));
        assertFalse(zone.ordered(List.of(new int[]{50, 51})), "just outside");
        assertTrue(zone.ordered(List.of(new int[]{10, 10}, new int[]{60, 40})));
    }

    @Test void theZoneIsTheFencesNorthEastQuarterKeptInFromTheFence() {
        assertArrayEquals(new int[]{50, 2 + CordonPullOut.INSET, 80 - CordonPullOut.INSET, 21},
                CordonPullOut.quadrant(new int[]{20, 2, 80, 40}));
        assertNull(CordonPullOut.quadrant(null));
    }

    @Test void theHoldRunsWhileTheZoneIsHeldAndStartsAgainWhenItEmpties() {
        assertEquals(3, CordonPullOut.held(2, 1));
        assertEquals(0, CordonPullOut.held(250, 0), "an empty zone resets the count");
        assertFalse(CordonPullOut.withdrawn(12, 0, 299, 300), "not held long enough");
        assertFalse(CordonPullOut.withdrawn(12, 1, 300, 300), "a Marine still past the line");
        assertTrue(CordonPullOut.withdrawn(12, 0, 300, 300));
    }
}
