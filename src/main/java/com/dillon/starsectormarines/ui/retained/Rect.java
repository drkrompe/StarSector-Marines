package com.dillon.starsectormarines.ui.retained;

/**
 * Axis-aligned rectangle in top-left, Y-down document coordinates.
 *
 * <p>Adapted from MoonLightEngine's retained UI geometry. The Starsector host
 * conversion lives in {@link UiViewport}; layout, painting, and hit-testing all
 * retain this document-space rectangle.
 */
public record Rect(float x, float y, float width, float height) {

    public static final Rect EMPTY = new Rect(0f, 0f, 0f, 0f);

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public Rect intersect(Rect other) {
        float left = Math.max(x, other.x);
        float top = Math.max(y, other.y);
        float right = Math.min(right(), other.right());
        float bottom = Math.min(bottom(), other.bottom());
        return new Rect(left, top, Math.max(0f, right - left), Math.max(0f, bottom - top));
    }

    public Rect inset(Insets insets) {
        return new Rect(
                x + insets.left(),
                y + insets.top(),
                Math.max(0f, width - insets.horizontal()),
                Math.max(0f, height - insets.vertical()));
    }
}
