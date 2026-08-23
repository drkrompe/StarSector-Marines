package com.dillon.starsectormarines.ui.retained;

/**
 * One OpenGL scissor rectangle in physical framebuffer pixels.
 *
 * <p>The retained tree stays in top-left document pixels. This adapter is the
 * only place an overflow clip becomes Starsector's bottom-left framebuffer
 * coordinates, including UI-scale and framebuffer-DPI conversion.
 */
public record FramebufferScissor(int x, int y, int width, int height) {

    public static FramebufferScissor from(UiViewport viewport, Rect clip,
                                          int framebufferWidth, int framebufferHeight,
                                          float uiWidth, float uiHeight) {
        if (viewport == null) throw new IllegalArgumentException("viewport must not be null");
        if (clip == null) throw new IllegalArgumentException("clip must not be null");

        int safeFramebufferWidth = Math.max(0, framebufferWidth);
        int safeFramebufferHeight = Math.max(0, framebufferHeight);
        float scaleX = safeFramebufferWidth / Math.max(1f, uiWidth);
        float scaleY = safeFramebufferHeight / Math.max(1f, uiHeight);

        int left = clamp(pixelBoundary(viewport.screenXFor(clip.x()) * scaleX),
                0, safeFramebufferWidth);
        int bottom = clamp(pixelBoundary(viewport.screenBottomFor(clip) * scaleY),
                0, safeFramebufferHeight);
        int right = clamp(pixelBoundary(viewport.screenXFor(clip.right()) * scaleX),
                0, safeFramebufferWidth);
        int top = clamp(pixelBoundary(viewport.screenTopFor(clip.y()) * scaleY),
                0, safeFramebufferHeight);

        return new FramebufferScissor(left, bottom,
                Math.max(0, right - left), Math.max(0, top - bottom));
    }

    public boolean empty() {
        return width <= 0 || height <= 0;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Converts a continuous window edge to the first pixel-center index on or
     * after it. Using floor/ceil on opposite sides would expand a fractional UI
     * clip and allow a one-pixel bleed at scaled boundaries.
     */
    private static int pixelBoundary(float value) {
        return (int) Math.ceil(value - 0.5f);
    }
}
