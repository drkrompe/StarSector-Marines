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
 * {@link VehicleMission} coupling — reusable for tanks / player vehicles. Tuned in
 * slice 4.
 */
public final class VehicleRoutePlanner {

    private VehicleRoutePlanner() {}

    /**
     * Route from {@code (startX, startY)} to {@code (goalX, goalY)} (cell coords),
     * returning a sparse cell-center polyline or {@code null} if no
     * clearance-respecting path exists. Both endpoints must be in the clearance
     * mask (endpoint snapping for eroded perimeter cells is the caller's job —
     * see {@link #snapToMask}).
     */
    public static float[][] route(int startX, int startY, int goalX, int goalY,
                                  NavigationGrid grid, TerrainCostField costField, VehicleClearance clearance) {
        return routeMasked(startX, startY, goalX, goalY, grid, costField,
                clearance.passableArray(), clearance.getWidth(), clearance.getHeight());
    }

    /**
     * Re-route variant for the recovery ladder's "lap around" rung: like
     * {@link #route} but treats a disc of radius {@code avoidRadius} cells around
     * {@code (avoidX, avoidY)} as impassable, so the search detours around the
     * spot the vehicle got stuck. Returns {@code null} if avoiding it disconnects
     * the goal (genuinely boxed in → caller gives up). The avoid disc is honored
     * by the string-pull too, so a straightened segment can't cut back through it.
     */
    public static float[][] routeAvoiding(int startX, int startY, int goalX, int goalY,
                                          NavigationGrid grid, TerrainCostField costField,
                                          VehicleClearance clearance,
                                          int avoidX, int avoidY, float avoidRadius) {
        int w = clearance.getWidth(), h = clearance.getHeight();
        boolean[] mask = clearance.passableArray().clone();
        int r = (int) Math.ceil(avoidRadius);
        float r2 = avoidRadius * avoidRadius;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r2) continue;
                int x = avoidX + dx, y = avoidY + dy;
                if (x >= 0 && x < w && y >= 0 && y < h) mask[y * w + x] = false;
            }
        }
        // Safety net: never blank the route's own start — if the avoid disc clips
        // it (a small/short-corridor edge case), the search would have no valid
        // start and return null. Restore it to its original passability.
        if (startX >= 0 && startX < w && startY >= 0 && startY < h) {
            mask[startY * w + startX] = clearance.passableArray()[startY * w + startX];
        }
        return routeMasked(startX, startY, goalX, goalY, grid, costField, mask, w, h);
    }

    private static float[][] routeMasked(int startX, int startY, int goalX, int goalY,
                                         NavigationGrid grid, TerrainCostField costField,
                                         boolean[] passable, int w, int h) {
        int[] cells = GridPathfinder.findPath(grid, startX, startY, goalX, goalY,
                costField.costArray(), passable);
        if (cells.length < 4) {
            // EMPTY_PATH (no route) or a single cell (start == goal) — nothing to drive.
            return null;
        }
        return stringPull(cells, passable, w, h);
    }

    /**
     * Nearest cell to {@code (x, y)} that the footprint fits in
     * ({@link VehicleClearance#isPassable}), searched in expanding Chebyshev rings
     * out to {@code maxRadius}, nearest-Euclidean within a ring. Returns
     * {@code {cx, cy}} or {@code null} if nothing in range fits. Shared by the
     * spawn layer ({@code ConvoyMeans}) and the recovery re-route to pull eroded
     * perimeter / wall-hugging endpoints onto a drivable cell before routing.
     */
    public static int[] snapToMask(VehicleClearance clearance, int x, int y, int maxRadius) {
        if (clearance.isPassable(x, y)) return new int[]{x, y};
        for (int r = 1; r <= maxRadius; r++) {
            int bestX = -1, bestY = -1, bestD2 = Integer.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue; // ring perimeter only
                    int nx = x + dx, ny = y + dy;
                    if (!clearance.isPassable(nx, ny)) continue;
                    int d2 = dx * dx + dy * dy;
                    if (d2 < bestD2) { bestD2 = d2; bestX = nx; bestY = ny; }
                }
            }
            if (bestX >= 0) return new int[]{bestX, bestY};
        }
        return null;
    }

    /**
     * Collapse a dense cell path (flat {@code x,y} pairs) to a sparse cell-center
     * polyline, dropping any vertex a straight clearance-clear segment can skip.
     * Greedy "last visible from the anchor" funnel: advance until the next cell
     * is no longer visible from the current anchor, lock the previous cell as a
     * vertex, and re-anchor there.
     */
    private static float[][] stringPull(int[] cells, boolean[] passable, int w, int h) {
        int n = cells.length / 2;
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();

        int anchor = 0;
        addCenter(xs, ys, cells, anchor);
        for (int i = 2; i < n; i++) {
            if (!segmentClear(cells, anchor, i, passable, w, h)) {
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

    private static boolean cellPassable(boolean[] passable, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && passable[y * w + x];
    }

    /**
     * True if the straight segment between two cell centers stays entirely within
     * the (possibly avoid-masked) passable set. Amanatides–Woo voxel traversal
     * that checks every cell the segment's interior passes through (start cell
     * pre-loop, each stepped cell including the end); a single sub-clearance cell
     * on the line fails. At an exact grid-corner crossing the {@code tMaxX < tMaxY}
     * tie-break steps Y first, so only the cells the line actually enters are
     * checked — the diagonally-opposite cell it merely grazes at the corner point
     * is not, which is correct: a vehicle on the centerline never enters it.
     */
    private static boolean segmentClear(int[] cells, int from, int to,
                                        boolean[] passable, int w, int h) {
        double x0 = cells[from * 2] + 0.5, y0 = cells[from * 2 + 1] + 0.5;
        double x1 = cells[to * 2] + 0.5,   y1 = cells[to * 2 + 1] + 0.5;
        double dx = x1 - x0, dy = y1 - y0;

        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0);
        int endX = (int) Math.floor(x1), endY = (int) Math.floor(y1);

        if (!cellPassable(passable, w, h, cx, cy)) return false;

        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tMaxX = stepX > 0 ? (cx + 1 - x0) / dx : stepX < 0 ? (cx - x0) / dx : Double.POSITIVE_INFINITY;
        double tMaxY = stepY > 0 ? (cy + 1 - y0) / dy : stepY < 0 ? (cy - y0) / dy : Double.POSITIVE_INFINITY;

        int guard = w * h + 2;
        while ((cx != endX || cy != endY) && guard-- > 0) {
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                cx += stepX;
            } else {
                tMaxY += tDeltaY;
                cy += stepY;
            }
            if (!cellPassable(passable, w, h, cx, cy)) return false;
        }
        return true;
    }
}
