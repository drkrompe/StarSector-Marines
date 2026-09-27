package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.apache.log4j.Logger;

import java.util.Arrays;

/**
 * A* pathfinding on a {@link NavigationGrid}, 8-directional (cardinal + diagonal).
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.GridPathfinder}.
 * Dropped from the original: the Theta* line-of-sight parent-shortcut (which
 * gives any-angle smoothed paths) and the {@code toWorldPath} corner-offset
 * helper. Both can come back when we want smoother movement; for the auto-battler
 * MVP a chunky right-angle path is fine and the renderer interpolates between
 * cells anyway.
 *
 * <p>Diagonal moves require both adjacent cardinal edges to be passable
 * (dual-side check), the diagonal edge itself passable, and both adjacent
 * cells walkable — so units can't slip through diagonal wall gaps.
 *
 * <p><b>Performance:</b> per-search node state lives in flat parallel arrays
 * held in a {@link ThreadLocal} workspace — zero allocation after the first
 * call per thread. The open set is an indexed binary min-heap with O(log n)
 * decrease-key. Node state is encoded into {@code heapPos}:
 * <ul>
 *   <li>{@code >= 0}: open at that heap position</li>
 *   <li>{@code CLOSED}: expanded</li>
 *   <li>{@code UNSEEN}: never visited</li>
 * </ul>
 */
public final class GridPathfinder {

    /** Indexed, stable traversal gate for a search over lazily derived cells. */
    @FunctionalInterface
    public interface IndexedPassability {
        boolean isPassable(int index);
    }

    /** Indexed traversal multiplier; values must be finite and at least 1.0. */
    @FunctionalInterface
    public interface IndexedCost {
        float costAt(int index);

        /** Same cell with coordinates already decoded by the search. */
        default float costAt(int index, int x, int y) { return costAt(index); }
    }

    private static final Logger LOG = Logger.getLogger(GridPathfinder.class);
    private static final boolean PROFILE_PATH_REQUESTS =
            Boolean.getBoolean("battle.profile.pathRequests");

    public static boolean USE_CARDINAL_NAVIGATION = false;

    /**
     * Per-occupant extra cost added when stepping into a cell. A penalty of 2 makes
     * an occupied cell effectively cost 3 to enter (1 for the move + 2 for occupancy),
     * so A* prefers detours up to ~2 cells longer over walking through an ally.
     */
    public static final float OCCUPANCY_PENALTY = 2f;

    /** Same-build control retaining the unavoidable terminal occupancy toll. */
    public static final String OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY =
            "battle.pathfinding.omitFixedGoalOccupancy";

    private static final float SQRT2 = (float) Math.sqrt(2.0);
    private static final float INF = Float.MAX_VALUE;

    private static final int UNSEEN = -1;
    private static final int CLOSED = -2;

    private static final int[] DIR_DX;
    private static final int[] DIR_DY;
    private static final int[] DIR_EDGE_MASK;
    private static final int[] DIR_OPP_EDGE_MASK;
    private static final float[] DIR_COST;
    private static final boolean[] DIR_IS_DIAGONAL;
    private static final int[] DIR_CARD1_MASK;
    private static final int[] DIR_CARD2_MASK;
    private static final int[] DIR_OPP_CARD1_MASK;
    private static final int[] DIR_OPP_CARD2_MASK;
    private static final int[] DIR_ADJ1_DX;
    private static final int[] DIR_ADJ1_DY;
    private static final int[] DIR_ADJ2_DX;
    private static final int[] DIR_ADJ2_DY;
    /** Representative direction indices for equal-cost cardinal/diagonal steps. */
    static final int FIRST_CARDINAL_DIRECTION = 0;
    static final int FIRST_DIAGONAL_DIRECTION = 4;

