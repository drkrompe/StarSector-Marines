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
}
