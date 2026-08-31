package com.dillon.starsectormarines.battle.nav;

/** Resolves a requested cell to the nearest cell in one mover's component. */
public final class ReachableCellResolver {

    private ReachableCellResolver() { }

    /**
     * Returns the closest walkable cell connected to {@code originX,originY}.
     * Stable cell-index order breaks equal-distance ties.
     */
    public static int[] nearest(NavigationGrid grid, int originX, int originY,
                                int requestedX, int requestedY) {
        if (grid == null || !grid.inBounds(requestedX, requestedY)
                || !grid.inBounds(originX, originY)) return null;
        int[] components = GridPathfinder.labelConnectedComponents(grid);
        int width = grid.getWidth();
        int component = components[grid.index(originX, originY)];
        if (component < 0) return null;

        int best = -1;
        long bestDistanceSq = Long.MAX_VALUE;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                int index = grid.index(x, y);
                if (components[index] != component || !grid.isWalkable(x, y)) continue;
                long dx = x - (long) requestedX;
                long dy = y - (long) requestedY;
                long distanceSq = dx * dx + dy * dy;
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    best = index;
                }
            }
        }
        return best >= 0 ? new int[]{best % width, best / width} : null;
    }
}