    static {
        Direction[] all = Direction.ALL;
        int n = all.length;
        DIR_DX = new int[n];
        DIR_DY = new int[n];
        DIR_EDGE_MASK = new int[n];
        DIR_OPP_EDGE_MASK = new int[n];
        DIR_COST = new float[n];
        DIR_IS_DIAGONAL = new boolean[n];
        DIR_CARD1_MASK = new int[n];
        DIR_CARD2_MASK = new int[n];
        DIR_OPP_CARD1_MASK = new int[n];
        DIR_OPP_CARD2_MASK = new int[n];
        DIR_ADJ1_DX = new int[n];
        DIR_ADJ1_DY = new int[n];
        DIR_ADJ2_DX = new int[n];
        DIR_ADJ2_DY = new int[n];

        for (int i = 0; i < n; i++) {
            Direction d = all[i];
            DIR_DX[i] = d.dx;
            DIR_DY[i] = d.dy;
            DIR_EDGE_MASK[i] = 1 << d.bit();
            DIR_OPP_EDGE_MASK[i] = 1 << d.opposite().bit();
            DIR_COST[i] = d.isDiagonal() ? SQRT2 : 1.0f;
            DIR_IS_DIAGONAL[i] = d.isDiagonal();
        }

        int neIdx = Direction.NE.ordinal();
        DIR_CARD1_MASK[neIdx]     = 1 << Direction.N.bit();
        DIR_CARD2_MASK[neIdx]     = 1 << Direction.E.bit();
        DIR_OPP_CARD1_MASK[neIdx] = 1 << Direction.S.bit();
        DIR_OPP_CARD2_MASK[neIdx] = 1 << Direction.W.bit();
        DIR_ADJ1_DX[neIdx] = 1; DIR_ADJ1_DY[neIdx] = 0;
        DIR_ADJ2_DX[neIdx] = 0; DIR_ADJ2_DY[neIdx] = 1;

        int seIdx = Direction.SE.ordinal();
        DIR_CARD1_MASK[seIdx]     = 1 << Direction.S.bit();
        DIR_CARD2_MASK[seIdx]     = 1 << Direction.E.bit();
        DIR_OPP_CARD1_MASK[seIdx] = 1 << Direction.N.bit();
        DIR_OPP_CARD2_MASK[seIdx] = 1 << Direction.W.bit();
        DIR_ADJ1_DX[seIdx] = 1; DIR_ADJ1_DY[seIdx] = 0;
        DIR_ADJ2_DX[seIdx] = 0; DIR_ADJ2_DY[seIdx] = -1;

        int swIdx = Direction.SW.ordinal();
        DIR_CARD1_MASK[swIdx]     = 1 << Direction.S.bit();
        DIR_CARD2_MASK[swIdx]     = 1 << Direction.W.bit();
        DIR_OPP_CARD1_MASK[swIdx] = 1 << Direction.N.bit();
        DIR_OPP_CARD2_MASK[swIdx] = 1 << Direction.E.bit();
        DIR_ADJ1_DX[swIdx] = -1; DIR_ADJ1_DY[swIdx] = 0;
        DIR_ADJ2_DX[swIdx] = 0;  DIR_ADJ2_DY[swIdx] = -1;

        int nwIdx = Direction.NW.ordinal();
        DIR_CARD1_MASK[nwIdx]     = 1 << Direction.N.bit();
        DIR_CARD2_MASK[nwIdx]     = 1 << Direction.W.bit();
        DIR_OPP_CARD1_MASK[nwIdx] = 1 << Direction.S.bit();
        DIR_OPP_CARD2_MASK[nwIdx] = 1 << Direction.E.bit();
        DIR_ADJ1_DX[nwIdx] = -1; DIR_ADJ1_DY[nwIdx] = 0;
        DIR_ADJ2_DX[nwIdx] = 0;  DIR_ADJ2_DY[nwIdx] = 1;
    }

    /** Shared empty-path sentinel — returned when no path exists or endpoints are blocked. Avoids per-call {@code new int[0]} allocations. */
    public static final int[] EMPTY_PATH = new int[0];

    private GridPathfinder() {}

    // ----- ThreadLocal workspace -----

    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    private static final class Workspace {
        float[] gCost = new float[0];
        float[] fCost = new float[0];
        int[] parentIdx = new int[0];
        int[] heapPos = new int[0];
        int[] heap = new int[0];

        int[] touchedIndices = new int[0];
        int touchedCount = 0;
        int expandedNodes;

        void ensureCapacity(int totalCells) {
            if (gCost.length < totalCells) {
                gCost = new float[totalCells];
                fCost = new float[totalCells];
                parentIdx = new int[totalCells];
                heapPos = new int[totalCells];
                Arrays.fill(gCost, INF);
                Arrays.fill(fCost, INF);
                Arrays.fill(heapPos, UNSEEN);
                touchedIndices = new int[totalCells];
                touchedCount = 0;
            }
        }

        /**
         * Grow the open set, <b>keeping what is already in it</b>.
         *
         * <p>Reallocating without the copy discards the heap mid-search. Every
         * slot then reads back as zero, which is a valid cell index rather than
         * a sentinel, so the search continues over a cell it never opened and
         * writes a heap position for it. That write is the lasting damage: the
         * workspace is cleaned by replaying the cells a search touched, cell
         * zero was never on that list, and so its bogus position survives into
         * every later search on the thread — including searches on an entirely
         * different, smaller grid, which then abandon routes that plainly
         * exist.
         *
         * <p>Observed as exactly that: a fully open thirty-two by twenty-four
         * grid where every path returned empty after expanding two nodes, with
         * {@code heapPos[0]} left reading 1075 after the workspace had been
         * cleaned. It stays hidden because it needs a frontier past the initial
         * capacity to trigger, and because the symptom is an empty path, which
         * is indistinguishable from an honest "no route".
         */
        void ensureHeapCapacity(int required) {
            if (heap.length < required) {
                heap = Arrays.copyOf(heap,
                        Math.max(heap.length * 2, Math.max(required, 1024)));
            }
        }

        void resetTouched() {
            for (int i = 0; i < touchedCount; i++) {
                int idx = touchedIndices[i];
                gCost[idx] = INF;
                fCost[idx] = INF;
                heapPos[idx] = UNSEEN;
            }
            touchedCount = 0;
        }

        void touch(int idx) {
            touchedIndices[touchedCount++] = idx;
        }
    }

    // ----- Public API -----

    public static int[] findPath(NavigationGrid grid, int startX, int startY, int goalX, int goalY) {
        return findPath(grid, startX, startY, goalX, goalY, USE_CARDINAL_NAVIGATION, null);
    }

    /**
     * Geometric A* with a conservative cost ceiling derived from a caller's
     * eventual step-count limit. Every route of at most {@code maxSteps} has
     * cost at most {@code maxSteps * sqrt(2)} (or {@code maxSteps} for cardinal
     * movement). Once the minimum frontier estimate exceeds that ceiling no
     * route the caller would accept can remain. Expansion order below the
     * ceiling is unchanged, including ties; the returned route must still be
     * checked against the caller's step limit because diagonals cost more.
     *
     * <p>An empty answer means no acceptable bounded proof, not necessarily
     * structural disconnection. No occupancy or terrain multipliers are used.
     */
    public static int[] findPathWithinStepEnvelope(NavigationGrid grid,
                                                   int startX, int startY,
                                                   int goalX, int goalY,
                                                   boolean cardinalOnly, int maxSteps) {
        if (maxSteps < 0) throw new IllegalArgumentException("maxSteps must be nonnegative");
        float maximumCost = maxSteps * (cardinalOnly ? 1f : SQRT2);
        // Accumulated g and heuristic addition each round in float. A generous
        // per-step ULP allowance can only do extra work, never tighten refusal.
        float ceiling = maximumCost + (maxSteps + 2f) * Math.ulp(maximumCost);
        return findPathProfiled(grid, startX, startY, goalX, goalY,
                cardinalOnly, null, null, null, null, null, true, ceiling);
    }

