package com.dillon.starsectormarines.battle.nav;

import java.util.BitSet;

/**
 * Every half-cell lattice point a body of one radius can reach from where a {@link ClearanceRoutePlanner} search
 * started, as that search found them when it proved its goal unreachable.
 *
 * <p>Proving a goal unreachable empties the search's frontier, so its workspace then holds exactly the start's
 * component of the lattice: the planner's own graph, its standing test and its swept links. Keeping that set costs
 * nothing more than the flood already did, and a mech choosing where to go can then prefer a cell it can actually
 * reach over one behind a wall it would only learn about from another whole-map flood.
 *
 * <p>The set is a preference, never a refusal. It was proved at {@link #topologyRevision()}; ground opened since is
 * missing from it, and a long straight sweep the planner may accept can join points the lattice keeps apart. So a
 * cell outside it is only less likely, and the route proof stays the authority.
 *
 * <p>Immutable once built, so readers on any thread may share one.
 */
public final class ReachableRegion {

    /** The planner's endpoint tolerance for a cell-centre goal ({@code AsyncClearanceRoutes}). */
    static final float GOAL_TOLERANCE = .75f;
    /** The planner's start attachment reach. */
    static final float ATTACHMENT_REACH = 1f;

    private final float radius;
    private final long topologyRevision;
    private final int latticeWidth;
    private final int latticeHeight;
    private final BitSet nodes;

    ReachableRegion(float radius, long topologyRevision, int latticeWidth, int latticeHeight, BitSet nodes) {
        this.radius = radius;
        this.topologyRevision = topologyRevision;
        this.latticeWidth = latticeWidth;
        this.latticeHeight = latticeHeight;
        this.nodes = nodes;
    }

    public float radius() { return radius; }

    public long topologyRevision() { return topologyRevision; }

    /** Lattice points held. */
    public int size() { return nodes.cardinality(); }

    /** Whether a body standing at ({@code x}, {@code y}) is inside: it attaches to a held point. */
    public boolean holds(float x, float y) {
        return anyWithin(x, y, ATTACHMENT_REACH);
    }

    /** Whether a route to the centre of cell ({@code cellX}, {@code cellY}) would end on a held point. */
    public boolean reaches(int cellX, int cellY) {
        return anyWithin(cellX + .5f, cellY + .5f, GOAL_TOLERANCE);
    }

    /** Whether the two regions share a point: the same component, proved twice. */
    boolean overlaps(ReachableRegion other) {
        return latticeWidth == other.latticeWidth && latticeHeight == other.latticeHeight
                && nodes.intersects(other.nodes);
    }

    /**
     * The reached cell nearest ({@code x}, {@code y}) within {@code maxRing} cells of it (Chebyshev rings, nearest by
     * distance within a ring, then lowest row and column), or {@code null} when none is.
     */
    public int[] nearestReachedCell(float x, float y, int maxRing) {
        int centerX = (int) Math.floor(x), centerY = (int) Math.floor(y);
        for (int ring = 0; ring <= maxRing; ring++) {
            int[] best = null;
            float bestDistance = Float.MAX_VALUE;
            for (int cy = centerY - ring; cy <= centerY + ring; cy++) {
                for (int cx = centerX - ring; cx <= centerX + ring; cx++) {
                    if (Math.max(Math.abs(cx - centerX), Math.abs(cy - centerY)) != ring) continue;
                    if (!reaches(cx, cy)) continue;
                    float dx = cx + .5f - x, dy = cy + .5f - y;
                    float distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new int[]{cx, cy};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean anyWithin(float x, float y, float reach) {
        int minX = (int) Math.max(0d, Math.ceil(((double) x - reach) * 2d));
        int maxX = (int) Math.min(latticeWidth - 1d, Math.floor(((double) x + reach) * 2d));
        int minY = (int) Math.max(0d, Math.ceil(((double) y - reach) * 2d));
        int maxY = (int) Math.min(latticeHeight - 1d, Math.floor(((double) y + reach) * 2d));
        double squared = (double) reach * reach;
        for (int ly = minY; ly <= maxY; ly++) {
            for (int lx = minX; lx <= maxX; lx++) {
                double dx = lx * .5d - x, dy = ly * .5d - y;
                if (dx * dx + dy * dy <= squared && nodes.get(ly * latticeWidth + lx)) return true;
            }
        }
        return false;
    }
}
