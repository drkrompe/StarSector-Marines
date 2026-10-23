package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a {@link Road} runs, before any ground is touched: its endpoints
 * resolved against claimed bounds and gates, and a dog-leg course between
 * them (out from the first, across at the halfway line, in to the second),
 * brushed to the road's width. Pure and deterministic.
 */
public final class RoadCourse {

    private RoadCourse() {}

    /**
     * The cell just outside {@code side} of the inclusive bounds {x0, y0, x1, y1}, moved by {@code (dx, dy)}; or,
     * where a gate (each a list of its cells) stands on that side's half of the place, the first cell outside the
     * place ({@code inside} says what is the place's) stepping out from the middle of the gate whose middle lies
     * nearest the side's middle, so the road meets the road that made the gate.
     */
    public static int[] point(int[] bounds, Side side, List<List<int[]>> gates, int dx, int dy,
                              LineCourse.CellTest inside) {
        int mx = (bounds[0] + bounds[2]) / 2, my = (bounds[1] + bounds[3]) / 2;
        int[] middle = switch (side) {
            case NORTH -> new int[]{mx, bounds[3]};
            case SOUTH -> new int[]{mx, bounds[1]};
            case EAST -> new int[]{bounds[2], my};
            case WEST -> new int[]{bounds[0], my};
        };
        int[] at = middle;
        long best = Long.MAX_VALUE;
        for (List<int[]> gate : gates) {
            if (gate.isEmpty()) continue;
            long gx = 0, gy = 0;
            for (int[] c : gate) {
                gx += c[0];
                gy += c[1];
            }
            int cx = (int) (gx / gate.size()), cy = (int) (gy / gate.size());
            // Only a gate on this side's half of the place.
            if ((cx - mx) * side.dx + (cy - my) * side.dy <= 0) continue;
            long d = (long) (cx - middle[0]) * (cx - middle[0]) + (long) (cy - middle[1]) * (cy - middle[1]);
            if (d < best) {
                best = d;
                at = new int[]{cx, cy};
            }
        }
        if (at == middle) return new int[]{at[0] + side.dx + dx, at[1] + side.dy + dy};
        int x = at[0], y = at[1];
        for (int k = 0; k <= bounds[2] - bounds[0] + bounds[3] - bounds[1] && inside.test(x, y); k++) {
            x += side.dx;
            y += side.dy;
        }
        return new int[]{x + dx, y + dy};
    }

    /**
     * The course from {@code a} (leaving along {@code out}) to {@code b}: straight out to the halfway line between
     * them, across it, and on to {@code b}. Vertices, first and last the endpoints.
     */
    public static List<int[]> path(int[] a, Side out, int[] b) {
        List<int[]> vertices = new ArrayList<>();
        vertices.add(a);
        if (out.dy != 0) {
            int mid = (a[1] + b[1]) / 2;
            vertices.add(new int[]{a[0], mid});
            vertices.add(new int[]{b[0], mid});
        } else {
            int mid = (a[0] + b[0]) / 2;
            vertices.add(new int[]{mid, a[1]});
            vertices.add(new int[]{mid, b[1]});
        }
        vertices.add(b);
        return vertices;
    }

    /** The cells of the course brushed {@code width} wide (a square brush), each once, in order along it. */
    public static List<int[]> cells(List<int[]> path, int width) {
        int lo = -(width - 1) / 2, hi = width / 2;
        Set<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>();
        for (int[] c : LineCourse.raster(path)) {
            for (int dx = lo; dx <= hi; dx++) {
                for (int dy = lo; dy <= hi; dy++) {
                    int x = c[0] + dx, y = c[1] + dy;
                    if (seen.add((long) x << 32 | (y & 0xffffffffL))) out.add(new int[]{x, y});
                }
            }
        }
        return out;
    }
}
