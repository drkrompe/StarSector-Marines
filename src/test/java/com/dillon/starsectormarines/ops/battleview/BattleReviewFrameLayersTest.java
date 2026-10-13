package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A review frame is drawn by the headless Java2D drain, which cannot draw custom text labels. The mission-marker
 * layer's cards are a solid plate with their words drawn as such labels, so in a review frame they were empty black
 * panels across the map. The layer stays in the live view; it is only left out of review frames.
 */
class BattleReviewFrameLayersTest {

    @Test
    void reviewFramesDoNotDrawTheMissionCardPanels() {
        assertFalse(BattleReviewFrameRenderer.REVIEW_LAYERS.contains(RenderLayer.MISSION_MARKERS));
    }

    @Test
    void reviewFramesStillDrawTheWorldAndTheCompoundBeacons() {
        assertTrue(BattleReviewFrameRenderer.REVIEW_LAYERS.contains(RenderLayer.GROUND));
        assertTrue(BattleReviewFrameRenderer.REVIEW_LAYERS.contains(RenderLayer.UNITS));
        assertTrue(BattleReviewFrameRenderer.REVIEW_LAYERS.contains(RenderLayer.COMPOUND));
    }
}
