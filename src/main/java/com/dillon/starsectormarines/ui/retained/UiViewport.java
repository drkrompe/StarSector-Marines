package com.dillon.starsectormarines.ui.retained;

/**
 * The host rectangle and the one conversion between Starsector UI coordinates
 * and retained document coordinates.
 */
public record UiViewport(float screenX, float screenY, float width, float height) {

    public float documentX(float absoluteScreenX) {
        return absoluteScreenX - screenX;
    }

    public float documentY(float absoluteScreenY) {
        return screenY + height - absoluteScreenY;
    }

    public float screenXFor(float documentX) {
        return screenX + documentX;
    }

    public float screenTopFor(float documentY) {
        return screenY + height - documentY;
    }

    public float screenBottomFor(Rect documentRect) {
        return screenY + height - documentRect.bottom();
    }
}
