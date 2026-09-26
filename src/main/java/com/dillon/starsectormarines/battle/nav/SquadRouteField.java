package com.dillon.starsectormarines.battle.nav;

import java.util.Arrays;

/** Immutable, partially settled reverse route for one squad's navigation corridor. */
final class SquadRouteField {
    private final int width;
    private final int height;
    private final int goal;
    private final int corridorCellCount;
    private final int[] cells;
    private final int[] next;

    private SquadRouteField(int width, int height, int goal, int corridorCellCount,
                            int[] cells, int[] next) {
        this.width = width;
        this.height = height;
        this.goal = goal;
        this.corridorCellCount = corridorCellCount;
        this.cells = cells;
        this.next = next;
    }

    int corridorCellCount() { return corridorCellCount; }
    int settledCellCount() { return cells.length; }
    boolean covers(int globalCell) {
        return Arrays.binarySearch(cells, globalCell) >= 0;
    }

    /**
     * Returns ordinary interleaved x/y path cells, including both endpoints.
     * An empty result means this field does not cover the start, not that the
     * destination is globally unreachable. The owner must try ordinary routing.
     */
    int[] extract(int startX, int startY) {
        if (startX < 0 || startX >= width || startY < 0 || startY >= height) {
            return GridPathfinder.EMPTY_PATH;
        }
        int start = startY * width + startX;
        int cursor = start;
        int count = 1;
        while (true) {
            int local = Arrays.binarySearch(cells, cursor);
            if (local < 0) return GridPathfinder.EMPTY_PATH;
            if (cursor == goal) break;
            cursor = next[local];
            if (++count > cells.length) return GridPathfinder.EMPTY_PATH;
        }
        int[] path = new int[count * 2];
        cursor = start;
        for (int i = 0; i < count; i++) {
            path[i * 2] = cursor % width;
            path[i * 2 + 1] = cursor / width;
            if (cursor != goal) cursor = next[Arrays.binarySearch(cells, cursor)];
        }
        return path;
    }

    /** Serial-only reusable workspace; published fields never retain its arrays. */
    static final class Builder {
        private static final int CLOSED = -2;
        private final NavigationGrid grid;
        private final int width;
        private final int height;
        private final int[] seen;
        private final int[] allowed;
        private final int[] required;
        private final float[] distance;
        private final int[] next;
        private final int[] heapPosition;
        private final int[] heap;
        private final int[] settled;
        private int generation;
        private int heapSize;

        Builder(NavigationGrid grid) {
            this.grid = grid;
            width = grid.getWidth();
            height = grid.getHeight();
            int size = width * height;
            seen = new int[size];
            allowed = new int[size];
            required = new int[size];
            distance = new float[size];
            next = new int[size];
            heapPosition = new int[size];
            heap = new int[size];
            settled = new int[size];
        }

        /**
         * Settles only the corridor up to the last distinct valid required start.
         * Invalid, blocked, and outside-corridor starts are left for fallback.
         * Costs follow GridPathfinder's destination-cell multiplier contract.
         */
        SquadRouteField build(int[] sortedCorridorCells, RouteCostField cost, int goal,
                              int[] requiredStartCells, boolean cardinalOnly) {
            if (cost != null && cost.size() != seen.length) {
                throw new IllegalArgumentException("field dimensions must match navigation grid");
            }
            if (++generation == 0) {
                Arrays.fill(seen, 0);
                Arrays.fill(allowed, 0);
                Arrays.fill(required, 0);
                generation = 1;
            }
            long[] flags = grid.getCellFlagsArray();
            byte[] edges = grid.getEdgePassabilityArray();
            int corridorCount = 0;
            int previous = -1;
            for (int cell : sortedCorridorCells) {
                if (cell <= previous || cell >= seen.length) {
                    throw new IllegalArgumentException("corridor cells must be valid, unique, and sorted");
                }
                previous = cell;
                if ((flags[cell] & 1L) != 0) {
                    allowed[cell] = generation;
                    corridorCount++;
                }
            }
            if (goal < 0 || goal >= seen.length || allowed[goal] != generation
                    || (flags[goal] & 1L) == 0) {
                return publish(goal, corridorCount, 0);
            }
            int remaining = 0;
            for (int start : requiredStartCells) {
                if (start >= 0 && start < seen.length && allowed[start] == generation
                        && (flags[start] & 1L) != 0 && required[start] != generation) {
                    required[start] = generation;
                    remaining++;
                }
            }
            seen[goal] = generation;
            distance[goal] = 0f;
            next[goal] = goal;
            heap[0] = goal;
            heapPosition[goal] = 0;
            heapSize = 1;
            int settledCount = 0;
            int directionCount = GridPathfinder.directionCount(cardinalOnly);
            while (heapSize > 0) {
                int current = pop();
                heapPosition[current] = CLOSED;
                settled[settledCount++] = current;
                if (required[current] == generation) remaining--;
                if (remaining == 0) break;
                int x = current % width;
                int y = current / width;
                // Every predecessor enters this same cell; decode its block only once.
                float multiplier = cost == null ? 1f : cost.costAt(current, x, y);
                for (int direction = 0; direction < directionCount; direction++) {
                    int px = x - GridPathfinder.directionX(direction);
                    int py = y - GridPathfinder.directionY(direction);
                    if (px < 0 || px >= width || py < 0 || py >= height) continue;
                    int predecessor = py * width + px;
                    if (allowed[predecessor] != generation || (flags[predecessor] & 1L) == 0
                            || (seen[predecessor] == generation
                            && heapPosition[predecessor] == CLOSED)) continue;
                    if (!GridPathfinder.canStep(predecessor, px, py, current,
                            direction, width, height, flags, edges, null)) continue;
                    float candidate = distance[current]
                            + GridPathfinder.stepCost(direction, current, null, multiplier);
                    boolean discovered = seen[predecessor] == generation;
                    if (discovered && candidate >= distance[predecessor]) continue;
                    seen[predecessor] = generation;
                    distance[predecessor] = candidate;
                    next[predecessor] = current;
                    int position;
                    if (discovered) {
                        position = heapPosition[predecessor];
                    } else {
                        position = heapSize++;
                        heap[position] = predecessor;
                        heapPosition[predecessor] = position;
                    }
                    siftUp(position);
                }
            }
            return publish(goal, corridorCount, settledCount);
        }

        private SquadRouteField publish(int goal, int corridorCount, int count) {
            int[] cells = Arrays.copyOf(settled, count);
            Arrays.sort(cells);
            int[] successors = new int[count];
            for (int i = 0; i < count; i++) successors[i] = next[cells[i]];
            return new SquadRouteField(width, height, goal, corridorCount, cells, successors);
        }

        private void siftUp(int position) {
            int node = heap[position];
            while (position > 0) {
                int parentPosition = (position - 1) >>> 1;
                int parent = heap[parentPosition];
                if (distance[node] >= distance[parent]) break;
                heap[position] = parent;
                heapPosition[parent] = position;
                position = parentPosition;
            }
            heap[position] = node;
            heapPosition[node] = position;
        }

        private int pop() {
            int result = heap[0];
            int node = heap[--heapSize];
            int position = 0;
            int half = heapSize >>> 1;
            while (position < half) {
                int left = (position << 1) + 1;
                int right = left + 1;
                int best = right < heapSize && distance[heap[right]] < distance[heap[left]]
                        ? right : left;
                if (distance[node] <= distance[heap[best]]) break;
                heap[position] = heap[best];
                heapPosition[heap[position]] = position;
                position = best;
            }
            if (heapSize > 0) {
                heap[position] = node;
                heapPosition[node] = position;
            }
            return result;
        }
    }
}
