package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ground the Cordon sets people down on mid-battle. */
public final class CordonGround {

    /** Cells searched outward before giving up on a cluster. */
    private static final int SEARCH = 4_000;

    private CordonGround() {}

    /**
     * Up to {@code count} free walkable cells nearest {@code (x, y)} by walking
     * distance, the seed's own region first: where a group stands up together.
     * Starts from the nearest walkable cell when the seed itself is not.
     */
    /** {@link #cluster} round the open cell nearest a point, for setup outside this package. */
    public static List<int[]> clusterNear(BattleSimulation sim, int x, int y, int count) {
        int[] at = nearestWalkable(sim.getGrid(), x, y);
        return at == null ? List.of() : cluster(sim.getGrid(), sim.getOccupancyMap(), at[0], at[1], count);
    }

    static List<int[]> cluster(NavigationGrid grid, byte[] occupancy, int x, int y, int count) {
        List<int[]> out = new ArrayList<>();
        int[] start = nearestWalkable(grid, x, y);
        if (start == null) return out;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        queue.add(start);
        seen.add(grid.index(start[0], start[1]));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && out.size() < count && seen.size() < SEARCH) {
            int[] c = queue.poll();
            if (!grid.isDoorway(c[0], c[1]) && (occupancy[grid.index(c[0], c[1])] & 0xFF) == 0) out.add(c);
            for (int[] d : steps) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || !seen.add(grid.index(nx, ny))) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return out;
    }

    /** The walkable cell nearest {@code (x, y)} within twenty cells, or {@code null}. */
    static int[] nearestWalkable(NavigationGrid grid, int x, int y) {
        return nearestWalkable(grid, x, y, (cx, cy) -> true);
    }

    /** A cell a search will take. */
    interface Takes {
        boolean at(int x, int y);
    }

    /** The walkable cell nearest {@code (x, y)} within twenty cells that {@code takes} accepts, or {@code null}. */
    static int[] nearestWalkable(NavigationGrid grid, int x, int y, Takes takes) {
        for (int r = 0; r <= 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy) && takes.at(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }
}
