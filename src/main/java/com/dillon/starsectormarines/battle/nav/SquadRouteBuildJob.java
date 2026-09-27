package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;

/**
 * One serial, resumable preparation slot. Map-sized primitive scratch is owned
 * by the slot, not allocated for each squad. Each work unit visits one search
 * node, route cell, mesh boundary, corridor cell, or publication entry. Heap
 * operations have logarithmic cost; output-array allocation is not preemptible.
 * Only already-current connected-component labels may reject a seed: a lazy
 * full-map rebuild would bypass this budget. Topology must remain unchanged
 * between advances.
 */
final class SquadRouteBuildJob {
    private enum Stage { START, SEED, TRACE, PAD, CORRIDOR, REQUIRED, REVERSE,
        SORT_HEAP, SORT, PUBLISH, DONE }
    private static final int CLOSED = -2;
    private final NavigationGrid grid;
    private final int width, height, size;
    private final int[] seen, allowed, required, next, positions, heap, settled;
    private final float[] distance, priority;
    private int[] routeRegions = new int[0], selectedRegions = new int[0];
    private int generation, jobGeneration, heapSize, settledCount;
    private GreedyNavigationMesh.Snapshot mesh;
    private RouteCostField cost;
    private int[] starts;
    private int goal, startCursor, traceCursor, traceSuccessor, padRegion, padCursor;
    private int regionCursor, regionCellCursor, requiredCursor, remaining;
    private int sortCursor, sortEnd, publishCursor;
    private int[] publishedCells, publishedNext;
    private boolean direct, hasRoute, invalidated;
    private long topologyRevision, workUnits;
    private int seedSearches, seedExpanded, seedPathCells, unpaddedCells, corridorCells;
    private int reverseExpanded, maxStartGoalManhattan, lastSeedStart, lastSeedExpanded, maxSeedExpanded;
    private Stage stage = Stage.DONE;
    private SquadRouteField field;

    SquadRouteBuildJob(NavigationGrid grid) {
        this.grid = grid;
        width = grid.getWidth();
        height = grid.getHeight();
        size = width * height;
        seen = new int[size]; allowed = new int[size]; required = new int[size];
        next = new int[size]; positions = new int[size]; heap = new int[size];
        settled = new int[size]; distance = new float[size]; priority = new float[size];
    }

    void begin(SquadRouteRequest request, GreedyNavigationMesh.Snapshot mesh,
               boolean retainSingleton) {
        if (request.cost() != null && request.cost().size() != size) {
            throw new IllegalArgumentException("field dimensions must match navigation grid");
        }
        this.mesh = mesh;
        cost = request.cost();
        starts = request.startCells();
        topologyRevision = grid.topologyRevision();
        if (++jobGeneration == 0) {
            Arrays.fill(allowed, 0); Arrays.fill(required, 0);
            Arrays.fill(routeRegions, 0); Arrays.fill(selectedRegions, 0);
            jobGeneration = 1;
        }
        if (routeRegions.length < mesh.regions().size()) {
            routeRegions = new int[mesh.regions().size()];
            selectedRegions = new int[mesh.regions().size()];
        }
        goal = grid.inBounds(request.goalX(), request.goalY())
                ? grid.index(request.goalX(), request.goalY()) : -1;
        direct = retainSingleton && starts.length == 1;
        hasRoute = invalidated = false;
        field = null;
        publishedCells = publishedNext = null;
        startCursor = heapSize = settledCount = 0;
        seedSearches = seedExpanded = seedPathCells = unpaddedCells = corridorCells = 0;
        reverseExpanded = maxStartGoalManhattan = lastSeedExpanded = maxSeedExpanded = 0;
        lastSeedStart = -1;
        workUnits = 0;
        stage = goal < 0 || !grid.isWalkable(request.goalX(), request.goalY())
                || starts.length == 0 || mesh.regionIdAt(request.goalX(), request.goalY()) < 0
                ? Stage.DONE : Stage.START;
    }

