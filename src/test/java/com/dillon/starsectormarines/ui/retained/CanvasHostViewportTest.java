package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasHostViewportTest {

    @Test
    void mapsCanvasSurfaceIntoAbsoluteBottomLeftHostSpace() {
        CanvasHostViewport viewport = new CanvasHostViewport(
                100f, 200f, 900f, 520f, 600, 520);

        assertEquals(1.5f, viewport.scaleX(), 1e-6f);
        assertEquals(1f, viewport.scaleY(), 1e-6f);
        assertEquals(550f, viewport.screenXForCanvas(300f), 1e-6f);
        assertEquals(720f, viewport.screenYForCanvas(0f), 1e-6f);
        assertEquals(200f, viewport.screenYForCanvas(520f), 1e-6f);
    }

    /**
     * A canvas that draws its own primitives around a host pass has to read the
     * pass's coordinates back, and the stretch it converts through is not the
     * same on both axes and turns the Y axis over on the way. Getting that
     * inverse subtly wrong is invisible in a centered frame and only shows as
     * the two layers sliding apart once the camera moves, so it is pinned as a
     * round trip rather than as a pair of expected numbers.
     */
    @Test
    void readsHostCoordinatesBackIntoTheCanvasTheyCameFrom() {
        CanvasHostViewport viewport = new CanvasHostViewport(
                100f, 200f, 900f, 520f, 600, 520);

        for (float canvasX : new float[]{0f, 137f, 300f, 600f}) {
            assertEquals(canvasX,
                    viewport.canvasXForScreen(viewport.screenXForCanvas(canvasX)), 1e-4f);
        }
        for (float canvasY : new float[]{0f, 91f, 260f, 520f}) {
            assertEquals(canvasY,
                    viewport.canvasYForScreen(viewport.screenYForCanvas(canvasY)), 1e-4f);
        }
        // The axis really does turn over: the top of the surface is the top of
        // the host rect, which is its highest Y rather than its lowest.
        assertEquals(0f, viewport.canvasYForScreen(720f), 1e-4f);
        assertEquals(520f, viewport.canvasYForScreen(200f), 1e-4f);
    }
}
