package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * A vehicle-walkable mask: the {@link NavigationGrid}'s walkable set eroded by a
 * footprint radius, so a cell counts as passable only when a vehicle of that
 * size actually fits centered on it. The cost-field router (slice 1) gates
 * traversal on this rather than raw walkability, so a planned route can never
 * thread a gap the truck can't physically drive through — correct by
 * construction, rather than committing to a pinch and relying on recovery. See
 * {@code convoy-nouns.md}.
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
    /** Footprint tests run since construction. Evidence for the catch-up tests, not behavior. */
    private long fitEvaluations;

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
        VehicleClearance mask =
                new VehicleClearance(w, h, r, new boolean[w * h]);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask.passable[y * w + x] = mask.fits(grid, x, y, r);
            }
        }
        return mask;
    }

    /**
     * A fresh, independent copy of {@code source}'s mask.
     *
     * <p>A {@link RoutePlan}/{@code VehicleMission} may hold onto a returned
     * {@code VehicleClearance} for the whole of a vehicle's drive, as "the mask
     * this route was proved against" — see {@code RoutePlan}'s header. Patching
     * a changed neighbourhood therefore must not mutate an instance a caller has
     * already been handed; it patches this clone instead, which costs one
     * {@code boolean[]} copy (a few hundred KB, well under the erosion this
     * exists to avoid) and leaves every earlier snapshot exactly as it was.
     */
    static VehicleClearance copyOf(VehicleClearance source) {
        return new VehicleClearance(source.width, source.height,
                source.radiusCells, source.passable.clone());
    }

    /**
     * Re-evaluates the mask around one cell whose walkability or edges moved.
     *
     * <p>A cell's mask value depends only on the {@code (2r+1)} block centred on
     * it, so the cells a single change can flip are exactly those within
     * Chebyshev {@code r} of it — 9 cells for the radius-1 chassis this project
     * drives, against the 188,160 a re-erosion touches. Mutates this instance in
     * place; call it on a {@link #copyOf} clone, never on an instance a caller
     * may already be holding.
     *
     * @return how many cells actually flipped
     */
    int refreshAround(NavigationGrid grid, int cellX, int cellY) {
        int flipped = 0;
        for (int y = Math.max(0, cellY - radiusCells);
             y <= Math.min(height - 1, cellY + radiusCells); y++) {
            for (int x = Math.max(0, cellX - radiusCells);
                 x <= Math.min(width - 1, cellX + radiusCells); x++) {
                int idx = y * width + x;
                boolean now = fits(grid, x, y, radiusCells);
                if (passable[idx] == now) continue;
                passable[idx] = now;
                flipped++;
            }
        }
        return flipped;
    }

    /** Footprint tests run on this mask since it was eroded. Evidence, not behavior. */
    long fitEvaluations() { return fitEvaluations; }

    /**
     * Footprint radius (cells) for a vehicle of the given visual width — the
     * half-width rounded up, floored at 0. The convenience the spawn sites use
     * so the erosion matches the body that will drive it.
     */
    public static int radiusForWidth(float visualWidthCells) {
        return Math.max(0, Math.round(visualWidthCells * 0.5f));
    }

    private boolean fits(NavigationGrid grid, int cx, int cy, int r) {
        fitEvaluations++;
        return fitsAt(grid, cx, cy, r);
    }

    /** One footprint probe without allocating or eroding a whole-map mask. */
    public static boolean fitsAt(NavigationGrid grid, int cx, int cy,
                                 int radiusCells) {
        int r = Math.max(0, radiusCells);
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

    /** Backing mask for {@link VehicleRoutePlanner} to hand straight to the pathfinder — do not mutate. */
    boolean[] passableArray() {
        return passable;
    }
}
