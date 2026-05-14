package com.dillon.starsectormarines.ops;

/**
 * Immutable rect for one column's body (the area below the header). Y is at
 * the bottom of the column body (Starsector UI convention, Y-up); height
 * extends upward. {@link #headerTextY} is the top edge of the column canvas
 * where the column's title label is drawn.
 */
public final class ColumnRect {

    public final float x, y, w, h;
    public final float headerTextY;

    public ColumnRect(float x, float y, float w, float h, float headerTextY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.headerTextY = headerTextY;
    }
}
