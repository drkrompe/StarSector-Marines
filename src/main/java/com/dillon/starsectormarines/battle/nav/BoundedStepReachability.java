package com.dillon.starsectormarines.battle.nav;

import java.util.Arrays;

/**
 * Reusable, bounded minimum-step proof. A positive answer only rejects a route;
 * a negative answer leaves weighted route selection to the ordinary pathfinder.
 * Owned by one caller/thread, and valid only while the prepared topology is fixed.
 */
public final class BoundedStepReachability {
    private int[] distance = new int[0];
    private int[] queue = new int[0];
    private int minX, minY, width, height, depthLimit, provenDepth, expanded;
    private boolean invalidOrigin;

    public void prepare(NavigationGrid grid, int originX, int originY,
                        boolean cardinalOnly, int maxSteps, int maxExpandedNodes) {
        depthLimit = Math.max(0, maxSteps);
        expanded = 0;
        provenDepth = 0;
        invalidOrigin = !grid.inBounds(originX, originY)
                || !grid.isWalkable(originX, originY);
        width = height = 0;
        if (invalidOrigin) return;
        minX = (int) Math.max(0L, (long) originX - depthLimit);
        minY = (int) Math.max(0L, (long) originY - depthLimit);
        int maxX = (int) Math.min(grid.getWidth() - 1L, (long) originX + depthLimit);
        int maxY = (int) Math.min(grid.getHeight() - 1L, (long) originY + depthLimit);
        width = maxX - minX + 1;
        height = maxY - minY + 1;
        int cells = Math.multiplyExact(width, height);
        if (distance.length < cells) {
            distance = new int[cells];
            queue = new int[cells];
        }
        Arrays.fill(distance, 0, cells, -1);
        int seed = (originY - minY) * width + originX - minX;
        distance[seed] = 0;
        queue[0] = seed;
        int head = 0;
        int tail = 1;
        int gridWidth = grid.getWidth();
        long[] flags = grid.getCellFlagsArray();
        byte[] edges = grid.getEdgePassabilityArray();
        while (head < tail) {
            int local = queue[head];
            int depth = distance[local];
            // FIFO discovery has already found every cell through this depth.
            provenDepth = depth;
            if (depth >= depthLimit || expanded >= Math.max(0, maxExpandedNodes)) return;
            head++;
            expanded++;
            int x = minX + local % width;
            int y = minY + local / width;
            int from = y * gridWidth + x;
            for (int direction = 0; direction < (cardinalOnly ? 4 : 8); direction++) {
                Direction step = Direction.ALL[direction];
                int nx = x + step.dx;
                int ny = y + step.dy;
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
                int next = (ny - minY) * width + nx - minX;
                if (distance[next] >= 0) continue;
                if (!GridPathfinder.canStep(from, x, y, ny * gridWidth + nx,
                        direction, gridWidth, grid.getHeight(), flags, edges, null)) continue;
                distance[next] = depth + 1;
                queue[tail++] = next;
            }
        }
        provenDepth = depthLimit;
    }

    /** True only when no legal route can fit this step budget. */
    public boolean canReject(int x, int y, int maxSteps) {
        if (maxSteps < 0 || invalidOrigin) return true;
        if (maxSteps > depthLimit) return false;
        if (x < minX || y < minY || x - minX >= width || y - minY >= height) return true;
        int steps = distance[(y - minY) * width + x - minX];
        return steps >= 0 ? steps > maxSteps : maxSteps <= provenDepth;
    }

    public int expandedNodes() { return expanded; }

    /** Retained primitive-array capacity, in cells per array. */
    public int storageCells() { return distance.length; }
}
