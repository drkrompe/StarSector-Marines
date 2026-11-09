package com.dillon.starsectormarines.battle.world.gen.ruins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a ruins site's landing ground stands across the map ({@code mapgen-nouns.md}, Ruins ground). */
class RuinsLandingPlacementTest {

    @Test
    void theLandingStandsInFromTheEdgeWithTheDelveAheadOfIt() {
        for (int layers = 1; layers <= RuinsGroundPlan.MAX_LAYERS; layers++) {
            int width = RuinsGroundPlan.forLayers(layers).width();
            int left = RuinsDelveStage.landingLeft(width);
            assertTrue(left > 1, "landing at the west edge on a " + width + "-wide map");
            assertTrue(left + RuinsDelveStage.LANDING_DEPTH < width / 3,
                    "landing at " + left + " leaves too little of a " + width + "-wide map for the delve");
        }
    }

    @Test
    void theSmallestGroundStillKeepsTheLandingOffTheEdge() {
        assertTrue(RuinsDelveStage.landingLeft(96) > 1);
    }
}
