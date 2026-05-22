package com.dillon.starsectormarines.battle.mapgen.road;

/**
 * Bitmask of cells that belong to the {@link RoadGraph} — every node cell
 * plus every interior cell along every edge's traced chain. Map-gen stamps
 * that come AFTER the road graph is built (compound fillers, fortress wall,
 * defense posts, beach shoreline) consult this mask and refuse to clobber a
 * reserved cell.
 *
 * <p>The mask is the city's "public infrastructure" footprint: streets the
 * truck convoy actually drives through. Anything built later — turret pads,
 * gated-housing perimeter walls, fortress walls, even shoreline water — has
 * to route around it.
 *
 * <p>An empty mask is fine. Generators that don't emit a road graph (nature-
 * only maps, future hand-authored scenarios) get {@code boolean[w][h]} all
 * false and the stampers behave as if reservation didn't exist. No special
 * case at the call sites.
 *
 * <p>Width and height are passed explicitly rather than inferred from the
 * graph because an empty {@link RoadGraph} carries no extent — and the
 * generator already has the grid dimensions at hand.
 */
public final class RoadReservation {

    private RoadReservation() {}

    /**
     * Derive a {@code [w][h]} reservation mask from the given graph. Marks
     * every node cell and every cell in every edge's traced cell-list.
     * Out-of-bounds cells (shouldn't happen on a coherent graph but defensive
     * against future changes) are silently skipped.
     */
    public static boolean[][] mask(RoadGraph graph, int w, int h) {
        boolean[][] out = new boolean[w][h];
        if (graph == null) return out;
        for (RoadGraph.Node n : graph.nodes()) {
            if (n.cellX < 0 || n.cellX >= w || n.cellY < 0 || n.cellY >= h) continue;
            out[n.cellX][n.cellY] = true;
        }
        for (RoadGraph.Edge e : graph.edges()) {
            int n = e.cellsX.length;
            for (int i = 0; i < n; i++) {
                int cx = e.cellsX[i];
                int cy = e.cellsY[i];
                if (cx < 0 || cx >= w || cy < 0 || cy >= h) continue;
                out[cx][cy] = true;
            }
        }
        return out;
    }
}
