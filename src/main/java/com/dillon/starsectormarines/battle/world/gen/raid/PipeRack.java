package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * A refinery's pipe rack ({@code RefiningWorks}): pipes carried overhead on gantries, the way crude comes in from the
 * fields, stock goes between the units and product goes out to the tank farm. GL-free: it finds the course and its
 * pieces; {@link RefineryGround} lays them.
 *
 * <p>A rack runs in straight lines that turn only at right angles, as a real rack does. It prefers open ground beside a
 * road to the road itself, and crosses a road square on where it must: the pipe bridge over the haul road is a
 * refinery's tell. People walk under it. A gantry column stands every {@link #BAY} cells, and at each turn, where a
 * column may stand (off the roads, doorways and ground a column would strand); a column is a fixture and gives cover.
 */
public final class PipeRack {

    /** What one cell of the rack is: a run of pipe, or a gantry with its beam. */
    public enum Kind { RUN, GANTRY }

    /** A cell of the rack: what it is and whether its pipes lie along the map's x. */
    public record Piece(int x, int y, Kind kind, boolean alongX) { }

    /** Cells between gantry columns along a straight run. UNTUNED. */
    public static final int BAY = 6;
    /** Cost of a turn, in cells: a rack is laid in long straight runs. UNTUNED. */
    static final int TURN_COST = 10;

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};

    private PipeRack() { }

    /**
     * The cheapest four-connected course from {@code (sx, sy)} to any {@code goal} cell. Each step onto a cell costs
     * one plus {@code extra} of that cell, and a cell whose extra is negative may not be crossed; every turn costs
     * {@link #TURN_COST}. Empty when no goal is reached.
     */
    public static List<int[]> route(int w, int h, int sx, int sy, IntUnaryOperator extra, IntPredicate goal) {
        if (sx < 0 || sy < 0 || sx >= w || sy >= h || extra.applyAsInt(sy * w + sx) < 0) return List.of();
        int[] cost = new int[w * h * 4];
        int[] from = new int[w * h * 4];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(from, -1);
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        for (int d = 0; d < 4; d++) {
            cost[(sy * w + sx) * 4 + d] = 0;
            open.add(new int[]{0, (sy * w + sx) * 4 + d});
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
                int e = extra.applyAsInt(n);
                if (e < 0) continue;
                int c = top[0] + 1 + e + (d == dir ? 0 : TURN_COST);
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
     * The course beside {@code path}, one cell to its left as it runs: a second rack on the same gantries, so a trunk
     * line reads as the pipe alley it is. At a turn it takes both the cell beside the run in and the one beside the run
     * out, so it stays four-connected. Empty when the course is too short to have a side.
     */
    public static List<int[]> twin(List<int[]> path) {
        List<int[]> out = new ArrayList<>();
        int n = path.size();
        if (n < 2) return out;
        for (int i = 0; i < n; i++) {
            int[] at = path.get(i);
            int[] in = i > 0 ? step(path.get(i - 1), at) : step(at, path.get(i + 1));
            int[] next = i + 1 < n ? step(at, path.get(i + 1)) : in;
            // Left of a step (dx, dy) is (-dy, dx).
            addIfNew(out, path, at[0] - in[1], at[1] + in[0]);
            if (next[0] != in[0] || next[1] != in[1]) addIfNew(out, path, at[0] - next[1], at[1] + next[0]);
        }
        return out;
    }

    private static int[] step(int[] a, int[] b) { return new int[]{b[0] - a[0], b[1] - a[1]}; }

    private static void addIfNew(List<int[]> out, List<int[]> path, int x, int y) {
        for (int[] p : path) if (p[0] == x && p[1] == y) return;
        if (!out.isEmpty()) {
            int[] last = out.get(out.size() - 1);
            if (last[0] == x && last[1] == y) return;
        }
        out.add(new int[]{x, y});
    }

    /**
     * The pieces down a course: a gantry at each end, at each turn, and every {@link #BAY} cells along a straight run
     * where {@code canStand} (the nearest such cell within two either way when the due one may not), a run of pipe
     * everywhere else. A turn or end where no column may stand is still a gantry's beam, carried by the run.
     */
    public static List<Piece> pieces(List<int[]> path, IntPredicate canStand, int w) {
        List<Piece> out = new ArrayList<>();
        int n = path.size();
        if (n == 0) return out;
        boolean[] gantry = new boolean[n];
        boolean[] along = new boolean[n];
        for (int i = 0; i < n; i++) {
            int[] a = path.get(Math.max(0, i - 1)), b = path.get(Math.min(n - 1, i + 1));
            if (i > 0 && i < n - 1) {
                int[] p = path.get(i - 1), q = path.get(i + 1);
                boolean turn = p[0] != q[0] && p[1] != q[1];
                if (turn) gantry[i] = true;
                int[] next = path.get(i + 1), at = path.get(i);
                along[i] = next[1] == at[1];
            } else {
                along[i] = a[1] == b[1];
            }
        }
        gantry[0] = true;
        gantry[n - 1] = true;
        int last = 0;
        for (int i = 1; i < n - 1; i++) {
            if (gantry[i]) { last = i; continue; }
            if (i - last < BAY) continue;
            for (int k : new int[]{0, 1, -1, 2, -2}) {
                int j = i + k;
                if (j <= last || j >= n - 1 || gantry[j]) continue;
                int[] c = path.get(j);
                if (!canStand.test(c[1] * w + c[0])) continue;
                gantry[j] = true;
                last = j;
                break;
            }
        }
        for (int i = 0; i < n; i++) {
            int[] c = path.get(i);
            out.add(new Piece(c[0], c[1], gantry[i] ? Kind.GANTRY : Kind.RUN, along[i]));
        }
        return out;
    }
}
