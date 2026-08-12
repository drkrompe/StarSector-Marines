package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/** Selects authored shuttle berths first, then fills any shortfall with legacy BFS LZs. */
final class LandingPadSelector {

    private LandingPadSelector() {}

    static List<LandingPad> select(MapResult map, int count, int minSeparation) {
        List<LandingPad> selected = new ArrayList<>();
        List<LandingPad> authored = new ArrayList<>(map.landingPads);
        authored.sort(Comparator
                .comparingInt((LandingPad p) -> distanceSq(
                        p.centerX, p.centerY, map.marineSpawnX, map.marineSpawnY))
                .thenComparingInt(p -> p.centerY)
                .thenComparingInt(p -> p.centerX));

        for (LandingPad pad : authored) {
            if (selected.size() >= count) break;
            if (!pad.isClear(map.grid, map.topology)) continue;
            if (farEnough(pad.centerX, pad.centerY, selected, minSeparation)) selected.add(pad);
        }

        appendFallbacks(map, map.marineSpawnX, map.marineSpawnY,
                count, minSeparation, selected);
        return selected;
    }

    private static void appendFallbacks(MapResult map, int anchorX, int anchorY,
                                        int count, int minSeparation,
                                        List<LandingPad> selected) {
        NavigationGrid grid = map.grid;
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{anchorX, anchorY});
        seen.add(key(anchorX, anchorY));
        while (!q.isEmpty() && selected.size() < count) {
            int[] p = q.poll();
            if (hasFallbackClearance(map, p[0], p[1])
                    && farEnough(p[0], p[1], selected, minSeparation)) {
                selected.add(LandingPad.fallback(p[0], p[1]));
            }
            int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : neighbors) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny) || !seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        while (selected.size() < count) {
            selected.add(LandingPad.fallback(anchorX, anchorY));
        }
    }

    /** Legacy fallback still requires a clear outdoor 3x3, not just one walkable cell. */
    private static boolean hasFallbackClearance(MapResult map, int centerX, int centerY) {
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (!map.grid.inBounds(x, y) || !map.grid.isWalkable(x, y)) return false;
                if (map.topology.getBuildingId(x, y) != 0) return false;
            }
        }
        return true;
    }

    private static boolean farEnough(int x, int y, List<LandingPad> selected, int minSeparation) {
        int minSq = minSeparation * minSeparation;
        for (LandingPad previous : selected) {
            if (distanceSq(x, y, previous.centerX, previous.centerY) < minSq) return false;
        }
        return true;
    }

    private static int distanceSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
