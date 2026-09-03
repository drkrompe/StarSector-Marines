package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Arrays;

/**
 * The connected components of a {@link VehicleClearance} mask, labelled once so
 * that "can this chassis get from here to there at all" is an array compare
 * rather than a search.
 *
 * <p>It exists because the convoy route proof was asking that question with the
 * router. {@code ConvoyMeans} enumerates perimeter entries against interior
 * junctions, and its reachability filter was the <em>road graph</em>, which
 * knows nothing about whether a full vehicle body fits anywhere along the way.
 * A destination that is graph-reachable and mask-unreachable makes the
 * cost-field A* flood the whole reachable component of the grid before
 * returning no route — about five milliseconds on a 560x336 map — and the
 * enumeration then does it again for the next candidate. One labelling pass
 * answers every one of those candidates.
 *
 * <p><b>What it is worth, honestly.</b> On the traced Conquest dispatch that
 * prompted it, this filter skipped nothing: every ranked junction there was
 * genuinely reachable for the chassis, and the 745 searches that stalled the
 * game thread went on kinematic retries rather than on unreachable ground —
 * which is what {@code ConvoyMeans.ROUTE_SEARCH_BUDGET} bounds. The case this
 * covers is the other one in the same log, where dispatch after dispatch
 * reported no complete route at the same price. A route proof that pays a
 * full-grid flood to learn something a labelling pass already knows is the
 * fault; that it is not the whole of the bill on one fixture does not make it
 * a fair charge.
 *
 * <p><b>The step rule is the pathfinder's own.</b> A component here must mean
 * exactly what the router means by reachable, so the flood expands through
 * {@link NavigationGrid#canTraverseCellStep(int, int, Direction)} — the same
 * walkability, reciprocal-edge and diagonal-corner contract
 * {@code GridPathfinder.canStep} applies — gated additionally on the clearance
 * mask, which is what {@code findPath}'s {@code passable} argument does. It
 * honours {@link GridPathfinder#USE_CARDINAL_NAVIGATION} for the same reason:
 * a diagonal the router will not take must not join two components here.
 *
 * <p>Pure: {@code (grid, mask) -> labels}. A vehicle-scoped derived view of an
 * already-derived mask, off every hot path — built per dispatch beside the
 * clearance snapshot it labels, and no more durable than that snapshot is.
 */
public final class ClearanceComponents {

    /** Label of a cell no vehicle of this clearance can stand on, and of any out-of-bounds cell. */
    public static final int NONE = -1;

    private final int width;
    private final int height;
    private final int[] labels;
    private final int componentCount;

    private ClearanceComponents(int width, int height, int[] labels, int componentCount) {
        this.width = width;
        this.height = height;
        this.labels = labels;
        this.componentCount = componentCount;
    }

    /**
     * Labels every passable cell of {@code clearance} with the index of the
     * component it belongs to, in one breadth-first sweep of the grid. Cells
     * outside the mask take {@link #NONE}.
     */
    public static ClearanceComponents of(NavigationGrid grid, VehicleClearance clearance) {
        int w = clearance.getWidth();
        int h = clearance.getHeight();
        int[] labels = new int[w * h];
        Arrays.fill(labels, NONE);
        int[] queue = new int[w * h];
        int directionCount = GridPathfinder.USE_CARDINAL_NAVIGATION ? 4 : 8;
        int nextLabel = 0;

        for (int seed = 0; seed < labels.length; seed++) {
            if (labels[seed] != NONE || !clearance.isPassableAt(seed)) continue;
            int label = nextLabel++;
            labels[seed] = label;
            int head = 0;
            int tail = 0;
            queue[tail++] = seed;
            while (head < tail) {
                int current = queue[head++];
                int cx = current % w;
                int cy = current / w;
                for (int d = 0; d < directionCount; d++) {
                    Direction direction = Direction.ALL[d];
                    int nx = cx + direction.dx;
                    int ny = cy + direction.dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int neighbour = ny * w + nx;
                    if (labels[neighbour] != NONE) continue;
                    if (!clearance.isPassableAt(neighbour)) continue;
                    if (!grid.canTraverseCellStep(cx, cy, direction)) continue;
                    labels[neighbour] = label;
                    queue[tail++] = neighbour;
                }
            }
        }
        return new ClearanceComponents(w, h, labels, nextLabel);
    }

    /** Component index at {@code (x, y)}, or {@link #NONE} off the mask or off the grid. */
    public int labelAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return NONE;
        return labels[y * width + x];
    }

    /**
     * True when both cells are on the mask and in the same component — i.e. the
     * router would find <em>some</em> path between them, whatever it costs.
     * False for either cell off the mask, which is the case a route proof must
     * not pay a search to discover.
     */
    public boolean connected(int fromX, int fromY, int toX, int toY) {
        int from = labelAt(fromX, fromY);
        return from != NONE && from == labelAt(toX, toY);
    }

    /** How many distinct components the mask has. Zero when nothing on the map fits the chassis. */
    public int componentCount() { return componentCount; }
}
