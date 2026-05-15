package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Builds a battle scenario for the auto-battler. v2 uses {@link UrbanMapGenerator}
 * for a city-block layout and BFS-clusters units around each side's spawn anchor —
 * marines in the top-left, defenders in the bottom-right. Anchors are guaranteed
 * walkable so the cluster fills out into the adjacent street even if the seed
 * picked a tight alley.
 */
public final class BattleSetup {

    /** Default battle grid size (cells). Was 24x16 during the MVP loop; widened for urban combat. */
    public static final int GRID_W = 96;
    public static final int GRID_H = 48;

    private static final int MARINE_COUNT   = 12;
    private static final int DEFENDER_COUNT = 12;

    private BattleSetup() {}

    public static BattleSimulation createPlaceholder() {
        return createPlaceholder(System.currentTimeMillis());
    }

    public static BattleSimulation createPlaceholder(long seed) {
        UrbanMapGenerator.Result map = UrbanMapGenerator.generate(GRID_W, GRID_H, seed);
        BattleSimulation sim = new BattleSimulation(map.grid);

        List<int[]> marineCells   = pickSpawnCluster(map.grid, map.marineSpawnX,   map.marineSpawnY,   MARINE_COUNT);
        List<int[]> defenderCells = pickSpawnCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);

        for (int i = 0; i < marineCells.size(); i++) {
            int[] p = marineCells.get(i);
            sim.addUnit(new Unit("m" + i, Faction.MARINE, p[0], p[1]));
        }
        for (int i = 0; i < defenderCells.size(); i++) {
            int[] p = defenderCells.get(i);
            sim.addUnit(new Unit("d" + i, Faction.DEFENDER, p[0], p[1]));
        }
        return sim;
    }

    /** BFS from (cx, cy) over walkable cells, returning the first {@code count} cells in BFS order. */
    private static List<int[]> pickSpawnCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        seen.add(key(cx, cy));
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (!grid.isWalkable(p[0], p[1])) continue;
            picked.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        return picked;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
