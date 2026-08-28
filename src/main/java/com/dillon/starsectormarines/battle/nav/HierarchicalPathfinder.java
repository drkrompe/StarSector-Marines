package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;

/**
 * Coarse-to-fine pathfinder over a {@link GreedyNavigationMesh} snapshot.
 *
 * <p>A coarse A* walks directed region-boundary transitions, then ordinary
 * cell A* refines inside the chosen regions plus their immediate neighbors.
 * The refined path is accepted only when its measured cost is within a bounded
 * factor of the admissible unobstructed grid lower bound. Stale snapshots,
 * clearance conflicts, failed refinement, wide corridors, and excessive
 * detours all fall back to unrestricted {@link GridPathfinder}. Every returned
 * step is therefore validated by the authoritative grid even when hierarchy
 * supplied the corridor.
 */
public final class HierarchicalPathfinder {

    /** Accepted hierarchical routes are at most 25% above optimal cost. */
    static final float MAX_ACCEPTED_STRETCH = 1.25f;
    /** Skip hierarchy when padding leaves less than 15% of navigable cells out. */
    private static final int MAX_CORRIDOR_PERCENT = 85;
    private static final float SQRT2_MINUS_ONE =
            (float) Math.sqrt(2.0) - 1.0f;
    private static final float EPSILON = 1e-4f;
    private static final float INF = Float.MAX_VALUE;
    private static final int UNSEEN = -1;
    private static final int CLOSED = -2;

    private final NavigationGrid grid;
    private final GreedyNavigationMesh mesh;
    private final ThreadLocal<Workspace> workspaces =
            ThreadLocal.withInitial(Workspace::new);

    public HierarchicalPathfinder(NavigationGrid grid,
                                  GreedyNavigationMesh mesh) {
        this.grid = grid;
        this.mesh = mesh;
    }

    public int[] findPath(int startX, int startY, int goalX, int goalY,
                          boolean cardinalOnly, byte[] occupancy) {
        return findPath(startX, startY, goalX, goalY, cardinalOnly,
                occupancy, null, null);
    }

    public int[] findPath(int startX, int startY, int goalX, int goalY,
                          boolean cardinalOnly, byte[] occupancy,
                          float[] costField, boolean[] passable) {
        return findPathDetailed(startX, startY, goalX, goalY, cardinalOnly,
                occupancy, costField, passable).path();
    }