    /**
     * Labels every walkable cell with the id of its connected component, using
     * the same {@link #canStep} rule (and the same {@link #USE_CARDINAL_NAVIGATION}
     * setting) the search itself expands with. Non-walkable cells get {@code -1}.
     * Result is indexed by {@link NavigationGrid#index(int, int)}.
     *
     * <p>Two walkable cells share a component exactly when {@link #findPath}
     * would find a route between them: whether a path exists is a question about
     * connectivity, and step <em>costs</em> cannot change the answer. So one
     * O(cells) flood answers "is this reachable" for every pair on the grid at
     * once, where asking per pair runs a full search each time — and the pairs
     * that are <em>not</em> connected are the expensive ones, since a failed A*
     * exhausts the whole reachable region before returning empty.
     *
     * <p>The step rule lives in {@code canStep} and is called from here rather
     * than reimplemented, so the labeling cannot drift from the search: the
     * diagonal corner rules and the dual-side edge checks are symmetric, which
     * is what makes an undirected component labeling valid in the first place.
     *
     * <p>Intended for a caller holding a grid fixed across many queries (see
     * {@code CommandTopology}). It reads the grid once; a later mutation is not
     * reflected.
     */
    public static int[] labelConnectedComponents(NavigationGrid grid) {
        return labelConnectedComponents(grid, USE_CARDINAL_NAVIGATION);
    }

    static int[] labelConnectedComponents(NavigationGrid grid,
                                          boolean cardinalOnly) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        int totalCells = w * h;

        int[] component = new int[totalCells];
        Arrays.fill(component, -1);

        long[] cellFlags = grid.getCellFlagsArray();
        byte[] edgePass  = grid.getEdgePassabilityArray();
        int dirCount = cardinalOnly ? 4 : 8;

        int[] stack = new int[totalCells];
        int nextComponent = 0;

