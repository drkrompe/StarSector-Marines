package com.dillon.starsectormarines.ui.retained;

/**
 * Normalized, top-left/Y-down source rectangle within a canvas sprite asset.
 * A region is independent of the source image's pixel dimensions, so the same
 * producer can address an atlas through either the live or headless backend.
 */
public record CanvasSpriteRegion(float x, float y, float width, float height,
                                 boolean flipX, boolean flipY) {

    public static final CanvasSpriteRegion FULL =
            new CanvasSpriteRegion(0f, 0f, 1f, 1f, false, false);

    public CanvasSpriteRegion(float x, float y, float width, float height) {
        this(x, y, width, height, false, false);
    }

    public CanvasSpriteRegion {
        requireUnit(x, "x");
        requireUnit(y, "y");
        requireUnit(width, "width");
        requireUnit(height, "height");
        if (width == 0f || height == 0f) {
            throw new IllegalArgumentException("sprite region extent must be positive");
        }
        if (x + width > 1f || y + height > 1f) {
            throw new IllegalArgumentException("sprite region must remain inside the source asset");
        }
    }

    /** Returns one row-major cell from an evenly divided sprite sheet. */
    public static CanvasSpriteRegion frame(int columns, int rows, int frameIndex) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("sprite grid dimensions must be positive");
        }
        int frameCount = Math.multiplyExact(columns, rows);
        if (frameIndex < 0 || frameIndex >= frameCount) {
            throw new IllegalArgumentException("frame index " + frameIndex
                    + " is outside a " + columns + "x" + rows + " sprite grid");
        }
        float width = 1f / columns;
        float height = 1f / rows;
        return new CanvasSpriteRegion((frameIndex % columns) * width,
                (frameIndex / columns) * height, width, height);
    }

    public CanvasSpriteRegion flippedVertically() {
        return new CanvasSpriteRegion(x, y, width, height, flipX, !flipY);
    }

    private static void requireUnit(float value, String label) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(label + " must be finite and between zero and one");
        }
    }
}
