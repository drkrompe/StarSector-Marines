package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Welds the road networks of separately grown places into one.
 *
 * <p>Growth joins two places only by accident. An arm stops when it runs into
 * an existing band, so where one precinct happens to grow into another they
 * connect — and where their networks run near each other without touching, they
 * do not. The result is a map of several road systems that look connected and
 * are not, which is invisible in a picture and severe for anything that drives.
 *
 * <p>So connectedness is solved for rather than hoped for. Every road cell is
 * labelled with the component it belongs to, the ground between components is
 * flooded from all of them at once, and the cheapest meeting point between each
 * pair of components becomes a candidate weld. Those are then taken in
 * increasing cost until one network remains, which is a minimum spanning tree
 * over the components — so the map gains the fewest, shortest links that make
 * it whole, rather than a road between every pair of places.
 *
 * <p><b>The weld follows the flood, not a straight line.</b> Each side's route
 * is walked back along the parent pointers the flood recorded, so it bends
 * around whatever the flood bent around. A straight link would be quicker to
 * write and would drive through buildings.
 */
public final class PrecinctInterconnect {

    private PrecinctInterconnect() {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Cells wide a weld is cut, matching what a vehicle needs of a gate. */
    private static final int WELD_WIDTH = PrecinctBoundary.DRIVABLE_GATE_WIDTH;

    private static final int HALF = WELD_WIDTH / 2;

    /** What welding had to do, so a map that needed a lot of it can say so. */
    public record Result(int componentsBefore, int componentsAfter, int weldsCut,
                         int cellsCut) { }

    /**
     * Joins every road component into one, cutting as little as possible.
     *
     * <p>Mutates {@code owner}, marking new road for whichever precinct already
     * owned the component it extends. Ground with no road on it at all is left
     * alone — a map with one network already is not touched.
     */
    public static Result weld(int[][] owner, int width, int height) {
        int[][] component = label(owner, width, height);
        int components = count(component, width, height);
        if (components <= 1) return new Result(components, components, 0, 0);

        Flood flood = flood(component, width, height);
        List<Weld> candidates = candidates(flood, width, height);
        candidates.sort((a, b) -> Integer.compare(a.cost, b.cost));

        int[] parent = new int[components];
        for (int i = 0; i < components; i++) parent[i] = i;
        int cut = 0;
        int cells = 0;
        int merged = 1;
        for (Weld weld : candidates) {
            if (merged >= components) break;
            int a = find(parent, weld.from);
            int b = find(parent, weld.to);
            if (a == b) continue;
            parent[a] = b;
            merged++;
            cut++;
            cells += carve(owner, component, flood, weld, width, height);
        }
        return new Result(components, components - cut, cut, cells);
    }

    /** How many separate road networks a map has. */
    public static int components(int[][] owner, int width, int height) {
        return count(label(owner, width, height), width, height);
    }

    // ---- components -------------------------------------------------------

    private static int[][] label(int[][] owner, int width, int height) {
        int[][] component = new int[width][height];
        for (int[] column : component) java.util.Arrays.fill(column, -1);
        int next = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (owner[x][y] == GrownTrunkPlan.UNOWNED || component[x][y] != -1) continue;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{x, y});
                component[x][y] = next;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    for (int[] step : STEPS) {
                        int nx = at[0] + step[0];
                        int ny = at[1] + step[1];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        if (owner[nx][ny] == GrownTrunkPlan.UNOWNED) continue;
                        if (component[nx][ny] != -1) continue;
                        component[nx][ny] = next;
                        stack.push(new int[]{nx, ny});
                    }
                }
                next++;
            }
        }
        return component;
    }

    private static int count(int[][] component, int width, int height) {
        int max = -1;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) max = Math.max(max, component[x][y]);
        }
        return max + 1;
    }

    // ---- the flood between them -------------------------------------------

    /** Per-cell nearest component, its distance, and the step back toward it. */
    private record Flood(int[][] nearest, int[][] distance, int[][] fromX, int[][] fromY) { }

    private static Flood flood(int[][] component, int width, int height) {
        int[][] nearest = new int[width][height];
        int[][] distance = new int[width][height];
        int[][] fromX = new int[width][height];
        int[][] fromY = new int[width][height];
        for (int[] column : nearest) java.util.Arrays.fill(column, -1);
        for (int[] column : distance) java.util.Arrays.fill(column, Integer.MAX_VALUE);

        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (component[x][y] == -1) continue;
                nearest[x][y] = component[x][y];
                distance[x][y] = 0;
                fromX[x][y] = x;
                fromY[x][y] = y;
                queue.add(new int[]{x, y});
            }
        }
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (nearest[nx][ny] != -1) continue;
                nearest[nx][ny] = nearest[at[0]][at[1]];
                distance[nx][ny] = distance[at[0]][at[1]] + 1;
                fromX[nx][ny] = at[0];
                fromY[nx][ny] = at[1];
                queue.add(new int[]{nx, ny});
            }
        }
        return new Flood(nearest, distance, fromX, fromY);
    }

    /** One possible link: two adjacent cells whose nearest components differ. */
    private record Weld(int from, int to, int ax, int ay, int bx, int by, int cost) { }

    private static List<Weld> candidates(Flood flood, int width, int height) {
        Map<Long, Weld> best = new HashMap<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int a = flood.nearest()[x][y];
                if (a == -1) continue;
                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    if (nx >= width || ny >= height) continue;
                    int b = flood.nearest()[nx][ny];
                    if (b == -1 || b == a) continue;
                    int cost = flood.distance()[x][y] + flood.distance()[nx][ny] + 1;
                    long key = (long) Math.min(a, b) * 100_000L + Math.max(a, b);
                    Weld existing = best.get(key);
                    if (existing == null || cost < existing.cost) {
                        best.put(key, new Weld(a, b, x, y, nx, ny, cost));
                    }
                }
            }
        }
        return new ArrayList<>(best.values());
    }

    // ---- cutting ----------------------------------------------------------

    /** Walks both sides of a weld back to their own road, painting as it goes. */
    private static int carve(int[][] owner, int[][] component, Flood flood, Weld weld,
                             int width, int height) {
        int cells = walkBack(owner, component, flood, weld.ax, weld.ay, width, height);
        return cells + walkBack(owner, component, flood, weld.bx, weld.by, width, height);
    }

    private static int walkBack(int[][] owner, int[][] component, Flood flood,
                                int x, int y, int width, int height) {
        int painted = 0;
        int cx = x;
        int cy = y;
        // The precinct the road being extended belongs to, so a weld reads as
        // that place's road reaching out rather than as ownerless tarmac.
        int who = ownerOfComponent(owner, component, flood.nearest()[x][y], width, height);
        while (true) {
            painted += paint(owner, who, cx, cy, width, height);
            if (component[cx][cy] != -1) return painted;
            int px = flood.fromX()[cx][cy];
            int py = flood.fromY()[cx][cy];
            if (px == cx && py == cy) return painted;
            cx = px;
            cy = py;
        }
    }

    private static int ownerOfComponent(int[][] owner, int[][] component, int which,
                                        int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (component[x][y] == which) return owner[x][y];
            }
        }
        return 0;
    }

    private static int paint(int[][] owner, int who, int x, int y, int width, int height) {
        int painted = 0;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dy = -HALF; dy <= HALF; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (owner[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                owner[nx][ny] = who;
                painted++;
            }
        }
        return painted;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
