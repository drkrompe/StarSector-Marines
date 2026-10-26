package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CordonWreckPlacementTest {

    @Test
    void theWreckLiesWhereItClosesTheStreetNearestTheSuburb() {
        // Cells 40 to 44 are a square; the street narrows either side of it, nearer behind.
        assertEquals(38, CordonSetPieces.wreckAt(40, 30, 60, i -> i < 39 || i > 47));
        assertEquals(39, CordonSetPieces.wreckAt(40, 30, 60, i -> i == 39 || i == 41));
        assertEquals(41, CordonSetPieces.wreckAt(40, 30, 60, i -> i == 41 || i == 30));
        assertEquals(40, CordonSetPieces.wreckAt(40, 30, 60, i -> true));
    }

    @Test
    void withNowhereNarrowItLiesWhereItWasAsked() {
        assertEquals(40, CordonSetPieces.wreckAt(40, 30, 60, i -> false));
        // Never outside the stretch it may lie on.
        assertEquals(40, CordonSetPieces.wreckAt(40, 35, 45, i -> i < 35 || i > 45));
    }
}
