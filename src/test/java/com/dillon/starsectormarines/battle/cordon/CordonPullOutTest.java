package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pull-out's rules: when it is offered, which order counts, and when the company has withdrawn. */
class CordonPullOutTest {

    @Test void pullingOutIsOfferedOnceEnoughAreOutAndNoTruckIsLoading() {
        assertFalse(CordonPullOut.offered(23, 24, false), "short of the number");
        assertFalse(CordonPullOut.offered(24, 24, true), "a truck still loading at the gate");
        assertTrue(CordonPullOut.offered(24, 24, false));
    }

    @Test void anOrderCountsOnlyWhenItPointsIntoTheZone() {
        CordonPullOut zone = new CordonPullOut(50, 50, List.of());
        assertFalse(zone.ordered(List.of()));
        assertFalse(zone.ordered(List.of(new int[]{50, 50 + CordonPullOut.RADIUS + 1})), "just outside");
        assertTrue(zone.ordered(List.of(new int[]{10, 10}, new int[]{53, 47})));
    }

    @Test void theCompanyHasWithdrawnOnlyWithNobodyAheadAndSomeoneInTheZone() {
        assertFalse(CordonPullOut.withdrawn(12, 1, 4), "a Marine still past the line");
        assertFalse(CordonPullOut.withdrawn(12, 0, 0), "nobody holding the zone");
        assertTrue(CordonPullOut.withdrawn(12, 0, 1));
    }
}
