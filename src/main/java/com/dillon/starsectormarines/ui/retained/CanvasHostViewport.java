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

    /**
     * The inverse of {@link #screenXForCanvas}, for a producer placing canvas
     * primitives against something a host pass drew.
     *
     * <p>Both directions are needed because both happen. A canvas hands the
     * host a rectangle to draw a world into; whatever that world put on screen
     * then has to come back the other way for anything the canvas itself draws
     * over or under it, and doing that by hand is how a backdrop ends up
     * sliding against the map it belongs to.
     */
    public float canvasXForScreen(float absoluteScreenX) {
        float scale = scaleX();
        return scale > 0f ? (absoluteScreenX - screenX) / scale : Float.NaN;
    }

    /** The inverse of {@link #screenYForCanvas}; the axis turns over with it. */
    public float canvasYForScreen(float absoluteScreenY) {
        float scale = scaleY();
        return scale > 0f ? (screenY + height - absoluteScreenY) / scale : Float.NaN;
    }
}
