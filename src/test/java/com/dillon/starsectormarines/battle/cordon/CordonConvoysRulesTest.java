package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonConvoysRulesTest {

    private static final int LOADING = CordonConvoys.LOADING_TICKS;

    @Test
    void aConvoyWaitsAtTheGateForItsStraggler() {
        assertFalse(CordonConvoys.leaves(LOADING - 1, LOADING, 2, 2));
        assertTrue(CordonConvoys.leaves(LOADING, LOADING, 2, 2));
        // One truck still on its way: it is waited for, then left to turn back empty.
        assertFalse(CordonConvoys.leaves(LOADING, LOADING, 2, 1));
        assertFalse(CordonConvoys.leaves(LOADING + CordonConvoys.STRAGGLER_TICKS - 1, LOADING, 2, 1));
        assertTrue(CordonConvoys.leaves(LOADING + CordonConvoys.STRAGGLER_TICKS, LOADING, 2, 1));
        assertFalse(CordonConvoys.leaves(10 * LOADING, LOADING, 0, 0));
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
}
