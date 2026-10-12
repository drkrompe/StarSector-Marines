package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Derives pavement sweeps from the final reserved bands, including added gate links. */
public final class GeneratedRoads {
    private GeneratedRoads() {}

    private record Band(int left, int bottom, int right, int top) {
        int width() { return right - left + 1; }
        int height() { return top - bottom + 1; }
        boolean horizontal() { return width() >= height(); }
        double breadth() { return Math.min(width(), height()); }
        double cx() { return (left + right) * .5; }
        double cy() { return (bottom + top) * .5; }
        boolean contains(Band b) {
            return left <= b.left && right >= b.right && bottom <= b.bottom && top >= b.top;
        }
    }

    /** No RNG consumption; straight service runs plus tangent-controlled junction turns. */
    public static List<Route> routes(boolean[][] road, long seed) {
        if (road.length == 0 || road[0].length == 0) return List.of();
        Set<Band> candidates = new LinkedHashSet<>();
        bands(road, false, candidates);
        bands(road, true, candidates);
        List<Band> bands = candidates.stream().filter(b -> candidates.stream()
                .noneMatch(other -> !other.equals(b) && other.contains(b))).toList();
        List<Route> routes = new ArrayList<>();
        for (Band b : bands) {
            double inset = b.breadth() / 2 - .5;
            Point a = b.horizontal() ? new Point(b.left + inset, b.cy()) : new Point(b.cx(), b.bottom + inset);
            Point z = b.horizontal() ? new Point(b.right - inset, b.cy()) : new Point(b.cx(), b.top - inset);
            // A square is a junction, covered by its through bands. Isolated small
            // squares retain their original paving rather than inventing a road.
            if (!a.equals(z)) add(routes, List.of(a, z), b.breadth(), seed);
        }
        for (Band h : bands) for (Band v : bands) {
            if (!h.horizontal() || v.horizontal() || v.cx() < h.left || v.cx() > h.right
                    || h.cy() < v.bottom || h.cy() > v.top) continue;
            double radius = Math.max(h.breadth(), v.breadth());
            for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                double x = v.cx(), y = h.cy();
                // Both approach arms must actually exist. Never bend a dead end
                // into a phantom street on the other side of a pruned junction.
                if (x + sx * (radius + 1) < h.left || x + sx * (radius + 1) > h.right
                        || y + sy * (radius + 1) < v.bottom || y + sy * (radius + 1) > v.top) continue;
                double mid = radius * (1 - Math.sqrt(.5));
                add(routes, List.of(new Point(x + sx * (radius + 1), y),
                        new Point(x + sx * radius, y), new Point(x + sx * mid, y + sy * mid),
                        new Point(x, y + sy * radius), new Point(x, y + sy * (radius + 1))),
                        Math.min(h.breadth(), v.breadth()), seed);
            }
        }
        return List.copyOf(routes);
    }

    private static void add(List<Route> routes, List<Point> points, double width, long seed) {
        routes.add(new Route("generated-" + routes.size(), points, width, 0, .4, seed, Material.ASPHALT));
    }

    /** Maximal horizontal and vertical rectangles, not the obsolete pre-pruning trunks. */
    private static void bands(boolean[][] road, boolean transpose, Set<Band> out) {
        int length = transpose ? road[0].length : road.length;
        int rows = transpose ? road.length : road[0].length;
        for (int row = 0; row < rows; row++) for (int start = 0; start < length;) {
            if (!at(road, transpose, start, row)) { start++; continue; }
            int end = start;
            while (end + 1 < length && at(road, transpose, end + 1, row)) end++;
            if (end - start >= 2) {
                int lo = row, hi = row;
                while (lo > 0 && full(road, transpose, start, end, lo - 1)) lo--;
                while (hi + 1 < rows && full(road, transpose, start, end, hi + 1)) hi++;
                int breadth = hi - lo + 1;
                if (breadth >= 3 && breadth <= 32 && end - start + 1 >= breadth)
                    out.add(transpose ? new Band(lo, start, hi, end) : new Band(start, lo, end, hi));
            }
            start = end + 1;
        }
    }

    private static boolean full(boolean[][] road, boolean transpose, int start, int end, int row) {
        for (int x = start; x <= end; x++) if (!at(road, transpose, x, row)) return false;
        return true;
    }

    private static boolean at(boolean[][] road, boolean transpose, int x, int y) {
        return transpose ? road[y][x] : road[x][y];
    }
}
