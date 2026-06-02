package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * The cost-field convoy router: a cost-weighted grid A* over a vehicle-clearance
 * mask, string-pulled into the sparse advisory polyline the corridor →
 * local-planner → controller stack consumes. Replaces the road-<em>graph</em>
 * router — roads are now a cost <em>bias</em> ({@link TerrainCostField}), not a
 * topology vehicles are confined to. See {@code cost-field-routing/overview.md}.
 *
 * <p>Two stages:
 * <ol>
 *   <li><b>Search</b> — {@link GridPathfinder}'s cost-field overload finds the
 *       minimum-cost cell path: prefers roads (cost 1.0), crosses open ground
 *       only when it pays, and is gated on the clearance mask so it never
 *       threads a gap the footprint can't fit.</li>
 *   <li><b>String-pull</b> — the jagged cell path collapses to a sparse polyline
 *       by keeping only the vertices where straight-line visibility breaks. The
 *       visibility test is a <em>clearance-aware supercover trace</em> (every
 *       cell the segment touches must be vehicle-passable), so a straightened
 *       segment never cuts a corner through a sub-clearance cell.</li>
 * </ol>
 *
 * <p>Output is {@code float[][]{xs, ys}} in cell-center coords ({@code cell +
 * 0.5}) — the same shape {@link ConvoyPlanner#expandToWaypoints} returns, so the
 * downstream stack is untouched. Returns {@code null} for no-route (distinct
 * from a valid path), so callers can fall back rather than mistake it for empty.
 *
 * <p>Pure: {@code (start, goal, grid, costField, clearance) -> polyline}. No
 * {@link Vehicle} coupling — reusable for tanks / player vehicles. Tuned in
 * slice 4.
 */
public final class VehicleRoutePlanner {

    private VehicleRoutePlanner() {}

    /**
     * Route from {@code (startX, startY)} to {@code (goalX, goalY)} (cell coords),
     * returning a sparse cell-center polyline or {@code null} if no
     * clearance-respecting path exists. Both endpoints must be in the clearance
     * mask (endpoint snapping for eroded perimeter cells is the spawn layer's
     * responsibility — slice 2).
     */
    public static float[][] route(int startX, int startY, int goalX, int goalY,
                                  NavigationGrid grid, TerrainCostField costField, VehicleClearance clearance) {
        int[] cells = GridPathfinder.findPath(grid, startX, startY, goalX, goalY,
                costField.costArray(), clearance.passableArray());
        if (cells.length < 4) {
            // EMPTY_PATH (no route) or a single cell (start == goal) — nothing to drive.
            return null;
        }
        return stringPull(cells, clearance);
    }

    /**
     * Collapse a dense cell path (flat {@code x,y} pairs) to a sparse cell-center
     * polyline, dropping any vertex a straight clearance-clear segment can skip.
     * Greedy "last visible from the anchor" funnel: advance until the next cell
     * is no longer visible from the current anchor, lock the previous cell as a
     * vertex, and re-anchor there.
     */
    private static float[][] stringPull(int[] cells, VehicleClearance clearance) {
        int n = cells.length / 2;
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();

        int anchor = 0;
        addCenter(xs, ys, cells, anchor);
        for (int i = 2; i < n; i++) {
            if (!segmentClear(cells, anchor, i, clearance)) {
                anchor = i - 1;
                addCenter(xs, ys, cells, anchor);
            }
        }
        addCenter(xs, ys, cells, n - 1);

        int m = xs.size();
        float[] outX = new float[m];
        float[] outY = new float[m];
        for (int i = 0; i < m; i++) {
            outX[i] = xs.get(i);
            outY[i] = ys.get(i);
        }
        return new float[][] { outX, outY };
    }

    private static void addCenter(List<Float> xs, List<Float> ys, int[] cells, int cell) {
        xs.add(cells[cell * 2] + 0.5f);
        ys.add(cells[cell * 2 + 1] + 0.5f);
    }

    /**
     * True if the straight segment between two cell centers stays entirely within
     * the clearance mask. Uses an Amanatides–Woo voxel traversal that checks every
     * cell the segment's interior passes through (start cell pre-loop, each stepped
     * cell including the end); a single sub-clearance cell on the line fails the
     * test. At an exact grid-corner crossing the {@code tMaxX < tMaxY} tie-break
     * steps Y first, so only the cells the line actually enters are checked — the
     * diagonally-opposite cell it merely grazes at the corner point is not, which
     * is correct: a vehicle on the centerline never enters that cell's interior.
     */
    private static boolean segmentClear(int[] cells, int from, int to, VehicleClearance clearance) {
        double x0 = cells[from * 2] + 0.5, y0 = cells[from * 2 + 1] + 0.5;
        double x1 = cells[to * 2] + 0.5,   y1 = cells[to * 2 + 1] + 0.5;
        double dx = x1 - x0, dy = y1 - y0;

        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0);
        int endX = (int) Math.floor(x1), endY = (int) Math.floor(y1);

        if (!clearance.isPassable(cx, cy)) return false;

        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tMaxX = stepX > 0 ? (cx + 1 - x0) / dx : stepX < 0 ? (cx - x0) / dx : Double.POSITIVE_INFINITY;
        double tMaxY = stepY > 0 ? (cy + 1 - y0) / dy : stepY < 0 ? (cy - y0) / dy : Double.POSITIVE_INFINITY;

        int guard = clearance.getWidth() * clearance.getHeight() + 2;
        while ((cx != endX || cy != endY) && guard-- > 0) {
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                cx += stepX;
            } else {
                tMaxY += tDeltaY;
                cy += stepY;
            }
            if (!clearance.isPassable(cx, cy)) return false;
        }
        return true;
    }
}
