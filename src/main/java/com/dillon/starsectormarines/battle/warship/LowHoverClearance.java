package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/** Clearance for an authored low-hover hull that keeps a fixed rectangular attitude. */
public final class LowHoverClearance {
    private LowHoverClearance() {}

    public static boolean clear(NavigationGrid grid, float x, float y, float width, float height) {
        int minX = (int) Math.floor(x - width / 2f);
        int minY = (int) Math.floor(y - height / 2f);
        int maxX = (int) Math.ceil(x + width / 2f) - 1;
        int maxY = (int) Math.ceil(y + height / 2f) - 1;
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
            }
        }
        return true;
    }

    public static boolean sweptClear(NavigationGrid grid, float x, float y, float nextX, float nextY,
                                     float width, float height) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(nextX - x, nextY - y) * 4));
        for (int i = 0; i <= steps; i++) {
            float share = (float) i / steps;
            if (!clear(grid, x + (nextX - x) * share, y + (nextY - y) * share, width, height)) return false;
        }
        return true;
    }
}
