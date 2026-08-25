package com.dillon.starsectormarines.ui.retained;

/** Absolute live-host rectangle corresponding to one canvas content surface. */
public record CanvasHostViewport(float screenX, float screenY,
                                 float width, float height,
                                 int surfaceWidth, int surfaceHeight) {

    public CanvasHostViewport {
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0f || height < 0f || surfaceWidth < 0 || surfaceHeight < 0) {
            throw new IllegalArgumentException("host viewport geometry must be valid");
        }
    }

    public float scaleX() {
        return surfaceWidth == 0 ? 0f : width / surfaceWidth;
    }

    public float scaleY() {
        return surfaceHeight == 0 ? 0f : height / surfaceHeight;
    }

    public float screenXForCanvas(float canvasX) {
        return screenX + canvasX * scaleX();
    }

    /** Converts top-left, Y-down canvas coordinates to the host's Y-up space. */
    public float screenYForCanvas(float canvasY) {
        return screenY + height - canvasY * scaleY();
    }
}
