package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared placement validators for stampers that turn rectangular footprints
 * non-walkable (defense posts, parked vehicles, future obstacles). Both checks
 * exist because the obvious "footprint doesn't contain a doorway / wall" gate
 * misses two failure modes:
 *
 * <ul>
 *   <li>{@link #touchesDoorway} — the stamp doesn't land ON a doorway, but it
 *       lands on the doorway's perpendicular <em>through-cell</em>, which is
 *       walkable and unflagged but is the building's only egress. Sealing it
 *       traps the interior.</li>
 *   <li>{@link #wouldPartitionWalkable} — the stamp sits one cell off an
 *       existing non-walkable mass (BSP outdoor wall, fortress wall, another
 *       building), sealing a thin strip of walkable cells between itself and
 *       the wall.</li>
 * </ul>
 *
 * <p>Both are pre-stamp checks — they read the grid's current state, don't
 * mutate it, and let the caller skip the placement if either returns true.
 */
public final class PlacementGuards {

    private PlacementGuards() {}

    /**
     * True if {@code (x, y)} is a doorway cell, or if any of its 4 cardinal
     * neighbors is. Cardinal-only because only the perpendicular neighbors of
     * a doorway carry traffic in/out — the parallel neighbors are the wall
     * continuing past the gap (already non-walkable), and the diagonal
     * neighbors don't block the threshold.
     */
    public static boolean touchesDoorway(NavigationGrid grid, int x, int y) {
        if (grid.isDoorway(x, y)) return true;
        if (grid.inBounds(x + 1, y) && grid.isDoorway(x + 1, y)) return true;
        if (grid.inBounds(x - 1, y) && grid.isDoorway(x - 1, y)) return true;
        if (grid.inBounds(x, y + 1) && grid.isDoorway(x, y + 1)) return true;
        if (grid.inBounds(x, y - 1) && grid.isDoorway(x, y - 1)) return true;
        return false;
    }

    /**
     * Rectangular-footprint connectivity check — convenience overload for
     * stampers whose footprint fills its whole bounding box (e.g. parked
     * vehicles). Treats every cell of
     * {@code [minX..minX+width-1] × [minY..minY+height-1]} as non-walkable and
     * delegates to {@link #wouldPartitionWalkable(NavigationGrid, int[][])}.
     *
     * <p><b>Do not use for sparse footprints</b> (a defense-post vent ring
     * leaves its bbox corners walkable): treating the whole rect as blocked
     * excludes those open cells from the check, so a corner the real stamp
     * leaves walkable — and that some pre-existing wall/water boxes in — is
     * silently missed. Such callers must pass the actual blocked cells.
     */
    public static boolean wouldPartitionWalkable(NavigationGrid grid,
                                                 int minX, int minY,
                                                 int width, int height) {
        int[][] cells = new int[width * height][2];
        int i = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                cells[i][0] = minX + dx;
                cells[i][1] = minY + dy;
                i++;
            }
        }
        return wouldPartitionWalkable(grid, cells);
    }

    /**
     * Simulated-stamp connectivity check. Treats {@code stampedCells} (the
     * exact cells a stamp will turn non-walkable) as blocked, picks the first
     * walkable cell outside that set as a BFS seed, walks the walkable graph,
     * and returns true if any walkable cell would remain unreached.
     *
     * <p>Catches two cases the cheaper footprint gate misses: the stamp sealing
     * a thin strip against an existing non-walkable mass, AND the stamp boxing
     * in one of its <em>own</em> footprint cells that it leaves walkable (a vent
     * ring's open corner trapped between the ring arms and pre-existing
     * water/wall). The latter is why this takes the real blocked-cell set rather
     * than a bounding rect — open footprint cells stay in the walkable graph and
     * are checked for reachability like any other cell.
     *
     * <p>Runs in {@code O(w*h)} on the grid but only fires for anchors that pass
     * the cheaper footprint/doorway gates, so typical use is a handful of calls
     * per gen, not hundreds.
     */
    public static boolean wouldPartitionWalkable(NavigationGrid grid, int[][] stampedCells) {
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();
        Set<Integer> stamped = new HashSet<>();
        for (int[] c : stampedCells) {
            if (c[0] >= 0 && c[0] < gridW && c[1] >= 0 && c[1] < gridH) {
                stamped.add(c[1] * gridW + c[0]);
            }
        }

        int seedIdx = -1;
        int target = 0;
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int idx = y * gridW + x;
                if (stamped.contains(idx)) continue;
                if (seedIdx < 0) seedIdx = idx;
                target++;
            }
        }
        if (seedIdx < 0) return false;

        boolean[] visited = new boolean[gridW * gridH];
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(seedIdx);
        visited[seedIdx] = true;
        int reached = 1;
        while (!stack.isEmpty()) {
            int idx = stack.pop();
            int x = idx % gridW;
            int y = idx / gridW;
            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (nx < 0 || nx >= gridW || ny < 0 || ny >= gridH) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                int nidx = ny * gridW + nx;
                if (stamped.contains(nidx)) continue;
                if (visited[nidx]) continue;
                visited[nidx] = true;
                reached++;
                stack.push(nidx);
            }
        }
        return reached != target;
    }
}
