package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * A vehicle-walkable mask: the {@link NavigationGrid}'s walkable set eroded by a
 * footprint radius, so a cell counts as passable only when a vehicle of that
 * size actually fits centered on it. The cost-field router (slice 1) gates
 * traversal on this rather than raw walkability, so a planned route can never
 * thread a gap the truck can't physically drive through — correct by
 * construction, rather than committing to a pinch and relying on recovery. See
 * {@code cost-field-routing/overview.md}.
 *
 * <p>Erosion model (slice-0 starting point): a cell is passable iff it and every
 * cell within Chebyshev distance {@code radiusCells} are walkable. The radius is
 * the footprint <em>half-width</em> (the vehicle aligns its length down a
 * corridor, so width is the binding dimension) — e.g. a HEAVY_APC at
 * visualWidth 1.4 erodes by radius 1. A 3-wide street therefore leaves a 1-cell
 * passable centerline, which is exactly the lane a truck drives; the string-pull
 * and the rolling local planner handle the rest. {@code radiusCells == 0}
 * reproduces the raw walkable set.
 *
 * <p>This is the {@code clearance map} {@link NavigationGrid} originally dropped
 * (see its header) — reintroduced here, vehicle-scoped, off the nav hot path.
 * Pure: {@code (grid, radius) -> mask}. Tuned in slice 4.
 */
public final class VehicleClearance {

    private final int width;
    private final int height;
    private final int radiusCells;
    private final boolean[] passable;

    private VehicleClearance(int width, int height, int radiusCells, boolean[] passable) {
        this.width = width;
        this.height = height;
        this.radiusCells = radiusCells;
        this.passable = passable;
    }

    /**
     * Erode the grid's walkable set by {@code radiusCells} (clamped to ≥0). A
     * cell is passable iff the full {@code (2r+1)×(2r+1)} Chebyshev block
     * centered on it is in-bounds and walkable.
     */
    public static VehicleClearance erode(NavigationGrid grid, int radiusCells) {
        int r = Math.max(0, radiusCells);
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean[] passable = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                passable[y * w + x] = fits(grid, x, y, r);
            }
        }
        return new VehicleClearance(w, h, r, passable);
    }

    /**
     * Footprint radius (cells) for a vehicle of the given visual width — the
     * half-width rounded up, floored at 0. The convenience the spawn sites use
     * so the erosion matches the body that will drive it.
     */
    public static int radiusForWidth(float visualWidthCells) {
        return Math.max(0, Math.round(visualWidthCells * 0.5f));
    }

    private static boolean fits(NavigationGrid grid, int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!grid.isWalkable(cx + dx, cy + dy)) return false;
            }
        }
        return true;
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }
    public int radiusCells() { return radiusCells; }

    /** True if a vehicle of this clearance fits centered on (x, y). */
    public boolean isPassable(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return passable[y * width + x];
    }

    /** Hot-path variant for callers holding a flat index ({@code y*width + x}). */
    public boolean isPassableAt(int idx) {
        return passable[idx];
    }
}
