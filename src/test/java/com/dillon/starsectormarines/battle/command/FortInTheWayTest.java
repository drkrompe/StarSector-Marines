package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortInTheWayTest {

    @Test
    void aRouteThroughTheFortCrossesIt() {
        assertTrue(ConquestCommand.segmentCrossesBox(120, 10, 120, 200, 100, 90, 140, 150));
    }

    @Test
    void aRouteBesideTheFortDoesNot() {
        assertFalse(ConquestCommand.segmentCrossesBox(120, 10, 120, 200, 150, 90, 230, 150));
    }

    @Test
    void aRouteThatStopsShortOfTheFortDoesNot() {
        assertFalse(ConquestCommand.segmentCrossesBox(120, 10, 120, 80, 100, 90, 140, 150));
    }

    @Test
    void aDiagonalRouteClippingACornerCrossesIt() {
        assertTrue(ConquestCommand.segmentCrossesBox(0, 0, 100, 100, 45, 0, 60, 50));
    }
}
