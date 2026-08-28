package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bounded-cadence reverse shortest-path fields for many movers sharing one
 * goal. The owner must call {@link #beginSnapshot()} after freezing occupancy
 * and before any concurrent requests. Fields are immutable for the remainder
 * of that snapshot and may retain that frozen occupancy view across a small,
 * fixed number of later snapshots before rebuilding.
 */
final class SharedGoalPathfinder {

    private static final float INF = Float.MAX_VALUE;
    private static final int UNSEEN = -1;
    private static final int CLOSED = -2;
    private static final int MAX_RETAINED_FIELDS = 32;
    static final int DEFAULT_MAX_BUILD_AGE_SNAPSHOTS = 15;

    private final NavigationGrid grid;
    private final byte[] occupancy;
    private final HierarchicalPathfinder ordinaryPathfinder;
    private final int maxBuildAgeSnapshots;
    private final ConcurrentHashMap<Long, ReverseField> fields =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ReverseField> recycled =
            new ConcurrentLinkedQueue<>();
    private volatile boolean snapshotReady;
    private long snapshotIndex;

    SharedGoalPathfinder(NavigationGrid grid, byte[] occupancy) {
        this(grid, occupancy, DEFAULT_MAX_BUILD_AGE_SNAPSHOTS, null);
    }

    SharedGoalPathfinder(NavigationGrid grid, byte[] occupancy,
                         int maxBuildAgeSnapshots) {
        this(grid, occupancy, maxBuildAgeSnapshots, null);
    }

    SharedGoalPathfinder(NavigationGrid grid, byte[] occupancy,
                         HierarchicalPathfinder ordinaryPathfinder) {
        this(grid, occupancy, DEFAULT_MAX_BUILD_AGE_SNAPSHOTS,
                ordinaryPathfinder);
    }

    private SharedGoalPathfinder(NavigationGrid grid, byte[] occupancy,
                                 int maxBuildAgeSnapshots,
                                 HierarchicalPathfinder ordinaryPathfinder) {
        if (maxBuildAgeSnapshots < 1) {
            throw new IllegalArgumentException(
                    "maxBuildAgeSnapshots must be positive");
        }
        this.grid = grid;
        this.occupancy = occupancy;
        this.ordinaryPathfinder = ordinaryPathfinder;
        this.maxBuildAgeSnapshots = maxBuildAgeSnapshots;
    }

    /**
     * Serial tick boundary: expire fields whose frozen occupancy view reached
     * its fixed age, then publish the retained immutable fields to workers.
     */
    void beginSnapshot() {
        snapshotReady = false;
        snapshotIndex++;
        expireOldFields();
        trimRetainedFields();
        snapshotReady = true;
    }

    void endSnapshot() {
        snapshotReady = false;
        trimRetainedFields();
    }

    /**
     * Serial topology boundary: drop every retained tree so a newly opened
     * cell is visible to the next shared request immediately.
     */
    void invalidateAll() {
        if (snapshotReady) {
            throw new IllegalStateException(
                    "cannot invalidate shared fields during a live snapshot");
        }
        for (var entry : fields.entrySet()) {
            ReverseField field = entry.getValue();
            if (fields.remove(entry.getKey(), field)) recycle(field);
        }
    }

    int retainedFieldCountForTest() {
        return fields.size();
    }

    int[] findPath(int startX, int startY, int goalX, int goalY,
                   boolean cardinalOnly) {
        if (!snapshotReady) {
            if (ordinaryPathfinder != null) {
                return ordinaryPathfinder.findPath(startX, startY,
                        goalX, goalY, cardinalOnly, occupancy);
            }
            return GridPathfinder.findPath(grid, startX, startY,
                    goalX, goalY, cardinalOnly, occupancy);
        }
        long requestStart = System.nanoTime();
        try {
            if (!grid.isWalkable(startX, startY)
                    || !grid.isWalkable(goalX, goalY)) {
                return GridPathfinder.EMPTY_PATH;
            }
            if (startX == goalX && startY == goalY) {
                return new int[]{startX, startY};
            }
            int goalIdx = grid.index(goalX, goalY);
            long key = ((long) goalIdx << 1) | (cardinalOnly ? 1L : 0L);
            ReverseField field = fields.computeIfAbsent(key,
                    ignored -> buildField(
                            goalIdx, cardinalOnly, snapshotIndex));
            long extractStart = System.nanoTime();
            int[] path = field.extract(startX, startY);
            TickInnerProfile profile = TickInnerProfile.current();
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.SHARED_PATH_FIELD_EXTRACT,
                        System.nanoTime() - extractStart);
            }
            return path;
        } finally {
            TickInnerProfile profile = TickInnerProfile.current();
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.PATHFIND,
                        System.nanoTime() - requestStart);
                if (GridPathfinder.profilePathRequests()) {
                    profile.recordPathfindRequest(startX, startY,
                            goalX, goalY, true);
                }
            }
        }
    }

    private ReverseField buildField(int goalIdx, boolean cardinalOnly,
                                    long builtSnapshot) {
        ReverseField field = recycled.poll();
        if (field == null) {
            field = new ReverseField(grid.getWidth(), grid.getHeight());
        }
        long buildStart = System.nanoTime();
        field.rebuild(grid, occupancy, goalIdx, cardinalOnly);
        field.builtSnapshot = builtSnapshot;
        TickInnerProfile profile = TickInnerProfile.current();
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.SHARED_PATH_FIELD_BUILD,
                    System.nanoTime() - buildStart);
        }
        return field;
    }

    private void expireOldFields() {
        for (var entry : fields.entrySet()) {
            ReverseField field = entry.getValue();
            if (snapshotIndex - field.builtSnapshot
                    < maxBuildAgeSnapshots) {
                continue;
            }
            if (fields.remove(entry.getKey(), field)) recycle(field);
        }
    }

    /** Keeps the live cache bounded after every serial worker boundary. */
    private void trimRetainedFields() {
        while (fields.size() > MAX_RETAINED_FIELDS) {
            Long victimKey = null;
            ReverseField victim = null;
            for (var entry : fields.entrySet()) {
                ReverseField candidate = entry.getValue();
                if (victim == null
                        || candidate.builtSnapshot < victim.builtSnapshot
                        || (candidate.builtSnapshot == victim.builtSnapshot
                        && entry.getKey() < victimKey)) {
                    victimKey = entry.getKey();
                    victim = candidate;
                }
            }
            if (victim == null
                    || !fields.remove(victimKey, victim)) {
                break;
            }
            recycle(victim);
        }
    }

    /**
     * Reuse an evicted workspace when doing so stays inside the same 32-field
     * retained-memory budget as the live cache.
     */
    private void recycle(ReverseField field) {
        if (fields.size() + recycled.size() < MAX_RETAINED_FIELDS) {
            recycled.offer(field);
        }
    }

    private static final class ReverseField {
        private final int width;
        private final int height;
        private final int totalCells;
        private final float[] distance;
        private final int[] nextIdx;
        private final int[] heapPos;
        private final int[] heap;
        private int goalIdx;
        private long builtSnapshot;

        ReverseField(int width, int height) {
            this.width = width;
            this.height = height;
            this.totalCells = width * height;
            this.distance = new float[totalCells];
            this.nextIdx = new int[totalCells];
            this.heapPos = new int[totalCells];
            this.heap = new int[totalCells];
        }

        void rebuild(NavigationGrid grid, byte[] occupancy,
                     int newGoalIdx, boolean cardinalOnly) {
            Arrays.fill(distance, INF);
            Arrays.fill(nextIdx, UNSEEN);
            Arrays.fill(heapPos, UNSEEN);
            goalIdx = newGoalIdx;
            distance[goalIdx] = 0f;
            nextIdx[goalIdx] = goalIdx;
            heap[0] = goalIdx;
            heapPos[goalIdx] = 0;
            int heapSize = 1;
            int directionCount = GridPathfinder.directionCount(cardinalOnly);
            long[] cellFlags = grid.getCellFlagsArray();
            byte[] edgePassability = grid.getEdgePassabilityArray();

            while (heapSize > 0) {
                int currentIdx = heap[0];
                heapSize--;
                if (heapSize > 0) {
                    heap[0] = heap[heapSize];
                    heapPos[heap[0]] = 0;
                    siftDown(heap, heapPos, distance, 0, heapSize);
                }
                if (heapPos[currentIdx] == CLOSED) continue;
                heapPos[currentIdx] = CLOSED;

                int currentX = currentIdx % width;
                int currentY = currentIdx / width;
                // Every predecessor enters the same current cell. Occupancy is
                // destination-based, while base cost differs only between the
                // cardinal and diagonal direction families, so compute these
                // two bit-identical candidates once per expansion rather than
                // repeating the occupancy work for all 4/8 predecessors.
                float currentDistance = distance[currentIdx];
                float cardinalCandidate = currentDistance
                        + GridPathfinder.stepCost(
                        GridPathfinder.FIRST_CARDINAL_DIRECTION, currentIdx,
                        occupancy, null);
                float diagonalCandidate = cardinalOnly ? 0f
                        : currentDistance + GridPathfinder.stepCost(
                        GridPathfinder.FIRST_DIAGONAL_DIRECTION, currentIdx,
                        occupancy, null);
                for (int direction = 0;
                     direction < directionCount; direction++) {
                    int predecessorX = currentX
                            - GridPathfinder.directionX(direction);
                    int predecessorY = currentY
                            - GridPathfinder.directionY(direction);
                    if (predecessorX < 0 || predecessorX >= width
                            || predecessorY < 0 || predecessorY >= height) {
                        continue;
                    }
                    int predecessorIdx = predecessorY * width + predecessorX;
                    if ((cellFlags[predecessorIdx] & 1L) == 0L) continue;
                    if (!GridPathfinder.canStep(predecessorIdx,
                            predecessorX, predecessorY, currentIdx, direction,
                            width, height, cellFlags, edgePassability, null)) {
                        continue;
                    }
                    if (heapPos[predecessorIdx] == CLOSED) continue;

                    float candidate = direction
                            < GridPathfinder.FIRST_DIAGONAL_DIRECTION
                            ? cardinalCandidate : diagonalCandidate;
                    if (candidate >= distance[predecessorIdx]) continue;
                    distance[predecessorIdx] = candidate;
                    nextIdx[predecessorIdx] = currentIdx;
                    int position = heapPos[predecessorIdx];
                    if (position >= 0) {
                        siftUp(heap, heapPos, distance, position);
                    } else {
                        heap[heapSize] = predecessorIdx;
                        heapPos[predecessorIdx] = heapSize;
                        siftUp(heap, heapPos, distance, heapSize);
                        heapSize++;
                    }
                }
            }
        }

        int[] extract(int startX, int startY) {
            int startIdx = startY * width + startX;
            if (nextIdx[startIdx] == UNSEEN) return GridPathfinder.EMPTY_PATH;
            int cellCount = 1;
            int cursor = startIdx;
            while (cursor != goalIdx && cellCount <= totalCells) {
                cursor = nextIdx[cursor];
                if (cursor < 0 || cursor >= totalCells) {
                    return GridPathfinder.EMPTY_PATH;
                }
                cellCount++;
            }
            if (cursor != goalIdx) return GridPathfinder.EMPTY_PATH;

            int[] path = new int[cellCount * 2];
            cursor = startIdx;
            for (int cell = 0; cell < cellCount; cell++) {
                path[cell * 2] = cursor % width;
                path[cell * 2 + 1] = cursor / width;
                if (cursor != goalIdx) cursor = nextIdx[cursor];
            }
            return path;
        }
    }

    private static void siftUp(int[] heap, int[] heapPos,
                               float[] priority, int position) {
        int node = heap[position];
        float nodePriority = priority[node];
        while (position > 0) {
            int parentPosition = (position - 1) >>> 1;
            int parent = heap[parentPosition];
            if (nodePriority >= priority[parent]) break;
            heap[position] = parent;
            heapPos[parent] = position;
            position = parentPosition;
        }
        heap[position] = node;
        heapPos[node] = position;
    }

    private static void siftDown(int[] heap, int[] heapPos,
                                 float[] priority, int position,
                                 int heapSize) {
        int node = heap[position];
        float nodePriority = priority[node];
        int half = heapSize >>> 1;
        while (position < half) {
            int left = (position << 1) + 1;
            int right = left + 1;
            int best = left;
            if (right < heapSize
                    && priority[heap[right]] < priority[heap[left]]) {
                best = right;
            }
            if (nodePriority <= priority[heap[best]]) break;
            heap[position] = heap[best];
            heapPos[heap[position]] = position;
            position = best;
        }
        heap[position] = node;
        heapPos[node] = position;
    }
}
