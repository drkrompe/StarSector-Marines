package com.dillon.starsectormarines.battle.world.gen.elevation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * The raised ore conveyor on the map's grid ({@code voids.md}, the open pit): from the transfer tower on a pit's rim to
 * the works it feeds, straight runs that turn only at right angles, raised on trestles over the ground so people walk
 * under it, with a trestle leg every few cells that stands as a solid prop and gives cover. GL-free: it finds the
 * course and its pieces; the landscape pass places them.
 *
 * <p>Sides are the art's: a piece's belt runs east in its frame, and one quarter turn carries east to north, north to
 * west (the turn {@code Doodad} gives a placed prop). {@code north} is the map's {@code +y} when {@code yUpIsNorth}.
 */
public final class OreLine {

    /** What one cell of the line is. */
    public enum Shape { STRAIGHT, CORNER, DRIVE }

    /** A piece of the line at a cell: its shape and the quarter turns its art is placed at. */
    public record Piece(int x, int y, Shape shape, int quarterTurns) { }

    /** Cost of one turn on the course, in cells: a long straight run is cheaper than a stair of corners. UNTUNED. */
    static final int TURN_COST = 8;
    /** Cells between trestle legs along a straight run. UNTUNED. */
    public static final int LEG_EVERY = 5;

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};

    private OreLine() { }

    /**
     * The cheapest course of four-connected cells from {@code (sx, sy)} to any {@code goal} cell, over cells the line
     * may cross ({@code passable}: open ground, roads and walls, never a building or the pit), paying
     * {@link #TURN_COST} for every turn; empty when none reaches a goal.
     */
    public static List<int[]> route(int w, int h, int sx, int sy, IntPredicate passable, IntPredicate goal) {
        if (sx < 0 || sy < 0 || sx >= w || sy >= h || !passable.test(sy * w + sx)) return List.of();
        int[] cost = new int[w * h * 4];
        int[] from = new int[w * h * 4];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(from, -1);
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        for (int d = 0; d < 4; d++) {
            int s = (sy * w + sx) * 4 + d;
            cost[s] = 0;
            open.add(new int[]{0, s});
        }
        int end = -1;
        while (!open.isEmpty()) {
            int[] top = open.poll();
            int state = top[1];
            if (top[0] > cost[state]) continue;
            int cell = state / 4, dir = state % 4;
            if (goal.test(cell)) { end = state; break; }
            int x = cell % w, y = cell / w;
            for (int d = 0; d < 4; d++) {
                if (d == (dir + 2) % 4) continue;
                int nx = x + DX[d], ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (!passable.test(n)) continue;
                int c = top[0] + 1 + (d == dir ? 0 : TURN_COST);
                int ns = n * 4 + d;
                if (c >= cost[ns]) continue;
                cost[ns] = c;
                from[ns] = state;
                open.add(new int[]{c, ns});
            }
        }
        if (end < 0) return List.of();
        List<int[]> path = new ArrayList<>();
        for (int s = end; s >= 0; s = from[s]) {
            int cell = s / 4;
            if (path.isEmpty() || path.get(0)[0] != cell % w || path.get(0)[1] != cell / w) {
                path.add(0, new int[]{cell % w, cell / w});
            }
        }
        return path;
    }

    /**
     * The pieces down a course: a straight piece turned to the way the ore flows, a corner where the course turns,
     * and a drive at its last cell facing the building it feeds at {@code (intoX, intoY)}, the next cell on.
     */
    public static List<Piece> pieces(List<int[]> path, int intoX, int intoY, boolean yUpIsNorth) {
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            int[] at = path.get(i);
            int[] prev = i > 0 ? path.get(i - 1) : null;
            int[] next = i + 1 < path.size() ? path.get(i + 1) : new int[]{intoX, intoY};
            int out0 = side(next[0] - at[0], next[1] - at[1], yUpIsNorth);
            if (i == path.size() - 1) {
                out.add(new Piece(at[0], at[1], Shape.DRIVE, out0));
                continue;
            }
            int in = prev == null ? (out0 + 2) % 4 : side(prev[0] - at[0], prev[1] - at[1], yUpIsNorth);
            if ((in + 2) % 4 == out0) {
                out.add(new Piece(at[0], at[1], Shape.STRAIGHT, out0));
            } else {
                out.add(new Piece(at[0], at[1], Shape.CORNER, cornerTurns(in, out0)));
            }
        }
        return out;
    }

    /**
     * Where the trestle legs stand: every {@link #LEG_EVERY} cells along the course, on a straight piece whose cell
     * {@code canStand} (ground a solid leg may take without blocking a road, a doorway or a route), the nearest such
     * cell within two either way when the due one may not.
     */
    public static List<int[]> legs(List<Piece> pieces, IntPredicate canStand, int w) {
        List<int[]> out = new ArrayList<>();
        int last = -LEG_EVERY;
        for (int i = 1; i < pieces.size() - 1; i++) {
            if (i - last < LEG_EVERY) continue;
            for (int k : new int[]{0, 1, -1, 2, -2}) {
                int j = i + k;
                if (j <= 0 || j >= pieces.size() - 1 || j - last < LEG_EVERY - 2) continue;
                Piece p = pieces.get(j);
                if (p.shape() != Shape.STRAIGHT || !canStand.test(p.y() * w + p.x())) continue;
                out.add(new int[]{p.x(), p.y()});
                last = j;
                break;
            }
        }
        return out;
    }

    /** The art side (0 east, 1 north, 2 west, 3 south) a step of {@code (dx, dy)} on the map leaves by. */
    static int side(int dx, int dy, boolean yUpIsNorth) {
        if (dx > 0) return 0;
        if (dx < 0) return 2;
        boolean north = yUpIsNorth ? dy > 0 : dy < 0;
        return north ? 1 : 3;
    }

    /**
     * The quarter turns that carry the corner's art, open west and north, to open on sides {@code a} and {@code b}:
     * turned once it opens south and west, twice east and south, three times north and east.
     */
    static int cornerTurns(int a, int b) {
        for (int q = 0; q < 4; q++) {
            int w = (2 + q) % 4, n = (1 + q) % 4;
            if ((a == w && b == n) || (a == n && b == w)) return q;
        }
        throw new IllegalArgumentException("not a corner: sides " + a + " and " + b);
    }
}
