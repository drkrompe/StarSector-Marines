package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Static collision check: does a vehicle's oriented bounding box at pose
 * ({@code x, y, facingDeg}) clear of walls and out-of-bounds in the
 * {@link NavigationGrid}?
 *
 * <p>Foundation for every collision-aware planning layer: Reeds-Shepp path
 * validity (call {@link #isPoseFeasible} at sampled poses along the path),
 * future Hybrid A* node feasibility, dynamic-obstacle re-plan triggers.
 *
 * <p>Implementation: sample a 5×3 grid of points in body frame, transform to
 * world, check each lands on a walkable cell. Sub-cell spacing along both
 * axes (0.5 cell for a 2-cell truck length, ~0.55 for 1.1-cell width) means
 * no 1-cell-wide wall can slip between sample points. Cheap enough to call
 * many times per Reeds-Shepp candidate.
 *
 * <p>Body frame convention matches {@link BicycleBody}: facing 0° = +Y,
 * positive CCW. The forward axis is {@code (-sin(rad), cos(rad))} and the
 * right-of-forward axis is {@code (cos(rad), sin(rad))}.
 */
public final class VehicleFootprint {

    private VehicleFootprint() {}

    /**
     * @param x world position, cells
     * @param y world position, cells
     * @param facingDeg facing in degrees, 0°=+Y, positive CCW
     * @param lengthCells nose-to-tail dimension (forward axis), cells
     * @param widthCells side-to-side dimension (right-of-forward axis), cells
     * @param grid the navigation grid; cells outside or with walkable=false fail the check
     * @return true iff every sample point inside the rotated rectangle is walkable
     */
    public static boolean isPoseFeasible(float x, float y, float facingDeg,
                                         float lengthCells, float widthCells,
                                         NavigationGrid grid) {
        float rad = (float) Math.toRadians(facingDeg);
        float fx = -(float) Math.sin(rad);
        float fy =  (float) Math.cos(rad);
        float sx =  (float) Math.cos(rad);
        float sy =  (float) Math.sin(rad);
        float halfL = lengthCells * 0.5f;
        float halfW = widthCells * 0.5f;

        // 5 length × 3 width = 15 samples. Spacing: halfL/2 along length,
        // halfW along width. Sub-cell on both axes for a truck-scale OBB.
        for (int li = -2; li <= 2; li++) {
            float u = (li / 2f) * halfL;
            for (int wi = -1; wi <= 1; wi++) {
                float v = wi * halfW;
                float px = x + u * fx + v * sx;
                float py = y + u * fy + v * sy;
                int cx = (int) Math.floor(px);
                int cy = (int) Math.floor(py);
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
            }
        }
        return true;
    }
}
