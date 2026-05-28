package com.dillon.starsectormarines.battle.world.gen;

/**
 * One terminal cell of a BSP partition — the addressable "block" a filler
 * paints into. Coordinates are inclusive nav-grid cell bounds; the rect
 * excludes the road frame that surrounds it (that belongs to the neighboring
 * road strip, not the leaf).
 *
 * <p>{@link #kind} is assigned by the labeling pass after segmentation. It's
 * mutable on purpose: the second labeling pass may re-roll a kind that
 * fails an adjacency constraint (e.g., a {@code LANDING_ZONE} that ended up
 * with no road neighbor).
 *
 * <p>{@link #touchesMapEdge} is set during segmentation so {@code WATERFRONT}
 * placement can prefer edge leaves without re-scanning the partition.
 */
public final class BlockLeaf {

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    public final boolean touchesMapEdge;
    public BlockKind kind;

    public BlockLeaf(int left, int top, int right, int bottom, boolean touchesMapEdge) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.touchesMapEdge = touchesMapEdge;
    }

    public int width()  { return right  - left + 1; }
    public int height() { return bottom - top  + 1; }
    public int centerX() { return (left + right)  / 2; }
    public int centerY() { return (top  + bottom) / 2; }

    /** Cell count — useful for size-aware kind weighting and as a stable size key. */
    public int area() { return width() * height(); }

    /** True iff {@code (x, y)} sits inside this leaf's inclusive rect. */
    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}
