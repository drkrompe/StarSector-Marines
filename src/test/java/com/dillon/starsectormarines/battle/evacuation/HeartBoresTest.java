package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartBoresTest {

    @Test
    void theRoadSatelliteStandsBesideTheRoadNotOnIt() {
        List<int[]> road = new ArrayList<>();
        for (int y = 80; y <= 130; y++) road.add(new int[]{200, y});
        assertTrue(HeartBores.besideRoad(road, 215, 130));
        assertFalse(HeartBores.besideRoad(road, 202, 120), "a mouth on the road would block it");
        assertFalse(HeartBores.besideRoad(road, 200, 130 + HeartBores.ROAD_REACH + 1), "out of the road's reach");
        // Broad Front advances along y: the road's far end is its deepest cell, the settlement's near gate.
        assertArrayEquals(new int[]{200, 130}, HeartBores.farEnd(Form.BROAD_FRONT, road));
    }

    @Test
    void theBurnsYoungBoresLineTheRouteThroughItsFirstTwoQuarters() {
        float[][] bands = HeartBores.youngBands(Form.THE_BURN);
        int first = 0, second = 0;
        for (float[] band : bands) {
            assertTrue(band[0] >= 0.25f && band[1] <= 0.75f, "short of the heart's quarter: " + band[0] + ".." + band[1]);
            if (band[1] <= 0.5f) first++; else if (band[0] >= 0.5f) second++;
        }
        assertTrue(first > 0 && second > 0 && first + second == bands.length, "in the first quarter past the start and the second");
        assertEquals(0, HeartBores.youngBands(Form.BROAD_FRONT).length);
    }
}