    /** Returns exactly the charged work consumed, never more than the supplied budget. */
    int advance(int workBudget) {
        if (workBudget < 0) throw new IllegalArgumentException("Negative work budget");
        if (isDone() || workBudget == 0) return 0;
        if (grid.topologyRevision() != topologyRevision) {
            invalidated = true;
            stage = Stage.DONE;
            return 0;
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        int searchesBefore = seedSearches;
        int expandedBefore = seedExpanded;
        int pathCellsBefore = seedPathCells;
        int used = 0;
        while (used < workBudget && !isDone()) {
            TickInnerProfile.Bucket bucket = bucket();
            long started = profile == null ? 0 : System.nanoTime();
            do {
                step();
                used++;
            } while (used < workBudget && !isDone() && bucket() == bucket);
            if (profile != null) profile.record(bucket, System.nanoTime() - started);
        }
        workUnits += used;
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_SEARCH,
                    seedSearches - searchesBefore);
            profile.recordCount(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_EXPANDED,
                    seedExpanded - expandedBefore);
            profile.recordCount(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_PATH_CELL,
                    seedPathCells - pathCellsBefore);
        }
        return used;
    }

    private TickInnerProfile.Bucket bucket() {
        return switch (stage) {
            case START, SEED, TRACE, PAD -> TickInnerProfile.Bucket.SQUAD_PATH_FIELD_SEED;
            case CORRIDOR, REQUIRED -> TickInnerProfile.Bucket.SQUAD_PATH_FIELD_CORRIDOR;
            default -> direct ? TickInnerProfile.Bucket.SQUAD_PATH_FIELD_DIRECT
                    : TickInnerProfile.Bucket.SQUAD_PATH_FIELD_REVERSE;
        };
    }

    private void step() {
        switch (stage) {
            case START -> startSeed();
            case SEED -> expandSeed();
            case TRACE -> traceSeed();
            case PAD -> padRegion();
            case CORRIDOR -> addCorridorCell();
            case REQUIRED -> addRequiredStart();
            case REVERSE -> expandReverse();
            case SORT_HEAP -> {
                if (sortCursor >= 0) siftSettledDown(sortCursor--, settledCount);
                else stage = Stage.SORT;
            }
            case SORT -> {
                if (sortEnd > 0) {
                    int swap = settled[0]; settled[0] = settled[sortEnd]; settled[sortEnd--] = swap;
                    siftSettledDown(0, sortEnd + 1);
                } else {
                    publishedCells = new int[settledCount];
                    publishedNext = new int[settledCount];
                    publishCursor = 0;
                    stage = Stage.PUBLISH;
                }
            }
            case PUBLISH -> {
                if (publishCursor < settledCount) {
                    int cell = settled[publishCursor];
                    publishedCells[publishCursor] = cell;
                    publishedNext[publishCursor++] = next[cell];
                } else {
                    field = SquadRouteField.takePublished(width, height, goal, corridorCells,
                            publishedCells, publishedNext);
                    publishedCells = publishedNext = null;
                    stage = Stage.DONE;
                }
            }
            case DONE -> { }
        }
    }

    private void startSeed() {
        if (startCursor == starts.length) {
            if (!hasRoute) { stage = Stage.DONE; return; }
            regionCursor = regionCellCursor = 0;
            stage = Stage.CORRIDOR;
            return;
        }
        int start = starts[startCursor++];
        if (start < 0 || start >= size) return;
        int x = start % width, y = start / width;
        maxStartGoalManhattan = Math.max(maxStartGoalManhattan,
                Math.abs(x - goal % width) + Math.abs(y - goal / width));
        int region = mesh.regionIdAt(x, y);
        if (region < 0 || (grid.getCellFlagsArray()[start] & 1L) == 0
                || hasRoute && selectedRegions[region] == jobGeneration) return;
        seedSearches++;
        lastSeedStart = start;
        lastSeedExpanded = 0;
        if (grid.knownPathDisconnected(start, goal, GridPathfinder.USE_CARDINAL_NAVIGATION)) return;
        resetSearch();
        discover(start, 0, heuristic(start), start);
        stage = Stage.SEED;
    }

    private void expandSeed() {
        if (heapSize == 0) { finishSeed(); stage = Stage.START; return; }
        int current = pop();
        positions[current] = CLOSED;
        seedExpanded++;
        lastSeedExpanded++;
        if (current == goal) {
            finishSeed();
            hasRoute = true;
            traceCursor = goal;
            traceSuccessor = goal;
            stage = Stage.TRACE;
            return;
        }
        int x = current % width, y = current / width;
        for (int direction = 0; direction < GridPathfinder.directionCount(
                GridPathfinder.USE_CARDINAL_NAVIGATION); direction++) {
            int nx = x + GridPathfinder.directionX(direction);
            int ny = y + GridPathfinder.directionY(direction);
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
            int neighbor = ny * width + nx;
            if (seen[neighbor] == generation && positions[neighbor] == CLOSED) continue;
            if (!GridPathfinder.canStep(current, x, y, neighbor, direction, width, height,
                    grid.getCellFlagsArray(), grid.getEdgePassabilityArray(), null)) continue;
            float candidate = distance[current] + GridPathfinder.stepCost(direction, neighbor,
                    null, cost == null ? 1f : cost.costAt(neighbor, nx, ny));
            if (seen[neighbor] != generation || candidate < distance[neighbor]) {
                discover(neighbor, candidate, candidate + heuristic(neighbor), current);
            }
        }
    }

    private void finishSeed() { maxSeedExpanded = Math.max(maxSeedExpanded, lastSeedExpanded); }

    private void traceSeed() {
        if (traceCursor < 0) {
            if (direct) { corridorCells = settledCount; beginPublish(); }
            else stage = Stage.START;
            return;
        }
        int cell = traceCursor;
        int parent = next[cell];
        next[cell] = traceSuccessor;
        traceSuccessor = cell;
        traceCursor = cell == lastSeedStart ? -1 : parent;
        seedPathCells++;
        if (direct) { settled[settledCount++] = cell; return; }
        int region = mesh.regionIdAt(cell % width, cell / width);
        if (region >= 0 && routeRegions[region] != jobGeneration) {
            routeRegions[region] = selectedRegions[region] = jobGeneration;
            unpaddedCells += mesh.regions().get(region).cellCount();
            padRegion = region;
            padCursor = 0;
            stage = Stage.PAD;
        }
    }

    private void padRegion() {
        if (padCursor == mesh.transitionCount(padRegion)) { stage = Stage.TRACE; return; }
        GreedyNavigationMesh.Transition transition = mesh.transitions().get(
                mesh.transitionIdAt(padRegion, padCursor++));
        selectedRegions[transition.regionA()] = jobGeneration;
        selectedRegions[transition.regionB()] = jobGeneration;
    }

    private void addCorridorCell() {
        if (regionCursor == mesh.regions().size()) {
            resetSearch();
            requiredCursor = remaining = 0;
            stage = Stage.REQUIRED;
            return;
        }
        GreedyNavigationMesh.Region region = mesh.regions().get(regionCursor);
        if (selectedRegions[region.id()] != jobGeneration
                || regionCellCursor == region.cellCount()) {
            regionCursor++;
            regionCellCursor = 0;
            return;
        }
        int cell = (region.y() + regionCellCursor / region.width()) * width
                + region.x() + regionCellCursor % region.width();
        regionCellCursor++;
        if ((grid.getCellFlagsArray()[cell] & 1L) != 0) {
            allowed[cell] = jobGeneration;
            corridorCells++;
        }
    }

    private void addRequiredStart() {
        if (requiredCursor == starts.length) {
            discover(goal, 0, 0, goal);
            stage = Stage.REVERSE;
            return;
        }
        int start = starts[requiredCursor++];
        if (start >= 0 && start < size && allowed[start] == jobGeneration
                && required[start] != jobGeneration) {
            required[start] = jobGeneration;
            remaining++;
        }
    }

    private void expandReverse() {
        if (heapSize == 0) { beginPublish(); return; }
        int current = pop();
        positions[current] = CLOSED;
        settled[settledCount++] = current;
        reverseExpanded++;
        if (required[current] == jobGeneration) remaining--;
        if (remaining == 0) { beginPublish(); return; }
        int x = current % width, y = current / width;
        float multiplier = cost == null ? 1f : cost.costAt(current, x, y);
        for (int direction = 0; direction < GridPathfinder.directionCount(
                GridPathfinder.USE_CARDINAL_NAVIGATION); direction++) {
            int px = x - GridPathfinder.directionX(direction);
            int py = y - GridPathfinder.directionY(direction);
            if (px < 0 || px >= width || py < 0 || py >= height) continue;
            int predecessor = py * width + px;
            if (allowed[predecessor] != jobGeneration
                    || seen[predecessor] == generation && positions[predecessor] == CLOSED) continue;
            if (!GridPathfinder.canStep(predecessor, px, py, current, direction, width, height,
                    grid.getCellFlagsArray(), grid.getEdgePassabilityArray(), null)) continue;
            float candidate = distance[current] + GridPathfinder.stepCost(
                    direction, current, null, multiplier);
            if (seen[predecessor] != generation || candidate < distance[predecessor]) {
                discover(predecessor, candidate, candidate, current);
            }
        }
    }

    private void beginPublish() {
        sortCursor = (settledCount >>> 1) - 1;
        sortEnd = settledCount - 1;
        stage = Stage.SORT_HEAP;
    }

    private void siftSettledDown(int position, int count) {
        int value = settled[position];
        while (position < count / 2) {
            int left = position * 2 + 1, right = left + 1;
            int best = right < count && settled[right] > settled[left] ? right : left;
            if (value >= settled[best]) break;
            settled[position] = settled[best]; position = best;
        }
        settled[position] = value;
    }

    private void resetSearch() {
        if (++generation == 0) { Arrays.fill(seen, 0); generation = 1; }
        heapSize = 0;
    }

    private float heuristic(int cell) {
        int dx = Math.abs(cell % width - goal % width);
        int dy = Math.abs(cell / width - goal / width);
        return GridPathfinder.USE_CARDINAL_NAVIGATION ? dx + dy
                : Math.max(dx, dy) + ((float) Math.sqrt(2) - 1) * Math.min(dx, dy);
    }

    private void discover(int cell, float value, float score, int successor) {
        boolean known = seen[cell] == generation;
        seen[cell] = generation; distance[cell] = value; priority[cell] = score;
        next[cell] = successor;
        int position = known ? positions[cell] : heapSize++;
        while (position > 0) {
            int parent = (position - 1) >>> 1;
            if (score >= priority[heap[parent]]) break;
            heap[position] = heap[parent]; positions[heap[position]] = position; position = parent;
        }
        heap[position] = cell; positions[cell] = position;
    }

    private int pop() {
        int result = heap[0], cell = heap[--heapSize], position = 0;
        while (position < heapSize / 2) {
            int left = position * 2 + 1, right = left + 1;
            int best = right < heapSize && priority[heap[right]] < priority[heap[left]] ? right : left;
            if (priority[cell] <= priority[heap[best]]) break;
            heap[position] = heap[best]; positions[heap[position]] = position; position = best;
        }
        if (heapSize > 0) { heap[position] = cell; positions[cell] = position; }
        return result;
    }

    boolean isDone() { return stage == Stage.DONE; }
    /** Retires an interrupted or published request without discarding reusable primitive scratch. */
    void release() {
        mesh = null;
        cost = null;
        starts = null;
        field = null;
        publishedCells = publishedNext = null;
        stage = Stage.DONE;
    }
    boolean invalidated() { return invalidated; }
    SquadRouteField field() { return field; }
    String stage() { return stage.name(); }
    long workUnits() { return workUnits; }
    int seedSearches() { return seedSearches; }
    int seedExpanded() { return seedExpanded; }
    int seedPathCells() { return seedPathCells; }
    int unpaddedCells() { return unpaddedCells; }
    int corridorCells() { return corridorCells; }
    int reverseExpanded() { return reverseExpanded; }
    int maxStartGoalManhattan() { return maxStartGoalManhattan; }
    int startCount() { return starts == null ? 0 : starts.length; }
    boolean isDirect() { return direct; }
    int lastSeedStart() { return lastSeedStart; }
    int lastSeedExpanded() { return lastSeedExpanded; }
    int maxSeedExpanded() { return Math.max(maxSeedExpanded, lastSeedExpanded); }
}
