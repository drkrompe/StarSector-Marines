package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.BendFlood;

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
 * write and would drive through buildings. The flood pays for every bend
 * ({@link BendFlood}), so the way back is straight runs and clean bends, as a
 * planner lays a road, never the staircase a breadth-first flood walks down
 * toward a road that lies off both axes.
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
        return weld(owner, width, height, null);
    }

    /**
     * As above, with ground no weld is cut across ({@code blocked}, or null for none): sealed ground and the knolls'
     * tops and rings ({@code elevation-nouns.md}), which a weld goes round, reaching what lies beyond through the
     * gaps left in it. Networks that can be joined only across it are then joined as before, so the map is always
     * one network.
     */
    public static Result weld(int[][] owner, int width, int height, boolean[][] blocked) {
        int[][] component = label(owner, width, height);
        int components = count(component, width, height);
        if (components <= 1) return new Result(components, components, 0, 0);

        BendFlood flood = flood(component, blocked);
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
            cells += carve(owner, component, flood, weld, width, height, blocked);
        }
        if (blocked != null && merged < components) {
            Result rest = weld(owner, width, height, null);
            return new Result(components, rest.componentsAfter(), cut + rest.weldsCut(), cells + rest.cellsCut());
        }
        return new Result(components, components - cut, cut, cells);
    }

    /**
     * Takes up each leg of {@code welded} road (cells an earlier weld laid) that a road laid since has made
     * redundant: one whose removal leaves the map in no more networks than it is in. A weld is laid only where
     * nothing else joins two networks; left in place once a later road joins them, it is a second road beside
     * that one, two carriageways where one carries the traffic. A weld is straight legs joined by bends, and a
     * later road may meet one leg and run beside another, so legs are tried one at a time, the longest first,
     * until none more can go. Returns the cells taken up.
     */
    public static int untie(int[][] owner, boolean[][] welded, int width, int height) {
        int before = components(owner, width, height), taken = 0;
        for (boolean changed = true; changed; ) {
            changed = false;
            List<List<int[]>> legs = legs(owner, welded, width, height);
            legs.sort((a, b) -> Integer.compare(b.size(), a.size()));
            for (List<int[]> leg : legs) {
                int[] was = new int[leg.size()];
                for (int i = 0; i < leg.size(); i++) {
                    int[] c = leg.get(i);
                    was[i] = owner[c[0]][c[1]];
                    owner[c[0]][c[1]] = GrownTrunkPlan.UNOWNED;
                }
                if (components(owner, width, height) <= before) {
                    taken += leg.size();
                    changed = true;
                    continue;
                }
                for (int i = 0; i < leg.size(); i++) owner[leg.get(i)[0]][leg.get(i)[1]] = was[i];
            }
        }
        return taken;
    }

    /**
     * The straight legs of the welded road still standing: each cell joins the way its welded run is longer (along
     * its column or its row), and a leg is the cells of one way joined edge to edge.
     */
    private static List<List<int[]>> legs(int[][] owner, boolean[][] welded, int width, int height) {
        boolean[][] on = new boolean[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++)
            on[x][y] = welded[x][y] && owner[x][y] != GrownTrunkPlan.UNOWNED;
        boolean[][] vertical = new boolean[width][height];
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            if (!on[x][y]) continue;
            int across = 1, along = 1;
            for (int i = x - 1; i >= 0 && on[i][y]; i--) across++;
            for (int i = x + 1; i < width && on[i][y]; i++) across++;
            for (int j = y - 1; j >= 0 && on[x][j]; j--) along++;
            for (int j = y + 1; j < height && on[x][j]; j++) along++;
            vertical[x][y] = along >= across;
        }
        boolean[][] seen = new boolean[width][height];
        List<List<int[]>> legs = new ArrayList<>();
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            if (!on[x][y] || seen[x][y]) continue;
            List<int[]> leg = new ArrayList<>();
            Deque<int[]> open = new ArrayDeque<>();
            seen[x][y] = true;
            open.add(new int[]{x, y});
            while (!open.isEmpty()) {
                int[] c = open.poll();
                leg.add(c);
                for (int[] step : STEPS) {
                    int nx = c[0] + step[0], ny = c[1] + step[1];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height || seen[nx][ny] || !on[nx][ny]
                            || vertical[nx][ny] != vertical[x][y]) continue;
                    seen[nx][ny] = true;
                    open.add(new int[]{nx, ny});
                }
            }
            legs.add(leg);
        }
        return legs;
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

    /**
     * The flood from every component at once, paying for each bend ({@link BendFlood}): each cell's nearest
     * component, its cost, and the way back to it, so a weld walked back down it runs straight and turns cleanly.
     */
    private static BendFlood flood(int[][] component, boolean[][] blocked) {
        return BendFlood.from(component,
                (x, y) -> blocked != null && blocked[x][y] ? -1 : 1, BendFlood.BEND);
    }

    /** One possible link: two adjacent cells whose nearest components differ. */
    private record Weld(int from, int to, int ax, int ay, int bx, int by, int cost) { }

    private static List<Weld> candidates(BendFlood flood, int width, int height) {
        Map<Long, Weld> best = new HashMap<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int a = flood.label(x, y);
                if (a == -1) continue;
                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    if (nx >= width || ny >= height) continue;
                    int b = flood.label(nx, ny);
                    if (b == -1 || b == a) continue;
                    int cost = flood.cost(x, y) + flood.cost(nx, ny) + 1;
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
    private static int carve(int[][] owner, int[][] component, BendFlood flood, Weld weld,
                             int width, int height, boolean[][] blocked) {
        int cells = walkBack(owner, component, flood, weld.ax, weld.ay, width, height, blocked);
        return cells + walkBack(owner, component, flood, weld.bx, weld.by, width, height, blocked);
    }

    private static int walkBack(int[][] owner, int[][] component, BendFlood flood,
                                int x, int y, int width, int height, boolean[][] blocked) {
        int painted = 0;
        // The precinct the road being extended belongs to, so a weld reads as
        // that place's road reaching out rather than as ownerless tarmac.
        int who = ownerOfComponent(owner, component, flood.label(x, y), width, height);
        for (int[] c : flood.back(x, y)) painted += paint(owner, who, c[0], c[1], width, height, blocked);
        return painted;
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

    private static int paint(int[][] owner, int who, int x, int y, int width, int height, boolean[][] blocked) {
        int painted = 0;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dy = -HALF; dy <= HALF; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (owner[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                if (blocked != null && blocked[nx][ny]) continue;
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
