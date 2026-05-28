package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skeletonizes a road-cell bitmask into a {@link RoadGraph} of centerline
 * nodes + edges. Two passes:
 *
 * <ol>
 *   <li><b>Depth field.</b> 4-connected BFS from every non-road cell at
 *       distance 0, plus every perimeter road cell at distance 1 (virtual
 *       OOB just outside the grid). Result is the manhattan distance from
 *       each road cell to the nearest band boundary.</li>
 *   <li><b>Centerline mask.</b> A road cell sits on the centerline iff it
 *       is strictly greater than its southern depth neighbor and ≥ its
 *       northern neighbor (horizontal-band centerline), or strictly greater
 *       than its western neighbor and ≥ its eastern neighbor (vertical-band
 *       centerline). The strict/loose asymmetry breaks ties on even-width
 *       bands by picking the southern (or western) row of the tied pair —
 *       guarantees a single-cell-thick skeleton on width-4 BSP frames where
 *       two interior rows tie at peak depth.</li>
 * </ol>
 *
 * <p>Graph extraction then walks the centerline as an undirected 4-connected
 * graph. Cells with cardinal-centerline-degree ≠ 2 become {@link RoadGraph.Node}s
 * — that's junctions (degree ≥ 3), dead-ends (degree 1), and isolated cells
 * (degree 0). Cells with degree 2 are interior edge cells; tracing each
 * unvisited direction out of a node walks them until landing at another
 * node, producing one {@link RoadGraph.Edge} per chain.
 *
 * <p>The skeleton naturally includes the 1-cell map perimeter ring (each
 * perimeter cell is depth 1, adjacent neighbors along the ring are also
 * depth 1, perpendicular interior neighbor is the band's deeper centerline
 * — the perimeter cell qualifies as the southern/western tiebreak in one
 * axis). Nodes that land on the perimeter ring are flagged
 * {@link RoadGraph.Node#perimeter} so the convoy spawner can pick one as an
 * off-map entry point.
 */
public final class RoadGraphBuilder {

    private RoadGraphBuilder() {}

    /**
     * Build the graph from a road-cell bitmask. {@code roadCells[x][y]} is
     * {@code true} where the orchestrator wants drivable surface — same mask
     * the BSP / trunk passes produce. Returns {@link RoadGraph#EMPTY} for
     * degenerate inputs (zero-sized grid, no road cells).
     */
    /**
     * Minimum interior centerline depth required for a perimeter cell to
     * count as a convoy entry exit. Trunk centerlines run at depth ≥ 3
     * (SECONDARY width-5 has middle-row depth 3, PRIMARY width-7 depth 4);
     * BSP frame centerlines are at depth ≤ 2 (max frame width with trunks
     * is 4). This threshold filters the perimeter ring down to the four
     * trunk-edge exits and drops every back-street perimeter exit — keeps
     * the graph readable and matches the V1 convoy model where transports
     * arrive via major roads, not alleys.
     */
    private static final int PERIMETER_EXIT_MIN_INTERIOR_DEPTH = 3;

    public static RoadGraph build(boolean[][] roadCells) {
        return build(roadCells, null);
    }

    /**
     * Trunk-aware build. The depth-based centerline pass mishandles trunk
     * cells near the perimeter — the depth field flattens because the
     * perim ring is itself road, so a trunk centerline cell at {@code x=1}
     * has the same depth as its in-band neighbors and fails the strict-
     * peak criterion. Result: the centerline literally vanishes for the
     * last few cells before the map edge, and the trunk's perimeter exit
     * never registers.
     *
     * <p>{@code plan} is the source of truth for "what is a trunk." When
     * non-null, {@link #applyTrunkCenterlines} additively force-marks each
     * trunk's centerline row/column — including the perimeter exit cells
     * — as centerline. The depth-based pass still drives BSP-frame
     * centerlines exactly as before; only trunk bands get the override.
     * This gives the graph a clean trunk skeleton even when the depth
     * field can't find one on its own.
     */
    public static RoadGraph build(boolean[][] roadCells, TrunkPlan.Plan plan) {
        int w = roadCells.length;
        int h = w == 0 ? 0 : roadCells[0].length;
        if (w == 0 || h == 0) return RoadGraph.EMPTY;
        int[][] depth = computeDepth(roadCells, w, h);
        boolean[][] centerline = computeCenterline(depth, w, h);
        filterPerimeterToTrunkExits(centerline, depth, w, h);
        if (plan != null) {
            applyTrunkCenterlines(centerline, plan, w, h);
        }
        return extractGraph(centerline, w, h);
    }

    /**
     * Additively force-marks each {@link TrunkPlan.TrunkSegment}'s centerline
     * row/column as centerline cells across the trunk's full span, then
     * stitches BSP-frame centerlines that end at the trunk band's flanks
     * through to the trunk centerline. Without the stitch step the graph
     * splits into many disconnected components — the depth pass produces
     * BSP-frame centerlines that terminate one cell outside the trunk band,
     * and the trunk band's interior (other than its centerline row/col)
     * has no centerline cells, leaving a multi-cell gap. The stitch fills
     * the gap one column/row at a time, only where a frame is actually
     * present.
     *
     * <p>For a trunk meeting the map edge ({@code t.left == 0} or
     * {@code t.right == w - 1}, equivalent for vertical), the row/col fill
     * also marks the perimeter cell at that edge — that's the convoy
     * entry/exit point, surfaced as a degree-1 perimeter node by
     * {@link #extractGraph}.
     */
    private static void applyTrunkCenterlines(boolean[][] cl, TrunkPlan.Plan plan, int w, int h) {
        for (TrunkPlan.TrunkSegment t : plan.trunks) {
            if (t.horizontal) {
                int row = (t.top + t.bottom) / 2;
                if (row < 0 || row >= h) continue;
                for (int x = t.left; x <= t.right; x++) {
                    if (x >= 0 && x < w) cl[x][row] = true;
                }
                // Stitch frames flanking the band on either side of t.
                // t.top is the lower-y edge of the band; t.bottom the upper-y
                // edge. (Naming is Java-AWT-style despite our Y-up sim.)
                int below = t.top - 1;
                int above = t.bottom + 1;
                for (int x = Math.max(0, t.left); x <= Math.min(w - 1, t.right); x++) {
                    if (below >= 0 && cl[x][below]) {
                        // Frame end below the band — fill from band's lower
                        // edge up to (just below) centerline row.
                        for (int y = t.top; y < row; y++) cl[x][y] = true;
                    }
                    if (above < h && cl[x][above]) {
                        // Frame end above the band — fill from (just above)
                        // centerline row up to band's upper edge.
                        for (int y = row + 1; y <= t.bottom; y++) cl[x][y] = true;
                    }
                }
            } else {
                int col = (t.left + t.right) / 2;
                if (col < 0 || col >= w) continue;
                for (int y = t.top; y <= t.bottom; y++) {
                    if (y >= 0 && y < h) cl[col][y] = true;
                }
                int leftOf = t.left - 1;
                int rightOf = t.right + 1;
                for (int y = Math.max(0, t.top); y <= Math.min(h - 1, t.bottom); y++) {
                    if (leftOf >= 0 && cl[leftOf][y]) {
                        for (int x = t.left; x < col; x++) cl[x][y] = true;
                    }
                    if (rightOf < w && cl[rightOf][y]) {
                        for (int x = col + 1; x <= t.right; x++) cl[x][y] = true;
                    }
                }
            }
        }
    }

    /**
     * Demote every perimeter cell from the centerline mask, then re-promote
     * only those whose interior cardinal neighbor (one cell in) is itself
     * centerline AND deep enough to be a trunk centerline (depth ≥
     * {@link #PERIMETER_EXIT_MIN_INTERIOR_DEPTH}). The perimeter ring
     * disappears from the graph except at the handful of cells where a
     * major road actually meets the map edge — those become degree-1
     * perimeter nodes, the convoy entry/exit points.
     */
    private static void filterPerimeterToTrunkExits(boolean[][] cl, int[][] depth, int w, int h) {
        for (int x = 0; x < w; x++) {
            cl[x][0] = isTrunkExit(cl, depth, x, 1, w, h);
            if (h > 1) cl[x][h - 1] = isTrunkExit(cl, depth, x, h - 2, w, h);
        }
        for (int y = 0; y < h; y++) {
            cl[0][y] = isTrunkExit(cl, depth, 1, y, w, h);
            if (w > 1) cl[w - 1][y] = isTrunkExit(cl, depth, w - 2, y, w, h);
        }
    }

    private static boolean isTrunkExit(boolean[][] cl, int[][] depth,
                                       int interiorX, int interiorY, int w, int h) {
        if (interiorX < 0 || interiorX >= w || interiorY < 0 || interiorY >= h) return false;
        return cl[interiorX][interiorY]
                && depth[interiorX][interiorY] >= PERIMETER_EXIT_MIN_INTERIOR_DEPTH;
    }

    /**
     * Multi-source BFS. Sources: every non-road cell at distance 0, every
     * perimeter road cell at distance 1 (representing the virtual OOB band
     * just outside the grid). Output {@code depth[x][y]} is the 4-connected
     * manhattan distance from each road cell to the nearest non-road
     * boundary (or OOB), capped at the grid's natural reach.
     *
     * <p>Seeding perimeter road cells at depth 1 is what makes a closed
     * road network (e.g. a city with no internal non-road) still produce
     * a centerline — without it, perimeter cells would be unreachable from
     * any source and the depth field would be all-INF on landlocked road
     * networks.
     */
    private static int[][] computeDepth(boolean[][] road, int w, int h) {
        int[][] depth = new int[w][h];
        boolean[][] visited = new boolean[w][h];
        Deque<int[]> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!road[x][y]) {
                    depth[x][y] = 0;
                    visited[x][y] = true;
                    q.add(new int[]{x, y});
                }
            }
        }
        // Perimeter road cells — virtual OOB depth-1 seeds. Add after all
        // depth-0 seeds so FIFO processes the deeper sources first; any
        // perimeter cell already reachable via interior at distance 1 keeps
        // its visited flag, and the perim seed is silently skipped.
        for (int x = 0; x < w; x++) {
            seedPerimeter(road, depth, visited, q, x, 0);
            seedPerimeter(road, depth, visited, q, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            seedPerimeter(road, depth, visited, q, 0, y);
            seedPerimeter(road, depth, visited, q, w - 1, y);
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            int pd = depth[px][py];
            for (int[] d : dirs) {
                int nx = px + d[0], ny = py + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[nx][ny]) continue;
                visited[nx][ny] = true;
                depth[nx][ny] = pd + 1;
                q.add(new int[]{nx, ny});
            }
        }
        return depth;
    }

    private static void seedPerimeter(boolean[][] road, int[][] depth,
                                      boolean[][] visited, Deque<int[]> q,
                                      int x, int y) {
        if (road[x][y] && !visited[x][y]) {
            depth[x][y] = 1;
            visited[x][y] = true;
            q.add(new int[]{x, y});
        }
    }

    /**
     * Centerline criterion: {@code d > dSouth && d >= dNorth} for the
     * horizontal-band centerline, {@code d > dWest && d >= dEast} for the
     * vertical-band centerline. A cell on the centerline if either holds.
     *
     * <p>The asymmetric strict/loose comparison is the tie-breaker. On a
     * 4-wide band (depths 1, 2, 2, 1 along the cross-axis) the southern
     * mid-row {@code y=y0+1} has {@code dS=1, dN=2, d=2}: satisfies
     * {@code d>dS} but not {@code d>dN}; the {@code d>=dN} clause accepts
     * it. The northern mid-row {@code y=y0+2} has {@code dS=2, dN=1, d=2}:
     * fails {@code d>dS}. Result: single-cell-thick centerline, southern
     * pick. On odd-width bands the strict-peak path fires; the tiebreak
     * clause is moot.
     */
    private static boolean[][] computeCenterline(int[][] depth, int w, int h) {
        boolean[][] cl = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int d = depth[x][y];
                if (d == 0) continue;
                int dN = (y + 1 < h) ? depth[x][y + 1] : 0;
                int dS = (y - 1 >= 0) ? depth[x][y - 1] : 0;
                int dE = (x + 1 < w) ? depth[x + 1][y] : 0;
                int dW = (x - 1 >= 0) ? depth[x - 1][y] : 0;
                boolean hCenter = d > dS && d >= dN;
                boolean vCenter = d > dW && d >= dE;
                cl[x][y] = hCenter || vCenter;
            }
        }
        return cl;
    }

    /**
     * Walk the centerline mask as an undirected 4-connected graph. Cells
     * with centerline-degree ≠ 2 become nodes; degree-2 cells are interior
     * edge cells traversed during edge tracing.
     *
     * <p>From each node we trace each cardinal direction once. The trace
     * walks degree-2 cells until landing at another node (or back at the
     * same node for a loop), collecting the cell sequence inclusive of both
     * endpoint cells. Direction-slots at both endpoint nodes are marked
     * used after a successful trace so the same edge isn't re-emitted from
     * the opposite side.
     */
    private static RoadGraph extractGraph(boolean[][] cl, int w, int h) {
        boolean[][] isNode = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!cl[x][y]) continue;
                if (clDegree(cl, x, y, w, h) != 2) isNode[x][y] = true;
            }
        }

        List<RoadGraph.Node> nodes = new ArrayList<>();
        Map<Long, RoadGraph.Node> nodeByCell = new HashMap<>();
        int nextNodeId = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!isNode[x][y]) continue;
                boolean perim = (x == 0 || y == 0 || x == w - 1 || y == h - 1);
                RoadGraph.Node n = new RoadGraph.Node(nextNodeId++, x, y, perim);
                nodes.add(n);
                nodeByCell.put(cellKey(x, y), n);
            }
        }

        List<RoadGraph.Edge> edges = new ArrayList<>();
        int nextEdgeId = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean[][][] usedDir = new boolean[w][h][4];
        for (RoadGraph.Node start : nodes) {
            for (int dirIdx = 0; dirIdx < 4; dirIdx++) {
                if (usedDir[start.cellX][start.cellY][dirIdx]) continue;
                int nx = start.cellX + dirs[dirIdx][0];
                int ny = start.cellY + dirs[dirIdx][1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h || !cl[nx][ny]) continue;
                usedDir[start.cellX][start.cellY][dirIdx] = true;

                List<int[]> chain = new ArrayList<>();
                chain.add(new int[]{start.cellX, start.cellY});
                int cx = nx, cy = ny;
                int comeFromDir = oppositeDir(dirIdx);
                while (true) {
                    chain.add(new int[]{cx, cy});
                    if (isNode[cx][cy]) break;
                    int nextDir = -1;
                    for (int dd = 0; dd < 4; dd++) {
                        if (dd == comeFromDir) continue;
                        int tx = cx + dirs[dd][0];
                        int ty = cy + dirs[dd][1];
                        if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                        if (!cl[tx][ty]) continue;
                        nextDir = dd;
                        break;
                    }
                    if (nextDir < 0) break;  // dead chain — shouldn't happen on a coherent skeleton
                    comeFromDir = oppositeDir(nextDir);
                    cx += dirs[nextDir][0];
                    cy += dirs[nextDir][1];
                }
                RoadGraph.Node end = nodeByCell.get(cellKey(cx, cy));
                if (end == null) continue;  // trace fell off — drop the partial edge
                // Mark the slot on the end node that points back along this
                // edge so we don't re-trace it from the other side.
                usedDir[end.cellX][end.cellY][comeFromDir] = true;

                int n = chain.size();
                int[] cellsX = new int[n];
                int[] cellsY = new int[n];
                for (int i = 0; i < n; i++) {
                    cellsX[i] = chain.get(i)[0];
                    cellsY[i] = chain.get(i)[1];
                }
                edges.add(new RoadGraph.Edge(nextEdgeId++, start, end, cellsX, cellsY));
            }
        }
        return new RoadGraph(nodes, edges);
    }

    /** Cardinal-neighbor centerline count at {@code (x, y)}. */
    private static int clDegree(boolean[][] cl, int x, int y, int w, int h) {
        int d = 0;
        if (x + 1 < w && cl[x + 1][y]) d++;
        if (x - 1 >= 0 && cl[x - 1][y]) d++;
        if (y + 1 < h && cl[x][y + 1]) d++;
        if (y - 1 >= 0 && cl[x][y - 1]) d++;
        return d;
    }

    /** Direction index XOR — pairs (0,1) and (2,3) flip to each other. */
    private static int oppositeDir(int d) {
        return d ^ 1;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
