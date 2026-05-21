package com.dillon.starsectormarines.battle.mapgen.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vehicle-navigation graph over the city's road network. Built once after
 * {@link com.dillon.starsectormarines.battle.mapgen.bsp.BspCityGenerator}
 * lays down the trunk + BSP-frame road mask; consumed by ground-vehicle
 * pathing (convoys steer along edge cell-lists between {@link Node}s) and
 * by debug overlays in {@code BspMapPreviewTest}.
 *
 * <p>Nodes sit on cells; edges hold an ordered cell-list from {@link Edge#a}
 * to {@link Edge#b} that includes both endpoint cells. A truck reading the
 * graph as a waypoint queue just walks the cell-list in order (reversed via
 * {@link Edge#cellsFrom(Node)} if it entered from b instead of a). Edge
 * lengths are cell-counts, not Euclidean distances — close enough for the
 * grid-aligned bands BSP produces, and lets path costs stay integer.
 *
 * <p>Coordinate convention matches the rest of the battle module: integer
 * cell coords with Y up. Vehicle bodies use fractional float coords, so the
 * convoy converts cell {@code (cx, cy)} → world {@code (cx + 0.5, cy + 0.5)}
 * when consuming an edge — keeps the truck centered in the road band.
 *
 * <p>Perimeter nodes — {@link Node#perimeter} true — are the off-map entry
 * candidates. They sit on the 1-cell perimeter road ring where a trunk's
 * centerline hits the map edge; convoys spawn just outside one and steer
 * toward it as their first waypoint.
 */
public final class RoadGraph {

    /**
     * One vertex in the road graph. Sits on a specific cell; tracks its
     * incident edges so callers can walk the graph without an external
     * adjacency list.
     */
    public static final class Node {
        public final int id;
        public final int cellX;
        public final int cellY;
        /** True when this node is on the 1-cell map-edge perimeter — candidate off-map convoy entry. */
        public final boolean perimeter;
        private final List<Edge> edges = new ArrayList<>();

        public Node(int id, int cellX, int cellY, boolean perimeter) {
            this.id = id;
            this.cellX = cellX;
            this.cellY = cellY;
            this.perimeter = perimeter;
        }

        public List<Edge> edges() { return edges; }
        public int degree() { return edges.size(); }

        void addEdge(Edge e) { edges.add(e); }
    }

    /**
     * One edge in the road graph. Holds the ordered cell sequence from
     * {@link #a} to {@link #b} inclusive of both endpoint cells.
     *
     * <p>Cell-list orientation matters for truck pathing: a convoy that
     * entered the edge from {@link #a} consumes cells in array order; one
     * that entered from {@link #b} consumes them in reverse. {@link #cellsFrom}
     * handles the flip.
     */
    public static final class Edge {
        public final int id;
        public final Node a;
        public final Node b;
        public final int[] cellsX;
        public final int[] cellsY;

        public Edge(int id, Node a, Node b, int[] cellsX, int[] cellsY) {
            if (cellsX.length != cellsY.length) {
                throw new IllegalArgumentException("cellsX/cellsY length mismatch");
            }
            if (cellsX.length < 2) {
                throw new IllegalArgumentException("edge needs at least 2 cells (endpoints)");
            }
            this.id = id;
            this.a = a;
            this.b = b;
            this.cellsX = cellsX;
            this.cellsY = cellsY;
        }

        /** Number of cells the edge spans, including both endpoints. */
        public int length() { return cellsX.length; }

        /** The opposite endpoint when walking from {@code from}. */
        public Node otherEnd(Node from) {
            if (from == a) return b;
            if (from == b) return a;
            throw new IllegalArgumentException("node not incident to this edge");
        }

        /**
         * Cell sequence walked starting at {@code from}'s cell and ending at
         * the other endpoint's cell. Returns the underlying arrays when
         * {@code from == a}, freshly-reversed copies when {@code from == b} —
         * callers must not mutate the result.
         */
        public int[][] cellsFrom(Node from) {
            if (from == a) return new int[][] { cellsX, cellsY };
            if (from == b) {
                int n = cellsX.length;
                int[] rx = new int[n];
                int[] ry = new int[n];
                for (int i = 0; i < n; i++) {
                    rx[i] = cellsX[n - 1 - i];
                    ry[i] = cellsY[n - 1 - i];
                }
                return new int[][] { rx, ry };
            }
            throw new IllegalArgumentException("node not incident to this edge");
        }
    }

    private final List<Node> nodes;
    private final List<Edge> edges;

    public RoadGraph(List<Node> nodes, List<Edge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        // Wire each node's edge list once at construction so callers can
        // traverse the graph without an external adjacency map.
        for (Edge e : this.edges) {
            e.a.addEdge(e);
            if (e.a != e.b) e.b.addEdge(e);
        }
    }

    public List<Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }

    /** Perimeter nodes only — the off-map convoy entry candidates. */
    public List<Node> perimeterNodes() {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) if (n.perimeter) out.add(n);
        return out;
    }

    /** Empty graph — returned by generators that don't build one. */
    public static final RoadGraph EMPTY =
            new RoadGraph(Collections.emptyList(), Collections.emptyList());
}
