package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloomHeartSupplyRoadTest {

    @Test
    void aStreetWayIsWidenedToARoadsWidthOnceACellInsideTheMap() {
        // A way two cells long along the map's edge: each cell's 5x5 square, overlapping squares counted once, cut at the edge.
        List<int[]> road = BloomHeartSetup.widened(List.of(new int[]{0, 5}, new int[]{1, 5}), 2, 20, 20);
        assertEquals(4 * 5, road.size());
    }
}
