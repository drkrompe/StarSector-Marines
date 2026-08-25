package com.dillon.starsectormarines.ui.retained;

/**
 * The host rectangle and the one conversion between Starsector UI coordinates
 * and retained document coordinates.
 */
public record UiViewport(float screenX, float screenY, float width, float height,
                         float documentScale) {

    public UiViewport {
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(documentScale)
                || width < 0f || height < 0f || documentScale <= 0f) {
            throw new IllegalArgumentException(
                    "Viewport geometry must be finite and its size/scale valid");
        }
    }

    public UiViewport(float screenX, float screenY, float width, float height) {
        this(screenX, screenY, width, height, 1f);
    }

    /**
     * Builds a host viewport that shrinks a reference presentation on physically
     * smaller panels without neutralizing an explicit Starsector UI-scale choice.
     * Larger panels remain at 1:1 document scale and expose their extra logical
     * room to responsive layout.
     */
    public static UiViewport relative(float screenX, float screenY,
                                      float width, float height,
                                      float uiScale,
                                      float referenceWidth,
                                      float referenceHeight) {
        if (!Float.isFinite(uiScale) || uiScale <= 0f
                || !Float.isFinite(referenceWidth) || referenceWidth <= 0f
                || !Float.isFinite(referenceHeight) || referenceHeight <= 0f) {
            throw new IllegalArgumentException(
                    "UI scale and reference dimensions must be finite and positive");
        }
        float physicalFit = Math.min(
                width * uiScale / referenceWidth,
                height * uiScale / referenceHeight);
        return new UiViewport(screenX, screenY, width, height,
                physicalFit > 0f ? Math.min(1f, physicalFit) : 1f);
    }

    public float documentWidth() {
        return width / documentScale;
    }

    public float documentHeight() {
        return height / documentScale;
    }

    public float documentX(float absoluteScreenX) {
        return (absoluteScreenX - screenX) / documentScale;
    }

    public float documentY(float absoluteScreenY) {
        return (screenY + height - absoluteScreenY) / documentScale;
    }

    public float screenXFor(float documentX) {
        return screenX + documentX * documentScale;
    }

    public float screenTopFor(float documentY) {
        return screenY + height - documentY * documentScale;
    }

    public float screenBottomFor(Rect documentRect) {
        return screenY + height - documentRect.bottom() * documentScale;
    }
}
