package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The fort's outer belt, authored in the {@link BatteryFort} frame: an irregular ring of trench and sandbag line with
 * wire outside it and nests on it, standing off the main works across open ground. Pure geometry; the stage decides
 * which of these cells the ground will actually take and how each is drawn.
 *
 * <p>The trace is a hand-authored polygon, not a rectangle: it has salients and re-entrants so that no stretch of the
 * belt can be enfiladed from end to end, and the stage drops whatever cells fall on ground the city already holds, so
 * a belt follows the land it is given. The belt opens in front of each gate, where the approach lane crosses it.
 */
public final class FortBelt {

    /** What a belt cell is. */
    public enum Role {
        /** Open trench floor, walkable. */
        TRENCH,
        /** A sandbag piece (walkable, heavy cover) facing {@link Cell#dx()}, {@link Cell#dy()}. */
        BAG,
        /** Trench spoil or a firing step behind the trench, walkable earth. */
        EARTH,
        /** A nest's own cell; the stage seals it and stands a turret there. */
        NEST,
        /** A cell of wire, closed to movement. */
        WIRE
    }

    /**
     * @param dx,dy the outward direction for a bag or the run direction for wire (1,0 horizontal or 0,1 vertical), else 0
     * @param corner for a bag at a pit corner, the second outward component, else 0
     */
    public record Cell(int x, int y, Role role, int dx, int dy, int cornerDx, int cornerDy) {
        Cell(int x, int y, Role role, int dx, int dy) {
            this(x, y, role, dx, dy, 0, 0);
        }
    }

    private static final int[][] VERTICES = {
            {13, 4}, {26, 5}, {33, 9}, {48, 3}, {60, 3}, {70, 3}, {77, 13},
            {77, 25}, {78, 33}, {76, 40}, {77, 50}, {76, 62},
            {62, 66}, {50, 65}, {40, 68}, {27, 65}, {18, 67},
            {4, 62}, {3, 48}, {5, 40}, {4, 32}, {3, 21}, {6, 11}};

    /** Vertices that carry a nest, as indices into the trace. */
    private static final int[] NEST_VERTICES = {4, 11, 14, 19};

    private static final int PERIOD = 29;
    private static final int WIRE_DISTANCE = 4;

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final List<Cell> CELLS = build();

    private FortBelt() {}

    /** Every belt cell in drawing order; later cells never overlap earlier ones. */
    public static List<Cell> cells() {
        return CELLS;
    }

    /** The nest cells of the belt, in the footprint frame. */
    public static List<int[]> nests() {
        List<int[]> out = new ArrayList<>();
        for (Cell c : CELLS) if (c.role() == Role.NEST) out.add(new int[]{c.x(), c.y()});
        return out;
    }

    private static List<Cell> build() {
        int w = BatteryFort.WIDTH, h = BatteryFort.HEIGHT;
        List<int[]> path = trace();
        int[][] pathIndex = new int[w][h];
        for (int[] row : pathIndex) Arrays.fill(row, -1);
        for (int i = 0; i < path.size(); i++) pathIndex[path.get(i)[0]][path.get(i)[1]] = i;

        Cell[][] grid = new Cell[w][h];

        // The trench and sandbag line, run by run along the trace.
        for (int i = 0; i < path.size(); i++) {
            int x = path.get(i)[0], y = path.get(i)[1];
            if (inLane(x, y, 3)) continue;
            int s = i % PERIOD;
            if (s >= 12 && s < 17) {
                int[] out = outward(x, y, pathIndex);
                put(grid, new Cell(x, y, Role.BAG, out[0], out[1]));
                continue;
            }
            put(grid, new Cell(x, y, Role.TRENCH, 0, 0));
            if (s == 6 || s == 22) continue;
            for (int[] d : STEPS) {
                int nx = x + d[0], ny = y + d[1];
                if (!inside(nx, ny, w, h) || pathIndex[nx][ny] >= 0 || inLane(nx, ny, 3)) continue;
                // The trench's own parapet is part of its art; behind it the spoil and firing steps are bare earth.
                if (polygonContains(nx, ny) && !BatteryFort.isCleared(nx, ny) && (s & 3) < 2) put(grid, new Cell(nx, ny, Role.EARTH, 0, 0));
            }
        }
        // Traverses: a sandbag block across the trench that breaks the line of fire along it.
        for (int i = 0; i < path.size(); i++) {
            int x = path.get(i)[0], y = path.get(i)[1];
            int s = i % PERIOD;
            if ((s == 6 || s == 22) && !inLane(x, y, 3) && grid[x][y] != null && grid[x][y].role() == Role.TRENCH) {
                int[] out = outward(x, y, pathIndex);
                grid[x][y] = new Cell(x, y, Role.BAG, out[0] == 0 ? 1 : 0, out[0] == 0 ? 0 : 1);
            }
        }

        // Nests on the trace, each in a horseshoe pit open to the works.
        for (int v : NEST_VERTICES) pit(grid, VERTICES[v][0], VERTICES[v][1]);

        // Wire: a ring four cells outside the trace, in runs with gaps, never across a gate lane.
        int[][] dist = new int[w][h];
        int[][] source = new int[w][h];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int i = 0; i < path.size(); i++) {
            int x = path.get(i)[0], y = path.get(i)[1];
            dist[x][y] = 0;
            source[x][y] = i;
            open.add(new int[]{x, y});
        }
        while (!open.isEmpty()) {
            int[] at = open.poll();
            if (dist[at[0]][at[1]] >= WIRE_DISTANCE) continue;
            for (int[] d : STEPS) {
                int nx = at[0] + d[0], ny = at[1] + d[1];
                if (!inside(nx, ny, w, h) || dist[nx][ny] >= 0 || polygonContains(nx, ny)) continue;
                dist[nx][ny] = dist[at[0]][at[1]] + 1;
                source[nx][ny] = source[at[0]][at[1]];
                open.add(new int[]{nx, ny});
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (dist[x][y] != WIRE_DISTANCE || grid[x][y] != null || inLane(x, y, 4)) continue;
                if (source[x][y] % 17 >= 12) continue;
                boolean horizontal = dist[Math.min(w - 1, x + 1)][y] == WIRE_DISTANCE
                        || (x > 0 && dist[x - 1][y] == WIRE_DISTANCE);
                grid[x][y] = new Cell(x, y, Role.WIRE, horizontal ? 1 : 0, horizontal ? 0 : 1);
            }
        }

