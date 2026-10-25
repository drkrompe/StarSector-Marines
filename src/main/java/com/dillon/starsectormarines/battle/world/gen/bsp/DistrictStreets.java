package com.dillon.starsectormarines.battle.world.gen.bsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rule a district's street rewrite answers to ({@code set-piece-districts.md}): a district owns the
 * streets inside its claim and may straighten, widen, narrow or close them, but it never closes the network.
 * An <b>entry</b> is a street cell of the claim with a street cell outside the claim beside it, a way the
 * outside network had in. A rewrite is kept only if every entry is still street and any two entries joined
 * through the claim's streets before are still joined through them after.
 *
 * <p>The rewrite is published back as road ({@link #apply}) before the road graph is built, so vehicles, the
 * graph and the tactical links still see one network.
 */
public final class DistrictStreets {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private DistrictStreets() { }

    /** The claim's entries under {@code road}, {@code {x, y}} each, in scan order. */
    public static List<int[]> entries(DistrictClaim claim, boolean[][] road) {
        int[] b = claim.bounds();
        List<int[]> entries = new ArrayList<>();
        for (int y = b[1] - 1; y <= b[3] + 1; y++) {
            for (int x = b[0] - 1; x <= b[2] + 1; x++) {
                if (!street(road, x, y) || !claim.owns(x, y)) continue;
                for (int[] s : STEPS) {
                    int nx = x + s[0], ny = y + s[1];
                    if (street(road, nx, ny) && !claim.owns(nx, ny)) {
                        entries.add(new int[]{x, y});
                        break;
                    }
                }
            }
        }
        return entries;
    }

    /**
     * The claim's entries under {@code road} grouped into <b>mouths</b>: each mouth is one way in, the
     * touching entry cells where one outside street meets the claim. A street narrowed at its mouth keeps the
     * way in while it keeps one of the mouth's cells.
     */
    public static List<List<int[]>> mouths(DistrictClaim claim, boolean[][] road) {
        List<int[]> entries = entries(claim, road);
        boolean[] taken = new boolean[entries.size()];
        List<List<int[]>> mouths = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (taken[i]) continue;
            List<int[]> mouth = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            taken[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                int[] cell = entries.get(queue.removeFirst());
                mouth.add(cell);
                for (int j = 0; j < entries.size(); j++) {
                    int[] other = entries.get(j);
                    if (!taken[j] && Math.abs(other[0] - cell[0]) + Math.abs(other[1] - cell[1]) == 1) {
                        taken[j] = true;
                        queue.add(j);
                    }
                }
            }
            mouths.add(mouth);
        }
        return mouths;
    }

    /**
     * Whether rewriting the claim's streets from {@code before} to {@code after} keeps every way in: each
     * mouth keeps a street cell, and two mouths joined through the claim's streets before are still joined
     * after. Only the claim's own cells of {@code after} are read. Read-only.
     */
    public static boolean keepsEntries(DistrictClaim claim, boolean[][] before, boolean[][] after) {
        int[] b = claim.bounds();
        int[][] was = components(claim, before, b), now = components(claim, after, b);
        Map<Integer, Integer> joined = new HashMap<>();
        for (List<int[]> mouth : mouths(claim, before)) {
            int kept = 0;
            for (int[] cell : mouth) {
                kept = now[cell[0] - b[0]][cell[1] - b[1]];
                if (kept != 0) break;
            }
            if (kept == 0) return false;
            int[] first = mouth.get(0);
            Integer previous = joined.putIfAbsent(was[first[0] - b[0]][first[1] - b[1]], kept);
            if (previous != null && previous != kept) return false;
        }
        return true;
    }

    /** Writes the claim's cells of {@code after} into each of {@code masks}; every other cell is left alone. */
    public static void apply(DistrictClaim claim, boolean[][] after, boolean[][]... masks) {
        int[] b = claim.bounds();
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (!claim.owns(x, y)) continue;
                for (boolean[][] mask : masks) mask[x][y] = after[x][y];
            }
        }
    }

    /** Connected street components inside the claim, numbered from 1 over its bounds; 0 is not street. */
    private static int[][] components(DistrictClaim claim, boolean[][] road, int[] b) {
        int w = b[2] - b[0] + 1, h = b[3] - b[1] + 1;
        int[][] label = new int[w][h];
        int next = 0;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (label[x - b[0]][y - b[1]] != 0 || !inside(claim, road, x, y)) continue;
                label[x - b[0]][y - b[1]] = ++next;
                queue.add(new int[]{x, y});
                while (!queue.isEmpty()) {
                    int[] c = queue.removeFirst();
                    for (int[] s : STEPS) {
                        int nx = c[0] + s[0], ny = c[1] + s[1];
                        if (nx < b[0] || ny < b[1] || nx > b[2] || ny > b[3]) continue;
                        if (label[nx - b[0]][ny - b[1]] != 0 || !inside(claim, road, nx, ny)) continue;
                        label[nx - b[0]][ny - b[1]] = next;
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return label;
    }

    private static boolean inside(DistrictClaim claim, boolean[][] road, int x, int y) {
        return street(road, x, y) && claim.owns(x, y);
    }

    private static boolean street(boolean[][] road, int x, int y) {
        return x >= 0 && y >= 0 && x < road.length && y < road[0].length && road[x][y];
    }
}
