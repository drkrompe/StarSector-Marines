package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

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

    public static boolean USE_CARDINAL_NAVIGATION = false;

    /**
     * Per-occupant extra cost added when stepping into a cell. A penalty of 2 makes
     * an occupied cell effectively cost 3 to enter (1 for the move + 2 for occupancy),
     * so A* prefers detours up to ~2 cells longer over walking through an ally.
     */
    public static final float OCCUPANCY_PENALTY = 2f;

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

        void ensureHeapCapacity(int required) {
            if (heap.length < required) {
                heap = new int[Math.max(heap.length * 2, Math.max(required, 1024))];
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
     * Overload accepting a per-cell occupancy count — typically the unit count
     * standing on each cell, packed into a {@code byte[]} indexed by
     * {@link NavigationGrid#index(int, int)}. The pathfinder adds
     * {@link #OCCUPANCY_PENALTY} × {@code occupancy[idx]} to the cost of
     * stepping into each cell, so A* routes around stacked allies when a
     * reasonable detour exists. Pass {@code null} for vanilla pathing.
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
        long _profT0 = System.nanoTime();
        try {
            return findPathInner(grid, startX, startY, goalX, goalY, cardinalOnly, occupancy, costField, passable);
        } finally {
            TickInnerProfile p = TickInnerProfile.current();
            if (p != null) p.record(TickInnerProfile.Bucket.PATHFIND, System.nanoTime() - _profT0);
        }
    }

    private static int[] findPathInner(NavigationGrid grid, int startX, int startY, int goalX, int goalY,
                                        boolean cardinalOnly, byte[] occupancy, float[] costField, boolean[] passable) {
        if (!grid.isWalkable(startX, startY) || !grid.isWalkable(goalX, goalY)) {
            return EMPTY_PATH;
        }

        int w = grid.getWidth();
        int h = grid.getHeight();
        int totalCells = w * h;

        int startIdx = startY * w + startX;
        int goalIdx  = goalY  * w + goalX;

        // Clearance gate: a vehicle whose footprint can't sit on either endpoint
        // has no route. (Endpoint snapping for eroded perimeter cells is slice 2's job.)
        if (passable != null && (!passable[startIdx] || !passable[goalIdx])) {
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

        while (heapSize > 0) {
            int currentIdx = heap[0];
            heapSize--;
            if (heapSize > 0) {
                heap[0] = heap[heapSize];
                heapPos[heap[0]] = 0;
                heapSiftDown(heap, heapPos, fCost, 0, heapSize);
            }

            if (heapPos[currentIdx] == CLOSED) continue;
            heapPos[currentIdx] = CLOSED;

            if (currentIdx == goalIdx) {
                return reconstructPath(parentIdx, w, startIdx, goalIdx);
            }

            int cx = currentIdx % w;
            int cy = currentIdx / w;

            for (int dirI = 0; dirI < dirCount; dirI++) {
                int nx = cx + DIR_DX[dirI];
                int ny = cy + DIR_DY[dirI];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;

                int nIdx = ny * w + nx;

                if ((cellFlags[nIdx] & 1L) == 0L) continue;
                if (passable != null && !passable[nIdx]) continue;
                // Dual-side edge check: source cell's edge AND destination's reciprocal.
                if ((edgePass[currentIdx] & DIR_EDGE_MASK[dirI]) == 0) continue;
                if ((edgePass[nIdx]       & DIR_OPP_EDGE_MASK[dirI]) == 0) continue;

                if (DIR_IS_DIAGONAL[dirI]) {
                    // Both adjacent cardinal edges must be passable on both sides,
                    // and both adjacent cells walkable — prevents diagonal corner-cutting.
                    if ((edgePass[currentIdx] & DIR_CARD1_MASK[dirI]) == 0) continue;
                    if ((edgePass[currentIdx] & DIR_CARD2_MASK[dirI]) == 0) continue;
                    if ((edgePass[nIdx]       & DIR_OPP_CARD1_MASK[dirI]) == 0) continue;
                    if ((edgePass[nIdx]       & DIR_OPP_CARD2_MASK[dirI]) == 0) continue;
                    int a1x = cx + DIR_ADJ1_DX[dirI];
                    int a1y = cy + DIR_ADJ1_DY[dirI];
                    int a2x = cx + DIR_ADJ2_DX[dirI];
                    int a2y = cy + DIR_ADJ2_DY[dirI];
                    if (a1x < 0 || a1x >= w || a1y < 0 || a1y >= h) continue;
                    if (a2x < 0 || a2x >= w || a2y < 0 || a2y >= h) continue;
                    if ((cellFlags[a1y * w + a1x] & 1L) == 0L) continue;
                    if ((cellFlags[a2y * w + a2x] & 1L) == 0L) continue;
                }

                if (heapPos[nIdx] == CLOSED) continue;

                float stepCost = DIR_COST[dirI];
                if (costField != null) stepCost *= costField[nIdx];
                if (occupancy != null) {
                    int occCount = occupancy[nIdx] & 0xFF;
                    if (occCount > 0) stepCost += OCCUPANCY_PENALTY * occCount;
                }
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
    private static int[] reconstructPath(int[] parentIdx, int gridWidth, int startIdx, int goalIdx) {
        int cellCount = 0;
        int cursor = goalIdx;
        while (true) {
            cellCount++;
            if (cursor == startIdx) break;
            int parent = parentIdx[cursor];
            if (parent == cursor) break;
            cursor = parent;
        }
        int[] path = new int[cellCount * 2];
        cursor = goalIdx;
        int writeCell = cellCount - 1;
        while (writeCell >= 0) {
            path[writeCell * 2]     = cursor % gridWidth;
            path[writeCell * 2 + 1] = cursor / gridWidth;
            if (cursor == startIdx) break;
            int parent = parentIdx[cursor];
            if (parent == cursor) break;
            cursor = parent;
            writeCell--;
        }
        return path;
    }

    // ----- Heuristics and heap -----

    private static float heuristic(int x0, int y0, int x1, int y1, boolean cardinalOnly) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        if (cardinalOnly) return dx + dy; // Manhattan
        return Math.max(dx, dy) + (SQRT2 - 1) * Math.min(dx, dy); // Octile
    }

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
