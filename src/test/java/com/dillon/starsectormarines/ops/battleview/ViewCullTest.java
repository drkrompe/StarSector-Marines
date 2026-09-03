package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one thing {@link ViewCull} must never get wrong: rejecting something that
 * would have been drawn.
 *
 * <p>The pixel comparison in {@code RenderBudgetEvidence} is the acceptance for
 * the lever as a whole and needs a real driver; this asks the arithmetic
 * directly, on the cases a real scene only reaches by accident.
 */
class ViewCullTest {

    /** A camera framing 20x10 cells at 32 px each, viewport at the surface origin. */
    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(200, 200);
        camera.setViewport(0f, 0f, 640f, 320f, 32f);
        camera.centerOn(100f, 100f);
        return camera;
    }

    @Test
    void keepsABodyWhoseCentreIsOutsideButWhoseSpriteIsNot() {
        BattleCamera camera = camera();
        ViewCull view = ViewCull.of(camera);
        assertTrue(view.bounded(), "a sized camera must actually bound something");

        // The right edge, in cells. A body sitting two cells past it with a
        // four-cell sprite still reaches back over the boundary.
        float rightEdgeCells = camera.screenToCellX(640f);
        assertTrue(view.visible(rightEdgeCells + 1.9f, 100f, 4f),
                "a body whose sprite overlaps the edge must survive the cull");
        assertFalse(view.visible(rightEdgeCells + 2.1f, 100f, 4f),
                "and one whose sprite clears the edge entirely must not");
    }

    @Test
    void anExtentlessBodyIsCulledAtTheBoundaryAndKeptInside() {
        ViewCull view = ViewCull.of(camera());
        BattleCamera camera = camera();
        float topEdgeCells = camera.screenToCellY(320f);
        assertTrue(view.visible(100f, topEdgeCells - 0.1f, 0f));
        assertFalse(view.visible(100f, topEdgeCells + 0.1f, 0f));
    }

    /**
     * An unsized camera cannot say what is on screen, so it withholds nothing —
     * the same reasoning as {@link ZoomDetail}'s framing gates. A host that never
     * called {@code setViewport} draws what it always drew.
     */
    @Test
    void aCameraThatCannotSayRejectsNothing() {
        ViewCull unsized = ViewCull.of(new BattleCamera(200, 200));
        assertFalse(unsized.bounded());
        assertTrue(unsized.visible(-5000f, -5000f, 0f));
        assertTrue(ViewCull.of(null).visible(9999f, 9999f, 0f));
        assertTrue(ViewCull.EVERYTHING.visible(9999f, 9999f, 0f));
        assertTrue(ViewCull.EVERYTHING.visibleScreen(-9999f, -9999f, 0f));
        assertTrue(ViewCull.EVERYTHING.visibleSpan(-9f, -9f, -8f, -8f));
    }

    @Test
    void theSwitchIsReadWhenItIsAskedRatherThanAtClassLoad() {
        String previous = System.getProperty(ViewCull.PROPERTY);
        try {
            System.setProperty(ViewCull.PROPERTY, "false");
            assertFalse(ViewCull.enabled());
            assertFalse(ViewCull.of(camera()).bounded(),
                    "the control run must reject nothing at all");
            System.setProperty(ViewCull.PROPERTY, "true");
            assertTrue(ViewCull.enabled());
            assertTrue(ViewCull.of(camera()).bounded());
        } finally {
            if (previous == null) System.clearProperty(ViewCull.PROPERTY);
            else System.setProperty(ViewCull.PROPERTY, previous);
        }
    }

    @Test
    void screenSpaceDecorationIsBoundedByTheViewportItself() {
        ViewCull view = ViewCull.of(camera());
        assertTrue(view.visibleScreen(320f, 160f, 0f), "the middle of the viewport");
        assertTrue(view.visibleScreen(660f, 160f, 60f),
                "a label 60px wide centred 20px past the right edge still shows");
        assertFalse(view.visibleScreen(700f, 160f, 60f),
                "and one clear of it does not");
    }

    @Test
    void aSpanOverlappingTheViewSurvives() {
        BattleCamera camera = camera();
        ViewCull view = ViewCull.of(camera);
        float left = camera.screenToCellX(0f);
        assertTrue(view.visibleSpan(left - 10f, 99f, left + 0.5f, 101f));
        assertFalse(view.visibleSpan(left - 10f, 99f, left - 0.5f, 101f));
    }
}
