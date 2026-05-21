package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.mapgen.road.RoadGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans convoy routes over a {@link RoadGraph}. V1 uses unit-weighted BFS
 * between nodes — every edge counts as one hop regardless of cell-length —
 * which is enough to find a connected route through trunks + BSP-frame
 * roads. Edge-cell-length weighting is a future optimization once we have
 * multiple convoys competing for the shortest route.
 *
 * <p>{@link #expandToWaypoints} unrolls a node-path into the cell sequence
 * a {@link Vehicle} will consume as its waypoint queue. The expansion
 * orients each edge's cell-list relative to the entry node so the convoy
 * walks cells in driving order; junction cells (shared between two edges)
 * are de-duplicated to avoid the truck pausing twice at the same node.
 */
public final class ConvoyPlanner {

    private ConvoyPlanner() {}

    /**
     * BFS path from {@code from} to {@code to} as the ordered list of edges
     * traversed. Returns {@code null} when no path exists (disconnected
     * components — shouldn't happen on a properly-built road graph but
     * caller still has to handle it).
     */
    public static List<RoadGraph.Edge> planPath(RoadGraph graph, RoadGraph.Node from, RoadGraph.Node to) {
        if (from == to) return new ArrayList<>();
        Map<RoadGraph.Node, RoadGraph.Edge> cameFrom = new HashMap<>();
        Deque<RoadGraph.Node> q = new ArrayDeque<>();
        q.add(from);
        cameFrom.put(from, null);
        while (!q.isEmpty()) {
            RoadGraph.Node n = q.poll();
            if (n == to) break;
            for (RoadGraph.Edge e : n.edges()) {
                RoadGraph.Node nxt = e.otherEnd(n);
                if (cameFrom.containsKey(nxt)) continue;
                cameFrom.put(nxt, e);
                q.add(nxt);
            }
        }
        if (!cameFrom.containsKey(to)) return null;
        List<RoadGraph.Edge> reversed = new ArrayList<>();
        RoadGraph.Node cur = to;
        while (cur != from) {
            RoadGraph.Edge e = cameFrom.get(cur);
            reversed.add(e);
            cur = e.otherEnd(cur);
        }
        List<RoadGraph.Edge> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) path.add(reversed.get(i));
        return path;
    }

    /**
     * Expand an edge-path into the cell-center waypoint sequence a
     * {@link Vehicle} consumes. The first cell of the first edge (from's
     * cell) opens the sequence; thereafter each edge's cells are appended
     * skipping the shared junction cell at the start (already added by the
     * previous edge's tail). Result is two parallel float arrays of equal
     * length, in cell-center coords (cellX + 0.5, cellY + 0.5).
     *
     * <p>Pass {@code from} explicitly so the orientation of each edge's
     * cell-list is correct — edges are undirected and the cell-list always
     * runs from edge.a to edge.b, which may or may not match the path
     * direction. {@link RoadGraph.Edge#cellsFrom(RoadGraph.Node)} handles
     * the flip.
     */
    public static float[][] expandToWaypoints(List<RoadGraph.Edge> path, RoadGraph.Node from) {
        if (path == null || path.isEmpty()) {
            return new float[][] { new float[]{from.cellX + 0.5f}, new float[]{from.cellY + 0.5f} };
        }
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        RoadGraph.Node cur = from;
        for (int i = 0; i < path.size(); i++) {
            RoadGraph.Edge e = path.get(i);
            int[][] cells = e.cellsFrom(cur);
            int[] cx = cells[0];
            int[] cy = cells[1];
            int startIdx = (i == 0) ? 0 : 1;  // skip shared junction with previous edge
            for (int k = startIdx; k < cx.length; k++) {
                xs.add(cx[k] + 0.5f);
                ys.add(cy[k] + 0.5f);
            }
            cur = e.otherEnd(cur);
        }
        int n = xs.size();
        float[] outX = new float[n];
        float[] outY = new float[n];
        for (int i = 0; i < n; i++) {
            outX[i] = xs.get(i);
            outY[i] = ys.get(i);
        }
        return new float[][] { outX, outY };
    }

    /**
     * Reverse a waypoint sequence — for V1 where outbound paths are just
     * the inbound path walked back. Returned arrays are fresh copies; the
     * input arrays aren't mutated.
     */
    public static float[][] reverse(float[] xs, float[] ys) {
        int n = xs.length;
        float[] rx = new float[n];
        float[] ry = new float[n];
        for (int i = 0; i < n; i++) {
            rx[i] = xs[n - 1 - i];
            ry[i] = ys[n - 1 - i];
        }
        return new float[][] { rx, ry };
    }
}