    /** Detailed result used by diagnostics and regression tests. */
    public SearchResult findPathDetailed(
            int startX, int startY, int goalX, int goalY,
            boolean cardinalOnly, byte[] occupancy,
            float[] costField, boolean[] passable) {
        long started = System.nanoTime();
        try {
            return findPathInner(startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        } finally {
            TickInnerProfile profile = TickInnerProfile.current();
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.PATHFIND,
                        System.nanoTime() - started);
                if (GridPathfinder.profilePathRequests()) {
                    profile.recordPathfindRequest(startX, startY,
                            goalX, goalY, occupancy != null);
                }
            }
        }
    }

    private SearchResult findPathInner(
            int startX, int startY, int goalX, int goalY,
            boolean cardinalOnly, byte[] occupancy,
            float[] costField, boolean[] passable) {
        GreedyNavigationMesh.Snapshot snapshot = mesh.snapshot();
        int startRegion = snapshot.regionIdAt(startX, startY);
        int goalRegion = snapshot.regionIdAt(goalX, goalY);
        if (startRegion < 0 || goalRegion < 0) {
            return fallback(SearchMode.ENDPOINT_OUTSIDE_SNAPSHOT,
                    snapshot.revision(), 0, 0, 0,
                    startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        }

        Workspace workspace = workspaces.get();
        int[] regionRoute = findRegionRoute(snapshot, startRegion, goalRegion,
                startX, startY, goalX, goalY, cardinalOnly, workspace);
        if (regionRoute.length == 0) {
            return fallback(SearchMode.NO_COARSE_ROUTE,
                    snapshot.revision(), 0, 0,
                    navigableCellCount(snapshot, passable),
                    startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        }

        workspace.ensureRegions(snapshot.regions().size());
        Arrays.fill(workspace.routeRegions, 0,
                snapshot.regions().size(), false);
        Arrays.fill(workspace.selectedRegions, 0,
                snapshot.regions().size(), false);
        for (int regionId : regionRoute) {
            workspace.routeRegions[regionId] = true;
            workspace.selectedRegions[regionId] = true;
        }
        // One-region padding gives refinement room to choose a neighboring
        // portal or occupancy detour without opening the whole component.
        for (GreedyNavigationMesh.Transition transition
                : snapshot.transitions()) {
            if (workspace.routeRegions[transition.regionA()]) {
                workspace.selectedRegions[transition.regionB()] = true;
            }
            if (workspace.routeRegions[transition.regionB()]) {
                workspace.selectedRegions[transition.regionA()] = true;
            }
        }

        int totalCells = grid.getWidth() * grid.getHeight();
        workspace.ensureCells(totalCells);
        int corridorCells = buildCorridorMask(snapshot, passable, workspace);
        int navigableCells = navigableCellCount(snapshot, passable);
        if (corridorCells == 0 || navigableCells == 0
                || corridorCells * 100 >= navigableCells
                * MAX_CORRIDOR_PERCENT) {
            return fallback(SearchMode.CORRIDOR_TOO_WIDE,
                    snapshot.revision(), regionRoute.length,
                    corridorCells, navigableCells,
                    startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        }

        int[] refined = GridPathfinder.findPathUnprofiled(grid,
                startX, startY, goalX, goalY, cardinalOnly, occupancy,
                costField, workspace.corridorPassable);
        if (Paths.isEmpty(refined)) {
            return fallback(SearchMode.REFINEMENT_FAILED,
                    snapshot.revision(), regionRoute.length,
                    corridorCells, navigableCells,
                    startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        }

        float lowerBound = lowerBound(startX, startY, goalX, goalY,
                cardinalOnly, costField, passable);
        float refinedCost = pathCost(refined, occupancy, costField);
        if (refinedCost > lowerBound * MAX_ACCEPTED_STRETCH + EPSILON) {
            return fallback(SearchMode.STRETCH_LIMIT,
                    snapshot.revision(), regionRoute.length,
                    corridorCells, navigableCells,
                    startX, startY, goalX, goalY, cardinalOnly,
                    occupancy, costField, passable);
        }
        return new SearchResult(refined, snapshot.revision(),
                regionRoute.length, corridorCells, navigableCells,
                true, false, SearchMode.CORRIDOR);
    }

    private SearchResult fallback(
            SearchMode mode, long revision, int regionCount, int corridorCells,
            int navigableCells,
            int startX, int startY, int goalX, int goalY,
            boolean cardinalOnly, byte[] occupancy,
            float[] costField, boolean[] passable) {
        int[] path = GridPathfinder.findPathUnprofiled(grid,
                startX, startY, goalX, goalY, cardinalOnly, occupancy,
                costField, passable);
        return new SearchResult(path, revision, regionCount,
                corridorCells, navigableCells, false, true, mode);
    }

    private int buildCorridorMask(GreedyNavigationMesh.Snapshot snapshot,
                                  boolean[] passable, Workspace workspace) {
        Arrays.fill(workspace.corridorPassable, false);
        int width = grid.getWidth();
        int count = 0;
        for (GreedyNavigationMesh.Region region : snapshot.regions()) {
            if (!workspace.selectedRegions[region.id()]) continue;
            for (int y = region.y(); y < region.maxYExclusive(); y++) {
                int row = y * width;
                for (int x = region.x(); x < region.maxXExclusive(); x++) {
                    int idx = row + x;
                    if (passable != null && !passable[idx]) continue;
                    workspace.corridorPassable[idx] = true;
                    count++;
                }
            }
        }
        return count;
    }

    private int navigableCellCount(GreedyNavigationMesh.Snapshot snapshot,
                                   boolean[] passable) {
        if (passable == null) {
            int count = 0;
            for (GreedyNavigationMesh.Region region : snapshot.regions()) {
                count += region.cellCount();
            }
            return count;
        }
        int width = grid.getWidth();
        int count = 0;
        for (GreedyNavigationMesh.Region region : snapshot.regions()) {
            for (int y = region.y(); y < region.maxYExclusive(); y++) {
                int row = y * width;
                for (int x = region.x(); x < region.maxXExclusive(); x++) {
                    if (passable[row + x]) count++;
                }
            }
        }
        return count;
    }

    private int[] findRegionRoute(
            GreedyNavigationMesh.Snapshot snapshot,
            int startRegion, int goalRegion,
            int startX, int startY, int goalX, int goalY,
            boolean cardinalOnly, Workspace workspace) {
        if (startRegion == goalRegion) return new int[]{startRegion};
        int transitionCount = snapshot.transitions().size();
        int stateCount = transitionCount * 2;
        if (stateCount == 0) return new int[0];
        workspace.ensureCoarseStates(stateCount);
        workspace.resetCoarse(stateCount);

        float startPointX = startX + 0.5f;
        float startPointY = startY + 0.5f;
        float goalPointX = goalX + 0.5f;
        float goalPointY = goalY + 0.5f;
        int incidentCount = snapshot.transitionCount(startRegion);
        for (int i = 0; i < incidentCount; i++) {
            int transitionId = snapshot.transitionIdAt(startRegion, i);
            GreedyNavigationMesh.Transition transition =
                    snapshot.transitions().get(transitionId);
            int enteredRegion = otherRegion(transition, startRegion);
            int state = stateEntering(transition, enteredRegion);
            float midX = midpointX(transition);
            float midY = midpointY(transition);
            float cost = metric(startPointX, startPointY, midX, midY,
                    cardinalOnly) + 1f;
            offerCoarse(workspace, state, cost,
                    cost + metric(midX, midY, goalPointX, goalPointY,
                            cardinalOnly), -1);
        }

        while (workspace.coarseHeapSize > 0) {
            int state = popCoarse(workspace);
            if (workspace.coarseHeapPos[state] == CLOSED) continue;
            workspace.coarseHeapPos[state] = CLOSED;
            int transitionId = state >>> 1;
            GreedyNavigationMesh.Transition enteredThrough =
                    snapshot.transitions().get(transitionId);
            int region = enteredRegion(enteredThrough, state);
            if (region == goalRegion) {
                return reconstructRegionRoute(snapshot, startRegion, state,
                        workspace.coarseParent);
            }

            float fromX = midpointX(enteredThrough);
            float fromY = midpointY(enteredThrough);
            int nextCount = snapshot.transitionCount(region);
            for (int i = 0; i < nextCount; i++) {
                int nextTransitionId = snapshot.transitionIdAt(region, i);
                if (nextTransitionId == transitionId) continue;
                GreedyNavigationMesh.Transition nextTransition =
                        snapshot.transitions().get(nextTransitionId);
                int nextRegion = otherRegion(nextTransition, region);
                int nextState = stateEntering(nextTransition, nextRegion);
                if (workspace.coarseHeapPos[nextState] == CLOSED) continue;
                float nextX = midpointX(nextTransition);
                float nextY = midpointY(nextTransition);
                float tentative = workspace.coarseG[state]
                        + metric(fromX, fromY, nextX, nextY, cardinalOnly)
                        + 1f;
                if (tentative >= workspace.coarseG[nextState]) continue;
                offerCoarse(workspace, nextState, tentative,
                        tentative + metric(nextX, nextY,
                                goalPointX, goalPointY, cardinalOnly), state);
            }
        }
        return new int[0];
    }

    private static int[] reconstructRegionRoute(
            GreedyNavigationMesh.Snapshot snapshot, int startRegion,
            int goalState, int[] parent) {
        int count = 1;
        for (int state = goalState; state >= 0; state = parent[state]) count++;
        int[] route = new int[count];
        route[0] = startRegion;
        int write = count - 1;
        for (int state = goalState; state >= 0; state = parent[state]) {
            GreedyNavigationMesh.Transition transition =
                    snapshot.transitions().get(state >>> 1);
            route[write--] = enteredRegion(transition, state);
        }
        return route;
    }

    private static int otherRegion(GreedyNavigationMesh.Transition transition,
                                   int region) {
        return transition.regionA() == region
                ? transition.regionB() : transition.regionA();
    }

    private static int stateEntering(
            GreedyNavigationMesh.Transition transition, int region) {
        return transition.id() * 2
                + (transition.regionA() == region ? 0 : 1);
    }

    private static int enteredRegion(
            GreedyNavigationMesh.Transition transition, int state) {
        return (state & 1) == 0
                ? transition.regionA() : transition.regionB();
    }

    private static float midpointX(
            GreedyNavigationMesh.Transition transition) {
        return transition.direction() == Direction.E
                ? transition.x() + 1f
                : transition.x() + transition.length() * 0.5f;
    }

    private static float midpointY(
            GreedyNavigationMesh.Transition transition) {
        return transition.direction() == Direction.E
                ? transition.y() + transition.length() * 0.5f
                : transition.y() + 1f;
    }

    private static float metric(float x0, float y0, float x1, float y1,
                                boolean cardinalOnly) {
        float dx = Math.abs(x1 - x0);
        float dy = Math.abs(y1 - y0);
        if (cardinalOnly) return dx + dy;
        return Math.max(dx, dy) + SQRT2_MINUS_ONE * Math.min(dx, dy);
    }

    private float lowerBound(int startX, int startY, int goalX, int goalY,
                             boolean cardinalOnly, float[] costField,
                             boolean[] passable) {
        float multiplier = 1f;
        if (costField != null) {
            multiplier = INF;
            for (int i = 0; i < costField.length; i++) {
                if (passable != null && !passable[i]) continue;
                float value = costField[i];
                if (Float.isFinite(value) && value > 0f) {
                    multiplier = Math.min(multiplier, value);
                }
            }
            if (multiplier == INF) multiplier = 1f;
        }
        return metric(startX, startY, goalX, goalY, cardinalOnly)
                * multiplier;
    }

    private float pathCost(int[] path, byte[] occupancy,
                           float[] costField) {
        float cost = 0f;
        int width = grid.getWidth();
        for (int cell = 1; cell < Paths.cellCount(path); cell++) {
            int dx = Math.abs(Paths.cellX(path, cell)
                    - Paths.cellX(path, cell - 1));
            int dy = Math.abs(Paths.cellY(path, cell)
                    - Paths.cellY(path, cell - 1));
            float step = dx != 0 && dy != 0
                    ? 1f + SQRT2_MINUS_ONE : 1f;
            int destination = Paths.cellY(path, cell) * width
                    + Paths.cellX(path, cell);
            if (costField != null) step *= costField[destination];
            if (occupancy != null) {
                step += GridPathfinder.OCCUPANCY_PENALTY
                        * (occupancy[destination] & 0xFF);
            }
            cost += step;
        }
        return cost;
    }

    private static void offerCoarse(Workspace workspace, int state,
                                    float g, float f, int parent) {
        workspace.coarseG[state] = g;
        workspace.coarseF[state] = f;
        workspace.coarseParent[state] = parent;
        int position = workspace.coarseHeapPos[state];
        if (position >= 0) {
            siftCoarseUp(workspace, position);
            return;
        }
        position = workspace.coarseHeapSize++;
        workspace.coarseHeap[position] = state;
        workspace.coarseHeapPos[state] = position;
        siftCoarseUp(workspace, position);
    }

    private static int popCoarse(Workspace workspace) {
        int result = workspace.coarseHeap[0];
        int size = --workspace.coarseHeapSize;
        if (size > 0) {
            int tail = workspace.coarseHeap[size];
            workspace.coarseHeap[0] = tail;
            workspace.coarseHeapPos[tail] = 0;
            siftCoarseDown(workspace, 0);
        }
        return result;
    }

    private static void siftCoarseUp(Workspace workspace, int position) {
        int state = workspace.coarseHeap[position];
        while (position > 0) {
            int parent = (position - 1) >>> 1;
            int parentState = workspace.coarseHeap[parent];
            if (!coarseLess(state, parentState, workspace.coarseF)) break;
            workspace.coarseHeap[position] = parentState;
            workspace.coarseHeapPos[parentState] = position;
            position = parent;
        }
        workspace.coarseHeap[position] = state;
        workspace.coarseHeapPos[state] = position;
    }

    private static void siftCoarseDown(Workspace workspace, int position) {
        int state = workspace.coarseHeap[position];
        int half = workspace.coarseHeapSize >>> 1;
        while (position < half) {
            int child = (position << 1) + 1;
            int best = workspace.coarseHeap[child];
            if (child + 1 < workspace.coarseHeapSize
                    && coarseLess(workspace.coarseHeap[child + 1], best,
                    workspace.coarseF)) {
                child++;
                best = workspace.coarseHeap[child];
            }
            if (!coarseLess(best, state, workspace.coarseF)) break;
            workspace.coarseHeap[position] = best;
            workspace.coarseHeapPos[best] = position;
            position = child;
        }
        workspace.coarseHeap[position] = state;
        workspace.coarseHeapPos[state] = position;
    }

    private static boolean coarseLess(int left, int right, float[] priority) {
        int compare = Float.compare(priority[left], priority[right]);
        return compare < 0 || compare == 0 && left < right;
    }

    public record SearchResult(int[] path, long meshRevision,
                               int coarseRegionCount, int corridorCellCount,
                               int navigableCellCount, boolean usedCorridor,
                               boolean fellBack, SearchMode mode) {}

    public enum SearchMode {
        CORRIDOR,
        ENDPOINT_OUTSIDE_SNAPSHOT,
        NO_COARSE_ROUTE,
        CORRIDOR_TOO_WIDE,
        REFINEMENT_FAILED,
        STRETCH_LIMIT
    }

    private static final class Workspace {
        boolean[] corridorPassable = new boolean[0];
        boolean[] routeRegions = new boolean[0];
        boolean[] selectedRegions = new boolean[0];
        float[] coarseG = new float[0];
        float[] coarseF = new float[0];
        int[] coarseParent = new int[0];
        int[] coarseHeapPos = new int[0];
        int[] coarseHeap = new int[0];
        int coarseHeapSize;

        void ensureCells(int count) {
            if (corridorPassable.length < count) {
                corridorPassable = new boolean[count];
            }
        }

        void ensureRegions(int count) {
            if (routeRegions.length < count) {
                routeRegions = new boolean[count];
                selectedRegions = new boolean[count];
            }
        }

        void ensureCoarseStates(int count) {
            if (coarseG.length < count) {
                coarseG = new float[count];
                coarseF = new float[count];
                coarseParent = new int[count];
                coarseHeapPos = new int[count];
                coarseHeap = new int[count];
            }
        }

        void resetCoarse(int count) {
            Arrays.fill(coarseG, 0, count, INF);
            Arrays.fill(coarseF, 0, count, INF);
            Arrays.fill(coarseParent, 0, count, -1);
            Arrays.fill(coarseHeapPos, 0, count, UNSEEN);
            coarseHeapSize = 0;
        }
    }
}
