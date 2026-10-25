package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/** Where a walker stops at a spore haze's edge rather than step back into it (`bombard.md`, Counters). */
public final class HazeEdge {

    private HazeEdge() { }

    /**
     * Whether a walker at {@code (x, y)} waits rather than step onto {@code (nextX, nextY)}: it stands clear, the
     * step is into haze, and its route ends in haze, so it would only walk in to stand there. A route that crosses a
     * haze to clear ground beyond goes on.
     */
    public static boolean waits(NavigationGrid grid, int x, int y, int nextX, int nextY, int destX, int destY) {
        return !grid.hasHaze(x, y) && grid.hasHaze(nextX, nextY) && grid.hasHaze(destX, destY);
    }
}
