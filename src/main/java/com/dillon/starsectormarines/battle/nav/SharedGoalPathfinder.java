package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tick-scoped reverse shortest-path fields for many movers sharing one goal.
 * The owner must call {@link #beginSnapshot()} after freezing occupancy and
 * before any concurrent requests. Fields are immutable for the remainder of
 * that snapshot and recycled at the next boundary.
 */
final class SharedGoalPathfinder {

    private static final float INF = Float.MAX_VALUE;
    private static final int UNSEEN = -1;
    private static final int CLOSED = -2;
    private static final int MAX_RETAINED_FIELDS = 32;

    private final NavigationGrid grid;
    private final byte[] occupancy;
    private final ConcurrentHashMap<Long, ReverseField> fields =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ReverseField> recycled =
            new ConcurrentLinkedQueue<>();
    private volatile boolean snapshotReady;

    SharedGoalPathfinder(NavigationGrid grid, byte[] occupancy) {
        this.grid = grid;
        this.occupancy = occupancy;
    }

    /** Serial tick boundary: retire the old immutable fields for reuse. */
    void beginSnapshot() {
        snapshotReady = false;
        int retained = recycled.size();
        for (ReverseField field : fields.values()) {
            if (retained >= MAX_RETAINED_FIELDS) break;
            recycled.offer(field);
            retained++;
        }
        fields.clear();
        snapshotReady = true;
    }

    void endSnapshot() {
        snapshotReady = false;
    }

    int[] findPath(int startX, int startY, int goalX, int goalY,
                   boolean cardinalOnly) {
        if (!snapshotReady) {
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
                    ignored -> buildField(goalIdx, cardinalOnly));
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

    private ReverseField buildField(int goalIdx, boolean cardinalOnly) {
        ReverseField field = recycled.poll();
        if (field == null) {
            field = new ReverseField(grid.getWidth(), grid.getHeight());
        }
        long buildStart = System.nanoTime();
        field.rebuild(grid, occupancy, goalIdx, cardinalOnly);
        TickInnerProfile profile = TickInnerProfile.current();
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.SHARED_PATH_FIELD_BUILD,
                    System.nanoTime() - buildStart);
        }
        return field;
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
