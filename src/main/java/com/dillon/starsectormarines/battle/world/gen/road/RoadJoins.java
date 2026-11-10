package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Makes the drawn roads one network ({@code precincts.md}, Connectedness is solved for). The ground a road may be drawn on is
 * already one network once the places are welded, but the sweep that finishes it draws pavement only where a band
 * makes a street: a weld across a town's ground, a band refused as a pill or a stretch narrowed by its kerbs is left
 * bare, and what is drawn falls into pieces that read as roads ending in the dirt.
 *
 * <p>So the drawn roads are joined along the network they lie on: the pieces are labelled, the network is flooded
 * from all of them at once, keeping to the middle of its bands, and the cheapest meeting between each pair is taken
 * in increasing order until no two pieces the network joins are apart, a minimum spanning tree over the pieces.
 * Each join is walked back along the flood to both pieces and drawn as a road down it, {@link #WIDTH} wide. The
 * flood pays for its bends ({@link BendFlood}), so a join runs straight along a grid axis and turns cleanly, as
 * the streets it joins do. A piece the network does not
 * reach stays as it is: joining it would draw a road across ground nobody laid one on.
 */
public final class RoadJoins {

    private RoadJoins() {}

    /** Cells of clearance from a band's edge the flood prefers; a cell nearer its edge costs more. */
    static final int MIDDLE = 3;
    /** Extra cost per cell of clearance short of {@link #MIDDLE}. */
    static final int EDGE_COST = 4;
    /**
     * Cells wide every join is drawn: a join is the drawn face of a weld, one class of road, and holds the weld's
     * width along its whole length rather than swelling and narrowing with the ground it crosses.
     */
    static final int WIDTH = 3;

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Share of a cell its pavement must cover for the cell to read as road. */
    static final double DRAWN = .4;

    /** The cells a finished surface reads as road on: pavement of any material over {@link #DRAWN} of the cell. */
    public static boolean[][] drawn(RoadSurface surface) {
        int w = surface.width(), h = surface.height();
        boolean[][] out = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            double area = 0;
            for (int m = 0; m < RoadSurface.MATERIALS; m++) {
                RoadSurface.Patch p = surface.patch(x, y, m);
                if (p != null) area = Math.max(area, p.area());
            }
            out[x][y] = area > DRAWN;
        }
        return out;
    }

    /**
     * The cells a route's sweep can reach: each cell whose centre lies within half a cell more than its route's
     * half-width of the route's line. A cell's centre is its integer coordinate, as the sweeps lay their points.
     */
    public static boolean[][] covered(List<Route> routes, int width, int height) {
        boolean[][] out = new boolean[width][height];
        for (Route r : routes) {
            double reach = r.width() / 2 + .5;
            List<Point> p = r.points();
            for (int i = 0; i + 1 < p.size() || i == 0 && p.size() == 1; i++) {
                Point a = p.get(i), b = p.get(Math.min(i + 1, p.size() - 1));
                int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - reach), x1 = (int) Math.ceil(Math.max(a.x(), b.x()) + reach);
                int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - reach), y1 = (int) Math.ceil(Math.max(a.y(), b.y()) + reach);
                for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++)
                    for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++)
                        if (distance(x, y, a, b) <= reach) out[x][y] = true;
            }
        }
        return out;
    }

    /**
     * The routes that join the drawn pieces ({@code drawn}) along the network ({@code network}, every cell a road
     * may be drawn on; a drawn cell off it counts as on it). Empty when the drawn roads are already one network.
     */
    public static List<Route> joins(boolean[][] network, boolean[][] drawn, long seed) {
        List<Route> out = new ArrayList<>();
        int w = network.length, h = network[0].length;
        boolean[][] passable = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) passable[x][y] = network[x][y] || drawn[x][y];
        List<List<int[]>> paths = paths(passable, drawn);
        if (paths.isEmpty()) return out;
        for (List<int[]> path : paths) {
            for (List<int[]> run : runs(path)) {
                int[] a = run.get(0), b = run.get(run.size() - 1);
                out.add(new Route("join-" + out.size(), List.of(new Point(a[0], a[1]), new Point(b[0], b[1])),
                        WIDTH, 0, .4, seed, Material.ASPHALT, EndCap.ROUND));
            }
        }
        return out;
    }

    /**
     * The cell paths that join the pieces of {@code drawn} through {@code passable}: one per link of a minimum
     * spanning tree over the pieces the passable ground connects, each running from one piece to the other.
     */
    static List<List<int[]>> paths(boolean[][] passable, boolean[][] drawn) {
        int w = passable.length, h = passable[0].length;
        int[][] piece = new int[w][h];
        for (int[] row : piece) Arrays.fill(row, -1);
        int pieces = 0;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            if (!drawn[x][y] || piece[x][y] >= 0) continue;
            ArrayDeque<int[]> open = new ArrayDeque<>();
            open.add(new int[]{x, y});
            piece[x][y] = pieces;
            while (!open.isEmpty()) {
                int[] c = open.poll();
                for (int[] d : STEPS) {
                    int nx = c[0] + d[0], ny = c[1] + d[1];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h || !drawn[nx][ny] || piece[nx][ny] >= 0) continue;
                    piece[nx][ny] = pieces;
                    open.add(new int[]{nx, ny});
                }
            }
            pieces++;
        }
        if (pieces <= 1) return List.of();

        int[][] clearance = clearance(passable);
        // The flood pays for its bends, so a join walked back down it is straight runs and clean bends.
        BendFlood flood = BendFlood.from(piece, (x, y) -> passable[x][y]
                ? 1 + EDGE_COST * Math.max(0, MIDDLE - clearance[x][y]) : -1, BendFlood.BEND);
        // Every edge between two floods is a candidate meeting; the cheapest per pair is enough, but sorting all
        // of them is simpler and the union-find skips the rest.
        List<long[]> meetings = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            int a = flood.label(x, y);
            if (a < 0) continue;
            for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= w || ny >= h) continue;
                int b = flood.label(nx, ny);
                if (b < 0 || b == a) continue;
                meetings.add(new long[]{(long) flood.cost(x, y) + flood.cost(nx, ny) + 1, y * w + x, ny * w + nx});
            }
        }
        meetings.sort((p, q) -> Long.compare(p[0], q[0]));
        int[] root = new int[pieces];
        for (int k = 0; k < pieces; k++) root[k] = k;
        List<List<int[]>> out = new ArrayList<>();
        for (long[] m : meetings) {
            int i = (int) m[1], j = (int) m[2];
            int ra = find(root, flood.label(i % w, i / w)), rb = find(root, flood.label(j % w, j / w));
            if (ra == rb) continue;
            root[ra] = rb;
            List<int[]> path = new ArrayList<>(flood.back(i % w, i / w));
            Collections.reverse(path);
            path.addAll(flood.back(j % w, j / w));
            out.add(path);
        }
        return out;
    }

    /** Chebyshev distance from each passable cell to the nearest cell that is not, counting the cell itself as one. */
    static int[][] clearance(boolean[][] passable) {
        int w = passable.length, h = passable[0].length;
        int[][] out = new int[w][h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            if (!passable[x][y]) continue;
            boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
            for (int dx = -1; dx <= 1 && !edge; dx++) for (int dy = -1; dy <= 1 && !edge; dy++)
                edge = !passable[x + dx][y + dy];
            if (edge) {
                out[x][y] = 1;
                open.add(new int[]{x, y});
            }
        }
        while (!open.isEmpty()) {
            int[] c = open.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                int nx = c[0] + dx, ny = c[1] + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || !passable[nx][ny] || out[nx][ny] != 0) continue;
                out[nx][ny] = out[c[0]][c[1]] + 1;
                open.add(new int[]{nx, ny});
            }
        }
        return out;
    }

    /**
     * A cell path as its straight runs, each along a grid axis: a new run starts wherever the path turns, so a join
     * is drawn as the planner's road it follows and never as a diagonal stroke across the cells between its bends.
     * Each run shares its end cell with the next.
     */
    static List<List<int[]>> runs(List<int[]> path) {
        List<List<int[]>> out = new ArrayList<>();
        int start = 0;
        for (int k = 1; k + 1 < path.size(); k++) {
            int[] p = path.get(k - 1), c = path.get(k), n = path.get(k + 1);
            if (c[0] - p[0] != n[0] - c[0] || c[1] - p[1] != n[1] - c[1]) {
                out.add(List.of(path.get(start), c));
                start = k;
            }
        }
        out.add(List.of(path.get(start), path.get(path.size() - 1)));
        return out;
    }

    private static int find(int[] root, int a) {
        while (root[a] != a) a = root[a] = root[root[a]];
        return a;
    }

    private static double distance(double px, double py, Point a, Point b) {
        double vx = b.x() - a.x(), vy = b.y() - a.y();
        double len = vx * vx + vy * vy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - a.x()) * vx + (py - a.y()) * vy) / len));
        double dx = px - (a.x() + t * vx), dy = py - (a.y() + t * vy);
        return Math.sqrt(dx * dx + dy * dy);
    }
}
