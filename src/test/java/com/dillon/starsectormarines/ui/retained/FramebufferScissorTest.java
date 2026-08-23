package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FramebufferScissorTest {

    @Test
    void convertsTopLeftDocumentClipToBottomLeftFramebufferPixels() {
        UiViewport viewport = new UiViewport(10f, 20f, 100f, 80f);
        Rect clip = new Rect(5f, 10f, 20f, 30f);

        FramebufferScissor scissor = FramebufferScissor.from(
                viewport, clip, 400, 200, 200f, 100f);

        assertEquals(new FramebufferScissor(30, 120, 40, 60), scissor);
    }

    @Test
    void clampsAClipThatExtendsPastTheFramebuffer() {
        UiViewport viewport = new UiViewport(0f, 0f, 100f, 100f);
        Rect clip = new Rect(-20f, -10f, 150f, 140f);

        FramebufferScissor scissor = FramebufferScissor.from(
                viewport, clip, 200, 200, 100f, 100f);

        assertEquals(new FramebufferScissor(0, 0, 200, 200), scissor);
    }

    @Test
    void fractionalUiScaleDoesNotExpandAcrossAnOutsidePixelCenter() {
        UiViewport viewport = new UiViewport(0f, 0f, 100f, 100f);
        Rect clip = new Rect(15f, 15f, 20f, 20f);

        FramebufferScissor scissor = FramebufferScissor.from(
                viewport, clip, 125, 125, 100f, 100f);

        assertEquals(new FramebufferScissor(19, 81, 25, 25), scissor);
    }
}
