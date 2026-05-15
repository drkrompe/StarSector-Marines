package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Builds a battle scenario for the auto-battler. v3: marines arrive via
 * scheduled shuttle drops rather than pre-spawning. {@link UrbanMapGenerator}
 * carves the city; we pick spread-out landing zones in the marine quadrant,
 * stagger the drops, and let {@link BattleSimulation} run the state machine.
 * Defenders still pre-spawn (lore-correct: they're already on the ground).
 */
public final class BattleSetup {

    /** Default battle grid size (cells). Was 24x16 during the MVP loop; widened for urban combat. */
    public static final int GRID_W = 96;
    public static final int GRID_H = 48;

    private static final int DEFENDER_COUNT = 12;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    private static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    private BattleSetup() {}

    public static BattleSimulation createPlaceholder() {
        return createPlaceholder(System.currentTimeMillis());
    }

    public static BattleSimulation createPlaceholder(long seed) {
        UrbanMapGenerator.Result map = UrbanMapGenerator.generate(GRID_W, GRID_H, seed);
        BattleSimulation sim = new BattleSimulation(map.grid);

        // Marines: schedule SHUTTLE_COUNT staggered drops, each at its own LZ.
        // Marines spawn when each shuttle reaches LANDED and the deboard timer fires.
        List<int[]> lzCells = pickLandingZones(map.grid, map.marineSpawnX, map.marineSpawnY, SHUTTLE_COUNT);
        float topEdgeY = GRID_H;
        for (int i = 0; i < lzCells.size(); i++) {
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            // Entry directly above the LZ, off the top of the grid. Exit a bit further off to give
            // the departing shuttle a moment of visible climb before it disappears.
            float entryX = lzCenterX;
            float entryY = topEdgeY + SHUTTLE_OFFMAP_Y;
            float exitX  = lzCenterX;
            float exitY  = topEdgeY + SHUTTLE_OFFMAP_Y + 4f;
            sim.addShuttle(new Shuttle(
                    ShuttleType.BASIC_SHUTTLE, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entryX, entryY,
                    exitX, exitY,
                    i * SHUTTLE_DROP_STAGGER_SEC));
        }

        // Defenders pre-spawn around their anchor.
        List<int[]> defenderCells = pickSpawnCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);
        for (int i = 0; i < defenderCells.size(); i++) {
            int[] p = defenderCells.get(i);
            sim.addUnit(new Unit("d" + i, Faction.DEFENDER, p[0], p[1]));
        }
        return sim;
    }

    /**
     * BFS from the marine anchor; keeps the first {@code count} walkable cells
     * that are each at least {@link #LZ_MIN_SEPARATION} from every previously
     * picked LZ. Spreads drops across the marine quadrant instead of stacking
     * them on the anchor. Falls back to the anchor itself if not enough spread
     * cells exist (tight map) — better one stacked LZ than zero shuttles.
     */
    private static List<int[]> pickLandingZones(NavigationGrid grid, int anchorX, int anchorY, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{anchorX, anchorY});
        seen.add(key(anchorX, anchorY));
        int minSepSq = LZ_MIN_SEPARATION * LZ_MIN_SEPARATION;
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (grid.isWalkable(p[0], p[1])) {
                boolean farEnough = true;
                for (int[] prev : picked) {
                    int dx = prev[0] - p[0];
                    int dy = prev[1] - p[1];
                    if (dx * dx + dy * dy < minSepSq) {
                        farEnough = false;
                        break;
                    }
                }
                if (farEnough) picked.add(new int[]{p[0], p[1]});
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        while (picked.size() < count) picked.add(new int[]{anchorX, anchorY});
        return picked;
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
