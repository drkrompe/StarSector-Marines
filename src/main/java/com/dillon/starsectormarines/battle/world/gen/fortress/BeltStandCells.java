package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a belt nest's garrison stands: the run of belt ground (trench floor and sandbag) nearest the nest, taken
 * outward from it along the belt. Pure selection over cells the stage has already dressed; it knows nothing of
 * worlds, props or squads.
 *
 * <p>The run is grown breadth-first over four-connected cells, so it follows the trench round a salient rather than
 * jumping across the open ground a straight-line "nearest" would reach, and the same cells always give the same
 * order. A nest sits inside a sandbag horseshoe whose ring is itself belt ground, so the first cells are the pit's
 * own parapet and the run continues down the trench either side.
 */
public final class BeltStandCells {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private BeltStandCells() {}

    /**
     * Up to {@code count} cells of {@code ground}, nearest the nest first, connected to one another through
     * {@code ground}. The search starts from the cell of {@code ground} nearest the nest by Manhattan distance
     * (ties broken by x then y), and never strays further than {@code reach} cells (Manhattan) from the nest.
     *
     * @param ground the belt cells that can be stood on, packed as {@link #key}
     * @return the chosen cells as {@code {x, y}} in assignment order; empty when no belt ground lies within reach
     */
    public static List<int[]> near(Set<Long> ground, int nestX, int nestY, int count, int reach) {
        List<int[]> out = new ArrayList<>();
        if (count <= 0 || ground.isEmpty()) return out;
        int bestX = 0, bestY = 0, best = Integer.MAX_VALUE;
        for (long packed : ground) {
            int x = x(packed), y = y(packed);
            int d = Math.abs(x - nestX) + Math.abs(y - nestY);
            if (d > reach) continue;
            if (d < best || (d == best && (x < bestX || (x == bestX && y < bestY)))) {
                best = d;
                bestX = x;
                bestY = y;
            }
        }
        if (best == Integer.MAX_VALUE) return out;

        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        seen.add(key(bestX, bestY));
        open.add(new int[]{bestX, bestY});
        while (!open.isEmpty() && out.size() < count) {
            int[] at = open.poll();
            out.add(at);
            for (int[] step : STEPS) {
                int nx = at[0] + step[0], ny = at[1] + step[1];
                if (Math.abs(nx - nestX) + Math.abs(ny - nestY) > reach) continue;
                long k = key(nx, ny);
                if (!ground.contains(k) || !seen.add(k)) continue;
                open.add(new int[]{nx, ny});
            }
        }
        return out;
    }

    /** A cell packed for a {@code Set<Long>}. */
    public static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }

    private static int x(long packed) {
        return (int) (packed >> 32);
    }

    private static int y(long packed) {
        return (int) packed;
    }
}