        List<Cell> out = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) if (grid[x][y] != null) out.add(grid[x][y]);
        return List.copyOf(out);
    }

    private static void put(Cell[][] grid, Cell cell) {
        Cell old = grid[cell.x()][cell.y()];
        if (old != null && rank(old.role()) >= rank(cell.role())) return;
        grid[cell.x()][cell.y()] = cell;
    }

    private static int rank(Role role) {
        return switch (role) {
            case NEST -> 5;
            case TRENCH -> 4;
            case BAG -> 3;
            case EARTH -> 2;
            case WIRE -> 1;
        };
    }

    private static boolean inside(int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /** Whether the cell lies in the approach lane of a gate: the belt and wire stay open there. */
    private static boolean inLane(int x, int y, int half) {
        int mainY = BatteryFort.OY + 17;
        int posternX = BatteryFort.OX + 23;
        if (x < BatteryFort.OX && Math.abs(y - mainY) <= half) return true;
        return y < BatteryFort.OY && Math.abs(x - posternX) <= half;
    }

    /** The first 4-neighbour direction from the trace cell that leaves the polygon. */
    private static int[] outward(int x, int y, int[][] pathIndex) {
        for (int[] d : STEPS) {
            int nx = x + d[0], ny = y + d[1];
            if (inside(nx, ny, BatteryFort.WIDTH, BatteryFort.HEIGHT) && pathIndex[nx][ny] < 0
                    && !polygonContains(nx, ny)) return d;
        }
        return new int[]{0, 1};
    }

    private static void pit(Cell[][] grid, int cx, int cy) {
        int wx = BatteryFort.OX + BatteryFort.WORKS_W / 2 - cx;
        int wy = BatteryFort.OY + BatteryFort.WORKS_H / 2 - cy;
        int inX = Math.abs(wx) >= Math.abs(wy) ? Integer.signum(wx) : 0;
        int inY = inX == 0 ? Integer.signum(wy) : 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int x = cx + dx, y = cy + dy;
                if (!inside(x, y, BatteryFort.WIDTH, BatteryFort.HEIGHT)) continue;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dy) == 2;
                if (!ring) {
                    grid[x][y] = dx == 0 && dy == 0 ? new Cell(x, y, Role.NEST, 0, 0) : new Cell(x, y, Role.EARTH, 0, 0);
                    continue;
                }
                // The side facing the works is left open, three wide.
                boolean mouth = (inX != 0 && dx == 2 * inX && Math.abs(dy) <= 1)
                        || (inY != 0 && dy == 2 * inY && Math.abs(dx) <= 1);
                if (mouth) {
                    grid[x][y] = new Cell(x, y, Role.EARTH, 0, 0);
                    continue;
                }
                int ox = Math.abs(dx) == 2 ? Integer.signum(dx) : 0;
                int oy = Math.abs(dy) == 2 ? Integer.signum(dy) : 0;
                if (ox != 0 && oy != 0) {
                    grid[x][y] = new Cell(x, y, Role.BAG, ox, 0, 0, oy);
                } else {
                    grid[x][y] = new Cell(x, y, Role.BAG, ox, oy);
                }
            }
        }
    }

    /** The trace's cells in order, edge by edge, with consecutive repeats removed. */
    private static List<int[]> trace() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < VERTICES.length; i++) {
            int[] a = VERTICES[i];
            int[] b = VERTICES[(i + 1) % VERTICES.length];
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int s = 0; s < steps; s++) {
                int x = a[0] + Math.round((float) (b[0] - a[0]) * s / steps);
                int y = a[1] + Math.round((float) (b[1] - a[1]) * s / steps);
                int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
                if (last != null && last[0] == x && last[1] == y) continue;
                // Keep the trace 4-connected so the trench is one unbroken run.
                if (last != null && last[0] != x && last[1] != y) out.add(new int[]{x, last[1]});
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    private static boolean polygonContains(int px, int py) {
        boolean in = false;
        for (int i = 0, j = VERTICES.length - 1; i < VERTICES.length; j = i++) {
            double xi = VERTICES[i][0], yi = VERTICES[i][1], xj = VERTICES[j][0], yj = VERTICES[j][1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
}