        for (int seed = 0; seed < totalCells; seed++) {
            if (component[seed] >= 0) continue;
            if ((cellFlags[seed] & CellTag.WALKABLE.mask()) == 0L) continue;

            int id = nextComponent++;
            int stackSize = 0;
            component[seed] = id;
            stack[stackSize++] = seed;

            while (stackSize > 0) {
                int currentIdx = stack[--stackSize];
                int cx = currentIdx % w;
                int cy = currentIdx / w;

                for (int dirI = 0; dirI < dirCount; dirI++) {
                    int nx = cx + DIR_DX[dirI];
                    int ny = cy + DIR_DY[dirI];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;

                    int nIdx = ny * w + nx;
                    if (component[nIdx] >= 0) continue;
                    if (!canStep(currentIdx, cx, cy, nIdx, dirI, w, h,
                            cellFlags, edgePass, null)) continue;

                    component[nIdx] = id;
                    stack[stackSize++] = nIdx;
                }
            }
        }
        return component;
    }

    /**
     * Overload accepting a per-cell occupancy count — typically the unit count
     * standing on each cell, packed into a {@code byte[]} indexed by
     * {@link NavigationGrid#index(int, int)}. The pathfinder adds
     * {@link #OCCUPANCY_PENALTY} × {@code occupancy[idx]} to the cost of
     * stepping into each cell, so A* routes around stacked allies when a
     * reasonable detour exists. For a fixed goal, its unavoidable additive
     * occupancy toll is omitted from the search objective by default: every
     * complete route pays it once, so it cannot favor one route over another.
     * Intermediate occupancy and direction-sensitive goal terrain are retained.
     * Float-rounding and equal-cost route ties may differ from the control.
     * Pass {@code null} for vanilla pathing.
     */
    public static int[] findPath(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                  byte[] occupancy) {
        return findPath(grid, startX, startY, goalX, goalY, USE_CARDINAL_NAVIGATION, occupancy, null, null);
    }

    /**
     * Cost-field overload for vehicle routing. {@code costField}, when non-null,
     * is a per-cell traversal multiplier indexed by {@link NavigationGrid#index}:
     * the step cost into a cell becomes {@code DIR_COST[dir] × costField[nIdx]},
     * so search prefers cheap terrain (roads at baseline 1.0) yet crosses dearer
     * terrain when it shortens the route. {@code passable}, when non-null, gates
     * traversal in place of raw walkability — a vehicle-clearance mask, so the
     * route can never thread a gap the footprint can't fit. Both endpoints must
     * be passable. Baseline cost 1.0 keeps the octile heuristic admissible.
     *
     * <p>Generalizes the {@link #OCCUPANCY_PENALTY} hook (the occupancy path adds
     * a flat per-occupant penalty; this multiplies by terrain). Infantry callers
     * keep their existing signatures untouched.
     */
    public static int[] findPath(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                  float[] costField, boolean[] passable) {
        return findPath(grid, startX, startY, goalX, goalY, USE_CARDINAL_NAVIGATION, null, costField, passable);
    }

    /**
     * Returns a path from start to goal as a flat {@code int[]} of interleaved
     * {@code x,y} pairs — cell {@code i} is {@code (result[i*2], result[i*2+1])},
     * and cell count is {@code result.length / 2}. Returns {@link #EMPTY_PATH}
     * if either endpoint is non-walkable or no path exists; the start and goal
     * are both included when a path is returned.
     *
     * <p>One {@code int[]} allocation per call — replaces the prior
     * {@code List<int[]>} which allocated an {@code ArrayList} plus an
     * {@code int[2]} per cell, killing the per-pathfind GC spike that showed
     * up during firefights.
     */
    public static int[] findPath(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                  boolean cardinalOnly, byte[] occupancy) {
        return findPath(grid, startX, startY, goalX, goalY, cardinalOnly, occupancy, null, null);
    }

    /** Full-control overload threading both the occupancy penalty and the cost-field / clearance gate. */
    public static int[] findPath(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                  boolean cardinalOnly, byte[] occupancy, float[] costField, boolean[] passable) {
        return findPathProfiled(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, costField, passable, null, null, true);
    }

    /** Infantry cost snapshot, read directly without expanding compact block storage. */
    public static int[] findPathWithCost(NavigationGrid grid,
                                  int startX, int startY, int goalX, int goalY,
                                  boolean cardinalOnly, byte[] occupancy, RouteCostField cost) {
        return findPathProfiled(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, null, null, cost, null, true);
    }

    /**
     * Vehicle-search path that asks for clearance and terrain cost only as A*
     * reaches a cell. Both indexed views must remain stable for this search.
     * The raw-grid component precheck is deliberately omitted: it can require
     * a whole-map label build and cannot prove clearance connectivity anyway.
     */
    public static int[] findPathOnDemand(NavigationGrid grid,
                                         int startX, int startY, int goalX, int goalY,
                                         IndexedCost cost, IndexedPassability passable) {
        return findPathProfiled(grid, startX, startY, goalX, goalY,
                USE_CARDINAL_NAVIGATION, null, null, null, cost, passable, false);
    }

    /** Starts a search whose A* frontier can be advanced across simulation ticks. */
    public static OnDemandSearch beginOnDemand(NavigationGrid grid,
                                                int startX, int startY,
                                                int goalX, int goalY,
                                                IndexedCost cost,
                                                IndexedPassability passable) {
        return new OnDemandSearch(grid, startX, startY, goalX, goalY,
                USE_CARDINAL_NAVIGATION, cost, passable);
    }

    /**
     * Search-owned A* state for a frozen grid and stable indexed views. Unlike
     * the ordinary pathfinder workspace, these arrays and this heap cannot be
     * disturbed by another search between calls to {@link #advance(int)}.
     */
    public static final class OnDemandSearch {
        public enum Status { PENDING, ROUTED, NO_ROUTE }

        private static final byte UNVISITED = 0;
        private static final byte OPEN = 1;
        private static final byte DONE = 2;

        private final NavigationGrid grid;
        private final IndexedCost cost;
        private final IndexedPassability passable;
        private final boolean cardinalOnly;
        private final int width;
        private final int height;
        private final int totalCells;
        private final int startIdx;
        private final int goalIdx;
        private final int goalX;
        private final int goalY;
        private final float[] gCost;
        private final float[] fCost;
        private final int[] parentIdx;
        private final int[] heapPos;
        private final byte[] state;
        private int[] heap = new int[64];
        private int heapSize;
        private int expandedNodes;
        private Status status = Status.PENDING;
        private int[] path = EMPTY_PATH;

        private OnDemandSearch(NavigationGrid grid,
                               int startX, int startY, int goalX, int goalY,
                               boolean cardinalOnly, IndexedCost cost,
                               IndexedPassability passable) {
            this.grid = grid;
            this.cost = cost;
            this.passable = passable;
            this.cardinalOnly = cardinalOnly;
            this.width = grid.getWidth();
            this.height = grid.getHeight();
            this.totalCells = width * height;
            this.goalX = goalX;
            this.goalY = goalY;
            this.gCost = new float[totalCells];
            this.fCost = new float[totalCells];
            this.parentIdx = new int[totalCells];
            this.heapPos = new int[totalCells];
            this.state = new byte[totalCells];
            if (!grid.isWalkable(startX, startY) || !grid.isWalkable(goalX, goalY)) {
                startIdx = -1;
                goalIdx = -1;
                status = Status.NO_ROUTE;
                return;
            }
            startIdx = startY * width + startX;
            goalIdx = goalY * width + goalX;
            if (passable != null && (!passable.isPassable(startIdx)
                    || !passable.isPassable(goalIdx))) {
                status = Status.NO_ROUTE;
                return;
            }
            if (startIdx == goalIdx) {
                path = new int[]{startX, startY};
                status = Status.ROUTED;
                return;
            }
            gCost[startIdx] = 0f;
            fCost[startIdx] = heuristic(startX, startY, goalX, goalY, cardinalOnly);
            parentIdx[startIdx] = startIdx;
            heap[0] = startIdx;
            heapPos[startIdx] = 0;
            state[startIdx] = OPEN;
            heapSize = 1;
        }

        /** Expands at most this many nodes; zero preserves the current state. */
        public Status advance(int maxExpandedNodes) {
            if (maxExpandedNodes < 0) {
                throw new IllegalArgumentException("maxExpandedNodes must be nonnegative");
            }
            if (status != Status.PENDING || maxExpandedNodes == 0) return status;
            long[] cellFlags = grid.getCellFlagsArray();
            byte[] edgePass = grid.getEdgePassabilityArray();
            int dirCount = cardinalOnly ? 4 : 8;
            int spent = 0;
            while (heapSize > 0 && spent < maxExpandedNodes) {
                int currentIdx = heap[0];
                heapSize--;
                if (heapSize > 0) {
                    heap[0] = heap[heapSize];
                    heapPos[heap[0]] = 0;
                    heapSiftDown(heap, heapPos, fCost, 0, heapSize);
                }
                if (state[currentIdx] == DONE) continue;
                state[currentIdx] = DONE;
                expandedNodes++;
                spent++;
                if (currentIdx == goalIdx) {
                    path = reconstructPath(parentIdx, width, totalCells,
                            startIdx, goalIdx);
                    status = path == EMPTY_PATH ? Status.NO_ROUTE : Status.ROUTED;
                    return status;
                }
                int cx = currentIdx % width;
                int cy = currentIdx / width;
                for (int dirI = 0; dirI < dirCount; dirI++) {
                    int nx = cx + DIR_DX[dirI];
                    int ny = cy + DIR_DY[dirI];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int nIdx = ny * width + nx;
                    if (!canStep(currentIdx, cx, cy, nIdx, dirI, width, height,
                            cellFlags, edgePass, null, passable)) continue;
                    if (state[nIdx] == DONE) continue;
                    float step = DIR_COST[dirI];
                    if (cost != null) step *= cost.costAt(nIdx);
                    float nextG = gCost[currentIdx] + step;
                    if (state[nIdx] == UNVISITED || nextG < gCost[nIdx]) {
                        gCost[nIdx] = nextG;
                        fCost[nIdx] = nextG + heuristic(nx, ny, goalX, goalY,
                                cardinalOnly);
                        parentIdx[nIdx] = currentIdx;
                        if (state[nIdx] == OPEN) {
                            heapSiftUp(heap, heapPos, fCost, heapPos[nIdx]);
                        } else {
                            if (heapSize == heap.length) {
                                heap = Arrays.copyOf(heap, heap.length * 2);
                            }
                            heap[heapSize] = nIdx;
                            heapPos[nIdx] = heapSize;
                            state[nIdx] = OPEN;
                            heapSiftUp(heap, heapPos, fCost, heapSize);
                            heapSize++;
                        }
                    }
                }
            }
            if (heapSize == 0) status = Status.NO_ROUTE;
            return status;
        }

        public Status status() { return status; }

        /** Empty until a route is complete; also empty on terminal no-route. */
        public int[] path() { return path; }

        /** Cumulative A* node expansions, including all prior advances. */
        public int expandedNodes() { return expandedNodes; }

        /**
         * Whether this cell belongs to the fully exhausted reachable region.
         * Meaningful only after a no-route search: pending frontier cells are
         * not yet a connectivity proof.
         */
        public boolean exhaustedReachable(int index) {
            return status == Status.NO_ROUTE && index >= 0
                    && index < totalCells && state[index] == DONE;
        }
    }

    private static int[] findPathProfiled(NavigationGrid grid,
                                          int startX, int startY, int goalX, int goalY,
                                          boolean cardinalOnly, byte[] occupancy,
                                          float[] costField, boolean[] passable,
                                          IndexedCost indexedCost,
                                          IndexedPassability indexedPassable,
                                          boolean checkComponents) {
        return findPathProfiled(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, costField, passable,
                indexedCost, indexedPassable, checkComponents, INF);
    }

    private static int[] findPathProfiled(NavigationGrid grid,
                                          int startX, int startY, int goalX, int goalY,
                                          boolean cardinalOnly, byte[] occupancy,
                                          float[] costField, boolean[] passable,
                                          IndexedCost indexedCost,
                                          IndexedPassability indexedPassable,
                                          boolean checkComponents, float costCeiling) {
        long _profT0 = System.nanoTime();
        Workspace profileWorkspace = WORKSPACE.get();
        profileWorkspace.expandedNodes = 0;
        int[] result = EMPTY_PATH;
        try {
            result = findPathInner(grid, startX, startY, goalX, goalY,
                    cardinalOnly, occupancy, costField, passable,
                    indexedCost, indexedPassable, checkComponents, false, costCeiling);
            return result;
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) {
                long durationNanos = System.nanoTime() - _profT0;
                p.record(TickInnerProfile.Bucket.PATHFIND, durationNanos);
                p.recordPathSearch(durationNanos, startX, startY, goalX, goalY,
                        occupancy != null, result.length / 2,
                        profileWorkspace.expandedNodes,
                        goalOccupancy(grid, goalX, goalY, occupancy), "");
                if (PROFILE_PATH_REQUESTS) {
                    p.recordPathfindRequest(startX, startY, goalX, goalY,
                            occupancy != null);
                }
            }
        }
    }

    /**
     * Package-private refinement seam for composite pathfinders that own the
     * outer profiling scope. Semantics are identical to the full-control public
     * overload, but this call does not record another nested path request.
     */
    static int[] findPathUnprofiled(NavigationGrid grid,
                                    int startX, int startY,
                                    int goalX, int goalY,
                                    boolean cardinalOnly, byte[] occupancy,
                                    float[] costField, boolean[] passable) {
        return findPathInner(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, costField, passable, null, null, true, false);
    }

    /** Compact-cost counterpart for callers that own the outer profiling scope. */
    static int[] findPathWithCostUnprofiled(NavigationGrid grid,
                                            int startX, int startY, int goalX, int goalY,
                                            boolean cardinalOnly, byte[] occupancy,
                                            RouteCostField cost) {
        return findPathInner(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, null, null, cost, null, true, false);
    }

    /** Serial squad preparation owns timing; keep its expansions separate from member A*. */
    static int[] findSquadRouteSeed(NavigationGrid grid, int startX, int startY,
                                   int goalX, int goalY, boolean cardinalOnly,
                                   RouteCostField cost) {
        WORKSPACE.get().expandedNodes = 0;
        return findPathWithCostUnprofiled(grid, startX, startY, goalX, goalY,
                cardinalOnly, null, cost);
    }

    /** Read immediately after a squad seed, before another search reuses this thread's scratch. */
    static int squadSeedExpandedNodes() {
        return WORKSPACE.get().expandedNodes;
    }

    /** Records search detail without double-counting its caller's PATHFIND scope. */
    static int[] findPathWithCostSampled(NavigationGrid grid,
                                         int startX, int startY, int goalX, int goalY,
                                         boolean cardinalOnly, byte[] occupancy,
                                         RouteCostField cost, String fallbackReason) {
        long started = System.nanoTime();
        Workspace workspace = WORKSPACE.get();
        workspace.expandedNodes = 0;
        int[] result = EMPTY_PATH;
        try {
            result = findPathWithCostUnprofiled(grid, startX, startY, goalX, goalY,
                    cardinalOnly, occupancy, cost);
            return result;
        } finally {
            TickInnerProfile profile = TickInnerProfile.current();
            if (profile != null) {
                long elapsed = System.nanoTime() - started;
                if ("UNCOVERED_START".equals(fallbackReason)) {
                    profile.record(TickInnerProfile.Bucket.SQUAD_ROUTE_UNCOVERED_FALLBACK, elapsed);
                    profile.recordCount(TickInnerProfile.Bucket.SQUAD_ROUTE_UNCOVERED_EXPANDED,
                            workspace.expandedNodes);
                }
                profile.recordPathSearch(elapsed,
                        startX, startY, goalX, goalY, occupancy != null,
                        result.length / 2, workspace.expandedNodes,
                        goalOccupancy(grid, goalX, goalY, occupancy), fallbackReason);
            }
        }
    }

    private static int goalOccupancy(NavigationGrid grid, int x, int y, byte[] occupancy) {
        if (x < 0 || x >= grid.getWidth() || y < 0 || y >= grid.getHeight()) return -1;
        return occupancy == null ? 0 : occupancy[grid.index(x, y)] & 0xFF;
    }

    /** Cancelable unprofiled search used only by the battle-owned async worker. */
    static int[] findPathAsyncUnprofiled(NavigationGrid grid,
                                         int startX, int startY,
                                         int goalX, int goalY,
                                         boolean cardinalOnly, byte[] occupancy) {
        return findPathInner(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, null, null, null, null, true, true);
    }

    /** Full A* seam for validating the connectivity rejection independently. */
    static int[] findPathWithoutComponentCheck(
            NavigationGrid grid, int startX, int startY,
            int goalX, int goalY, boolean cardinalOnly) {
        return findPathInner(grid, startX, startY, goalX, goalY,
                cardinalOnly, null, null, null, null, null, false, false);
    }

    private static int[] findPathInner(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                        boolean cardinalOnly, byte[] occupancy,
                                        float[] costField, boolean[] passable,
                                        IndexedCost indexedCost,
                                        IndexedPassability indexedPassable,
                                        boolean checkComponents,
                                        boolean cancelable) {
        return findPathInner(grid, startX, startY, goalX, goalY,
                cardinalOnly, occupancy, costField, passable, indexedCost,
                indexedPassable, checkComponents, cancelable, INF);
    }

    private static int[] findPathInner(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                        boolean cardinalOnly, byte[] occupancy,
                                        float[] costField, boolean[] passable,
                                        IndexedCost indexedCost,
                                        IndexedPassability indexedPassable,
                                        boolean checkComponents,
                                        boolean cancelable, float costCeiling) {
        if (!grid.isWalkable(startX, startY) || !grid.isWalkable(goalX, goalY)) {
            return EMPTY_PATH;
        }
        if (checkComponents && !grid.arePathConnected(startX, startY, goalX, goalY,
                cardinalOnly)) {
            return EMPTY_PATH;
        }

        int w = grid.getWidth();
        int h = grid.getHeight();
        int totalCells = w * h;

        int startIdx = startY * w + startX;
        int goalIdx  = goalY  * w + goalX;

        // Clearance gate: a vehicle whose footprint can't sit on either endpoint
        // has no route. (Endpoint snapping for eroded perimeter cells is slice 2's job.)
        if ((passable != null && (!passable[startIdx] || !passable[goalIdx]))
                || (indexedPassable != null
                && (!indexedPassable.isPassable(startIdx)
                || !indexedPassable.isPassable(goalIdx)))) {
            return EMPTY_PATH;
        }
        if (startX == goalX && startY == goalY) {
            return new int[]{startX, startY};
        }

        Workspace ws = WORKSPACE.get();
        ws.ensureCapacity(totalCells);
        ws.resetTouched();

        float[] gCost = ws.gCost;
        float[] fCost = ws.fCost;
        int[] parentIdx = ws.parentIdx;
        int[] heapPos = ws.heapPos;

        int dirCount = cardinalOnly ? 4 : 8;

        gCost[startIdx] = 0;
        fCost[startIdx] = heuristic(startX, startY, goalX, goalY, cardinalOnly);
        parentIdx[startIdx] = startIdx;
        ws.touch(startIdx);

        ws.ensureHeapCapacity(1);
        int[] heap = ws.heap;
        heap[0] = startIdx;
        heapPos[startIdx] = 0;
        int heapSize = 1;

        long[] cellFlags = grid.getCellFlagsArray();
        byte[] edgePass  = grid.getEdgePassabilityArray();

        // Every completed route enters this fixed goal exactly once, so its
        // additive occupancy toll cannot affect which route is cheapest. Keeping
        // it in g while omitting it from the heuristic needlessly floods the
        // frontier before the goal can win. Omit only that toll, not terrain
        // (whose diagonal multiplier depends on the approach) or intermediate
        // occupancy. This changes float rounding/ties, not the real cost model.
        boolean omitGoalOccupancy = occupancy != null && Boolean.parseBoolean(
                System.getProperty(OMIT_FIXED_GOAL_OCCUPANCY_PROPERTY, "true"));

        while (heapSize > 0) {
            // Do not prune individual neighbors: preserving the heap's exact
            // contents/order keeps ordinary A* route ties below the bound.
            if (fCost[heap[0]] > costCeiling) return EMPTY_PATH;
            int currentIdx = heap[0];
            heapSize--;
            if (heapSize > 0) {
                heap[0] = heap[heapSize];
                heapPos[heap[0]] = 0;
                heapSiftDown(heap, heapPos, fCost, 0, heapSize);
            }

            if (heapPos[currentIdx] == CLOSED) continue;
            heapPos[currentIdx] = CLOSED;
            ws.expandedNodes++;
            // Only the async overload observes cancellation. Existing
            // synchronous searches keep their original semantics.
            if (cancelable && (ws.expandedNodes & 1023) == 0
                    && Thread.currentThread().isInterrupted()) return EMPTY_PATH;

            if (currentIdx == goalIdx) {
                return reconstructPath(parentIdx, w, totalCells,
                        startIdx, goalIdx);
            }

            int cx = currentIdx % w;
            int cy = currentIdx / w;

            for (int dirI = 0; dirI < dirCount; dirI++) {
                int nx = cx + DIR_DX[dirI];
                int ny = cy + DIR_DY[dirI];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;

                int nIdx = ny * w + nx;

                if (!canStep(currentIdx, cx, cy, nIdx, dirI, w, h,
                        cellFlags, edgePass, passable, indexedPassable)) continue;

                if (heapPos[nIdx] == CLOSED) continue;

                byte[] stepOccupancy = omitGoalOccupancy && nIdx == goalIdx ? null : occupancy;
                float stepCost = indexedCost == null
                        ? stepCost(dirI, nIdx, stepOccupancy, costField)
                        : stepCost(dirI, nIdx, stepOccupancy, indexedCost.costAt(nIdx, nx, ny));
                float tentativeG = gCost[currentIdx] + stepCost;

                if (tentativeG < gCost[nIdx]) {
                    if (heapPos[nIdx] == UNSEEN) ws.touch(nIdx);

                    gCost[nIdx] = tentativeG;
                    fCost[nIdx] = tentativeG + heuristic(nx, ny, goalX, goalY, cardinalOnly);
                    parentIdx[nIdx] = currentIdx;

                    int currentPos = heapPos[nIdx];
                    if (currentPos >= 0) {
                        heapSiftUp(heap, heapPos, fCost, currentPos);
                    } else {
                        ws.ensureHeapCapacity(heapSize + 1);
                        heap = ws.heap; // may have been reallocated
                        heap[heapSize] = nIdx;
                        heapPos[nIdx] = heapSize;
                        heapSiftUp(heap, heapPos, fCost, heapSize);
                        heapSize++;
                    }
                }
            }
        }

        return EMPTY_PATH;
    }

    // ----- Path reconstruction -----

    /**
     * Walks the parent chain back from goal to start, counting cells, then
     * allocates one right-sized {@code int[]} and fills it forward (writes
     * cell {@code i} into slots {@code [i*2, i*2+1]}). Single allocation per
     * call.
     */
    private static int[] reconstructPath(int[] parentIdx, int gridWidth,
                                         int totalCells, int startIdx,
                                         int goalIdx) {
        int cellCount = 0;
        int cursor = goalIdx;
        while (cellCount < totalCells) {
            if (cursor < 0 || cursor >= totalCells) {
                return invalidParentChain("parent index out of bounds",
                        startIdx, goalIdx, cursor, cellCount, totalCells);
            }
            cellCount++;
            if (cursor == startIdx) break;
            int parent = parentIdx[cursor];
            if (parent == cursor) {
                return invalidParentChain("self-parent before start",
                        startIdx, goalIdx, cursor, cellCount, totalCells);
            }
            cursor = parent;
        }
        if (cursor != startIdx) {
            return invalidParentChain("cycle or overlong parent chain",
                    startIdx, goalIdx, cursor, cellCount, totalCells);
        }

        int[] path = new int[cellCount * 2];
        cursor = goalIdx;
        int writeCell = cellCount - 1;
        while (writeCell >= 0) {
            path[writeCell * 2]     = cursor % gridWidth;
            path[writeCell * 2 + 1] = cursor / gridWidth;
            if (cursor == startIdx) break;
            cursor = parentIdx[cursor];
            writeCell--;
        }
        return path;
    }

    private static int[] invalidParentChain(String reason, int startIdx,
                                            int goalIdx, int cursor,
                                            int traversed, int totalCells) {
        LOG.error("GridPathfinder: rejecting invalid A* parent chain ("
                + reason + ") startIdx=" + startIdx + " goalIdx=" + goalIdx
                + " cursor=" + cursor + " traversed=" + traversed
                + " totalCells=" + totalCells);
        return EMPTY_PATH;
    }

    /** Package-private seam for corrupt-parent-chain regression tests. */
    static int[] reconstructPathForTest(int[] parentIdx, int gridWidth,
                                        int totalCells, int startIdx,
                                        int goalIdx) {
        return reconstructPath(parentIdx, gridWidth, totalCells,
                startIdx, goalIdx);
    }

    // ----- Heuristics and heap -----

    private static float heuristic(int x0, int y0, int x1, int y1, boolean cardinalOnly) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        if (cardinalOnly) return dx + dy; // Manhattan
        return Math.max(dx, dy) + (SQRT2 - 1) * Math.min(dx, dy); // Octile
    }

    static int directionCount(boolean cardinalOnly) {
        return cardinalOnly ? 4 : 8;
    }

    static int directionX(int direction) { return DIR_DX[direction]; }
    static int directionY(int direction) { return DIR_DY[direction]; }

    /** Shared forward-edge contract used by A* and reverse-field construction. */
    static boolean canStep(int fromIdx, int fromX, int fromY,
                           int toIdx, int direction, int width, int height,
                           long[] cellFlags, byte[] edgePass,
                           boolean[] passable) {
        return canStep(fromIdx, fromX, fromY, toIdx, direction, width, height,
                cellFlags, edgePass, passable, null);
    }

    private static boolean canStep(int fromIdx, int fromX, int fromY,
                                   int toIdx, int direction, int width, int height,
                                   long[] cellFlags, byte[] edgePass,
                                   boolean[] passable, IndexedPassability indexedPassable) {
        if ((cellFlags[toIdx] & 1L) == 0L) return false;
        if (passable != null && !passable[toIdx]) return false;
        if (indexedPassable != null && !indexedPassable.isPassable(toIdx)) return false;
        if ((edgePass[fromIdx] & DIR_EDGE_MASK[direction]) == 0) return false;
        if ((edgePass[toIdx] & DIR_OPP_EDGE_MASK[direction]) == 0) return false;
        if (!DIR_IS_DIAGONAL[direction]) return true;

        if ((edgePass[fromIdx] & DIR_CARD1_MASK[direction]) == 0) return false;
        if ((edgePass[fromIdx] & DIR_CARD2_MASK[direction]) == 0) return false;
        if ((edgePass[toIdx] & DIR_OPP_CARD1_MASK[direction]) == 0) return false;
        if ((edgePass[toIdx] & DIR_OPP_CARD2_MASK[direction]) == 0) return false;
        int adjacent1X = fromX + DIR_ADJ1_DX[direction];
        int adjacent1Y = fromY + DIR_ADJ1_DY[direction];
        int adjacent2X = fromX + DIR_ADJ2_DX[direction];
        int adjacent2Y = fromY + DIR_ADJ2_DY[direction];
        if (adjacent1X < 0 || adjacent1X >= width
                || adjacent1Y < 0 || adjacent1Y >= height) return false;
        if (adjacent2X < 0 || adjacent2X >= width
                || adjacent2Y < 0 || adjacent2Y >= height) return false;
        return (cellFlags[adjacent1Y * width + adjacent1X] & 1L) != 0L
                && (cellFlags[adjacent2Y * width + adjacent2X] & 1L) != 0L;
    }

    /** Forward cost of entering {@code destinationIdx}. */
    static float stepCost(int direction, int destinationIdx,
                          byte[] occupancy, float[] costField) {
        return stepCost(direction, destinationIdx, occupancy,
                costField == null ? 1f : costField[destinationIdx]);
    }

    /** The route multiplier never scales the additive occupancy penalty. */
    static float stepCost(int direction, int destinationIdx,
                          byte[] occupancy, float multiplier) {
        float cost = DIR_COST[direction] * multiplier;
        if (occupancy != null) {
            cost += OCCUPANCY_PENALTY * (occupancy[destinationIdx] & 0xFF);
        }
        return cost;
    }

    static boolean profilePathRequests() { return PROFILE_PATH_REQUESTS; }

    private static void heapSiftUp(int[] heap, int[] heapPos, float[] fCost, int pos) {
        int nodeIdx = heap[pos];
        float nodePriority = fCost[nodeIdx];
        while (pos > 0) {
            int parentPos = (pos - 1) >>> 1;
            int parentNodeIdx = heap[parentPos];
            if (nodePriority >= fCost[parentNodeIdx]) break;
            heap[pos] = parentNodeIdx;
            heapPos[parentNodeIdx] = pos;
            pos = parentPos;
        }
        heap[pos] = nodeIdx;
        heapPos[nodeIdx] = pos;
    }

    private static void heapSiftDown(int[] heap, int[] heapPos, float[] fCost, int pos, int heapSize) {
        int nodeIdx = heap[pos];
        float nodePriority = fCost[nodeIdx];
        int half = heapSize >>> 1;
        while (pos < half) {
            int childPos = (pos << 1) + 1;
            int childNodeIdx = heap[childPos];
            float childPriority = fCost[childNodeIdx];

            int rightPos = childPos + 1;
            if (rightPos < heapSize) {
                float rightPriority = fCost[heap[rightPos]];
                if (rightPriority < childPriority) {
                    childPos = rightPos;
                    childNodeIdx = heap[rightPos];
                    childPriority = rightPriority;
                }
            }

            if (nodePriority <= childPriority) break;
            heap[pos] = childNodeIdx;
            heapPos[childNodeIdx] = pos;
            pos = childPos;
        }
        heap[pos] = nodeIdx;
        heapPos[nodeIdx] = pos;
    }
}
