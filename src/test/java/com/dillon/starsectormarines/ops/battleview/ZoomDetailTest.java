package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The framing gates, asked directly.
 *
 * <p>A camera and three numbers: no world, no battle, no renderer. What is being
 * checked is that the predicates cut where {@link ZoomDetail} says they cut, and
 * that they cut in the right order — smoke goes on reading after the things
 * sized to a body have stopped, which is the whole reason it has its own
 * threshold.
 */
class ZoomDetailTest {

    /** A camera whose cell is exactly {@code cellPx} on screen. */
    private static BattleCamera at(float cellPx) {
        BattleCamera camera = new BattleCamera(200, 200);
        camera.setViewport(0f, 0f, 1920f, 1080f, cellPx);
        return camera;
    }

    @Test
    void bodyDecorationSurvivesTheFramingsAPlayerFightsAt() {
        // The profile's close and mid framings on both maps under test.
        for (float cellPx : new float[]{54.9f, 27.4f, 24.6f, 12.3f}) {
            assertTrue(ZoomDetail.bodyShadowsVisible(at(cellPx)),
                    cellPx + " px/cell is a framing the battle is fought at");
            assertTrue(ZoomDetail.impactParticlesVisible(at(cellPx)),
                    cellPx + " px/cell is a framing the battle is fought at");
        }
    }

    @Test
    void bodyDecorationIsWithheldWithTheWholeMapOnScreen() {
        // Cover-fit on the canonical Conquest, and on the 280x168 control.
        for (float cellPx : new float[]{3.4f, 6.9f}) {
            assertFalse(ZoomDetail.bodyShadowsVisible(at(cellPx)),
                    cellPx + " px/cell is the whole map on screen");
            assertFalse(ZoomDetail.impactParticlesVisible(at(cellPx)),
                    cellPx + " px/cell is the whole map on screen");
        }
    }

    @Test
    void theThresholdItselfStillDraws() {
        assertTrue(ZoomDetail.bodyShadowsVisible(
                at(ZoomDetail.BODY_DECORATION_MIN_CELL_PX)));
        assertFalse(ZoomDetail.bodyShadowsVisible(
                at(ZoomDetail.BODY_DECORATION_MIN_CELL_PX - 0.01f)));
        assertTrue(ZoomDetail.smokeScatterVisible(
                at(ZoomDetail.SMOKE_SCATTER_MIN_CELL_PX)));
        assertFalse(ZoomDetail.smokeScatterVisible(
                at(ZoomDetail.SMOKE_SCATTER_MIN_CELL_PX - 0.01f)));
    }

    /**
     * Smoke is not decoration and outlives what is. A field several cells across
     * still reads at a framing where the marine beside it is eight pixels, so a
     * gate that took them together would thin the one thing here the simulation
     * agrees is real.
     */
    @Test
    void smokeOutlastsBodyDecoration() {
        assertTrue(ZoomDetail.SMOKE_SCATTER_MIN_CELL_PX
                        < ZoomDetail.BODY_DECORATION_MIN_CELL_PX,
                "smoke must survive framings at which body decoration does not");
        BattleCamera between = at(
                (ZoomDetail.SMOKE_SCATTER_MIN_CELL_PX
                        + ZoomDetail.BODY_DECORATION_MIN_CELL_PX) * 0.5f);
        assertFalse(ZoomDetail.bodyShadowsVisible(between));
        assertTrue(ZoomDetail.smokeScatterVisible(between));
    }

    /**
     * A camera that cannot say how big a cell is gets everything. The gates drop
     * what cannot be read, and not knowing is not that.
     */
    @Test
    void anAbsentCameraIsNotAReasonToWithholdAnything() {
        assertTrue(ZoomDetail.bodyShadowsVisible(null));
        assertTrue(ZoomDetail.impactParticlesVisible(null));
        assertTrue(ZoomDetail.smokeScatterVisible(null));
    }
}
