package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import java.util.List;
import java.util.function.BiPredicate;

/** Separate, reachable ramps confined to the mission's safe pickup area. */
final class PickupLandingSlots {
    static final int RADIUS = 12;
    static final int SEPARATION = 8;
    private PickupLandingSlots() { }

    static int[] find(NavigationGrid grid, int x, int y, List<int[]> occupied,
                      BiPredicate<Integer, Integer> outdoor) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dy = -RADIUS; dy <= RADIUS; dy++) for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            int distance = dx * dx + dy * dy;
            if (distance > RADIUS * RADIUS || distance >= bestDistance) continue;
            int px = x + dx, py = y + dy;
            boolean clear = true;
            for (int[] other : occupied) {
                if (Math.hypot(px - other[0], py - other[1]) < SEPARATION) { clear = false; break; }
            }
            if (!clear) continue;
            for (int cy = py - 2; cy <= py + 2 && clear; cy++) for (int cx = px - 2; cx <= px + 2; cx++) {
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || !outdoor.test(cx, cy)) {
                    clear = false; break;
                }
            }
            if (!clear || !grid.arePathConnected(x, y, px, py, GridPathfinder.USE_CARDINAL_NAVIGATION)) continue;
            best = new int[]{px, py}; bestDistance = distance;
        }
        return best;
    }
}
