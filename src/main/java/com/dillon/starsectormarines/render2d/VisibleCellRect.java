package com.dillon.starsectormarines.render2d;

/**
 * Inclusive cell-space AABB of the battle camera's current view.
 *
 * <p>This is a 2D ortho viewport, not a 3D frustum: {@link BattleCamera} maps
 * screen {@code vpX/vpY/vpW/vpH} back to cells, and dense render passes iterate
 * {@code [minX, maxX] × [minY, maxY]} instead of the whole grid. Empty when
 * {@code minX > maxX} or {@code minY > maxY} — a {@code for} over the range
 * is then a no-op.
 */
public record VisibleCellRect(int minX, int minY, int maxX, int maxY) {

    /**
     * Halo around the exact viewport so partial cells, autotile neighbor reads
     * that still emit, and the ground-parallax FBO's edge samples do not pop.
     */
    public static final int GEOMETRY_MARGIN_CELLS = 2;

    /**
     * Fog's unrevealed-neighbor gradient looks one cell out; keep the original
     * 8-cell halo so the edge does not crawl when panning.
     */
    public static final int FOG_MARGIN_CELLS = 8;

    public static final VisibleCellRect EMPTY = new VisibleCellRect(0, 0, -1, -1);

    public boolean isEmpty() {
        return minX > maxX || minY > maxY;
    }

    public boolean contains(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    /**
     * Inclusive AABB test against another cell rectangle. Empty never intersects.
     */
    public boolean intersects(int otherMinX, int otherMaxX, int otherMinY, int otherMaxY) {
        if (isEmpty()) return false;
        return minX <= otherMaxX && maxX >= otherMinX
                && minY <= otherMaxY && maxY >= otherMinY;
    }

    /** Inclusive AABB of a {@code cellsW × cellsH} footprint anchored at {@code (cellX, cellY)}. */
    public boolean intersectsCells(int cellX, int cellY, int cellsW, int cellsH) {
        if (cellsW <= 0 || cellsH <= 0) return false;
        return intersects(cellX, cellX + cellsW - 1, cellY, cellY + cellsH - 1);
    }

    public int width() {
        return isEmpty() ? 0 : maxX - minX + 1;
    }

    public int height() {
        return isEmpty() ? 0 : maxY - minY + 1;
    }
}
