package com.dillon.starsectormarines.render2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ribbon's quads can be read back off the batch, which is how a drain with no GL turns the same geometry into
 * pixels instead of a {@code glBegin} block.
 */
class RibbonBatchQuadsTest {

    private static final ContrailStyle STYLE = new ContrailStyle(1f, 0.5f, 0.5f,
            1f, 0.5f, 0.25f, 1f, 1f, 0.5f, 0.25f, 0f, 0.05f, false);

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(20, 20);
        camera.setViewport(0f, 0f, 200f, 200f, 10f);
        return camera;
    }

    @Test
    void aStraightTrailIsOneQuadPerSegmentSymmetricAboutItsAxis() {
        ContrailTrail trail = new ContrailTrail(STYLE, 8);
        trail.pushSample(2f, 5f);
        trail.pushSample(4f, 5f);
        trail.pushSample(6f, 5f);
        BattleCamera camera = camera();
        RibbonBatch batch = new RibbonBatch(2);

        batch.append(trail, camera, 1f);

        assertEquals(2, batch.quadCount());
        float axisY = camera.cellToScreenY(5f);
        for (int quad = 0; quad < 2; quad++) {
            // Corners wind left-a, right-a, right-b, left-b: the a corners share an x, and the sides straddle the axis.
            assertEquals(batch.vertexX(quad, 0), batch.vertexX(quad, 1), 1e-3f);
            assertEquals(axisY, (batch.vertexY(quad, 0) + batch.vertexY(quad, 1)) / 2f, 1e-3f);
            assertEquals(5f, Math.abs(batch.vertexY(quad, 0) - batch.vertexY(quad, 1)) / 2f, 1e-3f);
        }
    }

    @Test
    void theAlphaMultiplierIsFoldedIntoEveryCorner() {
        ContrailTrail trail = new ContrailTrail(STYLE, 8);
        trail.pushSample(2f, 5f);
        trail.pushSample(4f, 5f);
        RibbonBatch batch = new RibbonBatch(1);

        batch.append(trail, camera(), 0.5f);

        // The newest sample is age zero, so it carries the style's start alpha of 1, halved by the multiplier.
        assertEquals(0.5f, batch.vertexChannel(0, 2, 3), 1e-4f);
        assertTrue(batch.vertexChannel(0, 0, 3) <= 0.5f);
    }

    @Test
    void aTrailWithOneSampleDrawsNothingAndClearEmptiesTheQueue() {
        ContrailTrail trail = new ContrailTrail(STYLE, 8);
        trail.pushSample(2f, 5f);
        RibbonBatch batch = new RibbonBatch(1);
        batch.append(trail, camera(), 1f);
        assertEquals(0, batch.quadCount());

        trail.pushSample(4f, 5f);
        batch.append(trail, camera(), 1f);
        assertEquals(1, batch.quadCount());
        batch.clear();
        assertEquals(0, batch.quadCount());
    }
}
