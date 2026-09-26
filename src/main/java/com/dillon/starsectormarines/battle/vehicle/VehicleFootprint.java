package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Direction;

/** Exact oriented chassis rectangle against solid cells and reciprocal closed edges.
 * The sampled-cell visitor is retained for existing presentation/map consumers;
 * collision feasibility itself never relies on those samples.
 */
public final class VehicleFootprint {

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int cellX, int cellY);
    }

    private VehicleFootprint() {}

    /**
     * @param x world position, cells
     * @param y world position, cells
     * @param facingDeg facing in degrees, 0°=+Y, positive CCW
     * @param lengthCells nose-to-tail dimension (forward axis), cells
     * @param widthCells side-to-side dimension (right-of-forward axis), cells
     * @param grid the navigation grid; cells outside or with walkable=false fail the check
     * @return true iff the complete rectangle clears terrain, closed edges and bounds
     */
    public static boolean isPoseFeasible(float x, float y, float facingDeg,
                                         float lengthCells, float widthCells,
                                         NavigationGrid grid) {
        return finite(x, y, facingDeg, lengthCells, widthCells)
                && clears(x, y, facingDeg, lengthCells * .5d, widthCells * .5d, grid, false);
    }

    /**
     * Returns whether the complete rotated footprint lies inside the grid,
     * independent of walkability. This distinguishes deliberate off-map route
     * tails from an on-grid pose that happens to overlap an obstacle.
     */
    public static boolean isPoseWithinGrid(float x, float y, float facingDeg,
                                           float lengthCells, float widthCells,
                                           NavigationGrid grid) {
        if (!finite(x, y, facingDeg, lengthCells, widthCells)) return false;
        double rad = Math.toRadians(facingDeg);
        double ex = Math.abs(Math.sin(rad)) * lengthCells * .5d + Math.abs(Math.cos(rad)) * widthCells * .5d;
        double ey = Math.abs(Math.cos(rad)) * lengthCells * .5d + Math.abs(Math.sin(rad)) * widthCells * .5d;
        return x - ex >= -EPS && y - ey >= -EPS
                && x + ex <= grid.getWidth() + EPS && y + ey <= grid.getHeight() + EPS;
    }

    /**
     * Visits grid cells touched by a legacy 5x3 body sample pattern for
     * presentation/map consumers; collision uses the exact rectangle instead.
     * Duplicate visits are intentional and harmless for
     * idempotent map writes; keeping this allocation-free matters more than
     * deduplicating a fifteen-sample footprint.
     */
    public static void forEachSampledCell(float x, float y, float facingDeg,
                                          float lengthCells, float widthCells,
                                          NavigationGrid grid, CellConsumer consumer) {
        float rad = (float) Math.toRadians(facingDeg);
        float fx = -(float) Math.sin(rad);
        float fy =  (float) Math.cos(rad);
        float sx =  (float) Math.cos(rad);
        float sy =  (float) Math.sin(rad);
        float halfL = lengthCells * 0.5f;
        float halfW = widthCells * 0.5f;
        for (int li = -2; li <= 2; li++) {
            float u = (li / 2f) * halfL;
            for (int wi = -1; wi <= 1; wi++) {
                float v = wi * halfW;
                int cx = (int) Math.floor(x + u * fx + v * sx);
                int cy = (int) Math.floor(y + u * fy + v * sy);
                if (grid.inBounds(cx, cy)) consumer.accept(cx, cy);
            }
        }
    }

    private static final double EPS = 1e-7;

    private static boolean finite(float x, float y, float facing, float length, float width) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(facing)
                && Float.isFinite(length) && Float.isFinite(width) && length > 0f && width > 0f;
    }

    /** Exact SAT against cells and axis-aligned segments, also used for conservative sweep enclosures. */
    static boolean clears(double x, double y, double facing, double halfL, double halfW,
                           NavigationGrid grid, boolean allowOffMap) {
        double rad = Math.toRadians(facing), fx = -Math.sin(rad), fy = Math.cos(rad);
        double sx = fy, sy = -fx;
        double ex = Math.abs(fx) * halfL + Math.abs(sx) * halfW;
        double ey = Math.abs(fy) * halfL + Math.abs(sy) * halfW;
        if (!allowOffMap && (x - ex < -EPS || y - ey < -EPS
                || x + ex > grid.getWidth() + EPS || y + ey > grid.getHeight() + EPS)) return false;
        // Include the cells immediately west/south of the envelope: they own
        // east/north edges that can meet the body exactly at its lower bound.
        int minX = Math.max(0, (int) Math.floor(x - ex) - 1);
        int minY = Math.max(0, (int) Math.floor(y - ey) - 1);
        int maxX = Math.min(grid.getWidth() - 1, (int) Math.floor(x + ex));
        int maxY = Math.min(grid.getHeight() - 1, (int) Math.floor(y + ey));
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (!grid.isWalkable(cx, cy)
                        && overlaps(cx + .5d - x, cy + .5d - y, .5d, .5d,
                        fx, fy, sx, sy, halfL, halfW, ex, ey)) return false;
                if (cx + 1 < grid.getWidth() && !grid.canTraverseCellStep(cx, cy, Direction.E)
                        && overlaps(cx + 1d - x, cy + .5d - y, 0d, .5d,
                        fx, fy, sx, sy, halfL, halfW, ex, ey)) return false;
                if (cy + 1 < grid.getHeight() && !grid.canTraverseCellStep(cx, cy, Direction.N)
                        && overlaps(cx + .5d - x, cy + 1d - y, .5d, 0d,
                        fx, fy, sx, sy, halfL, halfW, ex, ey)) return false;
            }
        }
        return true;
    }

    private static boolean overlaps(double dx, double dy, double boxX, double boxY,
                                     double fx, double fy, double sx, double sy,
                                     double halfL, double halfW, double ex, double ey) {
        // Boundary contact is legal; positive penetration is not. A zero-width
        // rectangle represents an edge and uses the same separating axes.
        return Math.abs(dx) < ex + boxX - EPS && Math.abs(dy) < ey + boxY - EPS
                && Math.abs(dx * fx + dy * fy) < halfL + boxX * Math.abs(fx) + boxY * Math.abs(fy) - EPS
                && Math.abs(dx * sx + dy * sy) < halfW + boxX * Math.abs(sx) + boxY * Math.abs(sy) - EPS;
    }
}
