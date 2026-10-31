package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonConvoysRulesTest {

    private static final int CAP = CordonConvoys.LOADING_CAP_TICKS;

    @Test
    void aConvoyLeavesWhenItsTrucksAreFullOrTheTownIsEmpty() {
        assertFalse(CordonConvoys.leaves(10, 0, 2, 2, false, false), "seats free and people left: it waits");
        assertTrue(CordonConvoys.leaves(10, 0, 2, 2, true, false), "both trucks full");
        assertTrue(CordonConvoys.leaves(0, 0, 2, 2, false, true), "nobody left in the town");
        assertTrue(CordonConvoys.leaves(CAP, 0, 2, 2, false, false), "the cap turns it for home part-loaded");
        assertFalse(CordonConvoys.leaves(10 * CAP, 0, 0, 0, false, true), "a convoy with no live truck never leaves");
    }

    @Test
    void aConvoyWaitsForItsStragglerUpToTheCap() {
        assertFalse(CordonConvoys.leaves(10, 0, 2, 1, true, false), "the truck at the gate is full, one still coming");
        assertFalse(CordonConvoys.leaves(CAP - 1, 0, 2, 1, true, false));
        assertTrue(CordonConvoys.leaves(CAP, 0, 2, 1, true, false));
    }

    @Test
    void aBeatsExtraLoadingHoldsTheConvoyAndSlowsTheBoarding() {
        int extra = 3600;
        assertFalse(CordonConvoys.leaves(extra - 1, extra, 2, 2, true, false));
        assertTrue(CordonConvoys.leaves(extra, extra, 2, 2, true, false));
        assertFalse(CordonConvoys.leaves(extra + CAP - 1, extra, 2, 2, false, false));
        assertTrue(CordonConvoys.leaves(extra + CAP, extra, 2, 2, false, false));
        assertEquals(CordonConvoys.BOARD_TICKS, CordonConvoys.boardTicks(0));
        assertTrue(CordonConvoys.SEATS * CordonConvoys.boardTicks(extra) >= extra,
                "a truck's seats take the extra wait to fill");
    }

    @Test
    void theRoadAheadCallsFirstThenTheSwarmOnATruck() {
        int[] ahead = {10, 20}, onTruck = {3, 4};
        assertArrayEquals(ahead, CordonConvoys.roadCall(ahead, onTruck));
        assertArrayEquals(onTruck, CordonConvoys.roadCall(null, onTruck));
        assertNull(CordonConvoys.roadCall(null, null));
    }

    @Test
    void theSwarmOnAConvoyIsTheNearestBodyWithinReachOfAnyTruck() {
        List<float[]> trucks = List.of(new float[]{100, 100}, new float[]{100, 110});
        assertNull(CordonConvoys.swarmOn(trucks, List.of(new float[]{100, 120})));
        assertArrayEquals(new int[]{104, 110},
                CordonConvoys.swarmOn(trucks, List.of(new float[]{100, 95}, new float[]{104, 110})));
        assertNull(CordonConvoys.swarmOn(List.of(), List.of(new float[]{100, 100})));
    }

    @Test
    void aTruckIsSetDownAtTheFirstCellPastTheEntryItCanSetOutFrom() {
        assertEquals(12, CordonConvoys.setDown(100, 10, i -> i >= 12));
        assertEquals(10, CordonConvoys.setDown(100, 10, i -> true));
        assertEquals(10, CordonConvoys.setDown(100, 10, i -> false), "the entry where no cell near it will do");
        assertEquals(99, CordonConvoys.setDown(100, 99, i -> false));
    }
}
