package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface.Marking;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        double length() { return Math.max(width(), height()); }
        boolean holds(boolean[][] cells) {
            for (int x = left; x <= right; x++) for (int y = bottom; y <= top; y++) if (cells[x][y]) return true;
            return false;
        }
        /** Runs the same way over all of {@code b}'s length and overlaps it, narrower. */
        boolean alongside(Band b) {
            if (horizontal() != b.horizontal() || breadth() >= b.breadth()) return false;
            return horizontal()
                    ? left <= b.left && right >= b.right && bottom <= b.top && top >= b.bottom
                    : bottom <= b.bottom && top >= b.top && left <= b.right && right >= b.left;
        }
        /** Whether the first (or last) cell line of this band meets another band or the map's edge. */
        boolean endMet(boolean first, List<Band> others, int mapW, int mapH) {
            Band end = horizontal()
                    ? (first ? new Band(left, bottom, left, top) : new Band(right, bottom, right, top))
                    : (first ? new Band(left, bottom, right, bottom) : new Band(left, top, right, top));
            if (end.left == 0 || end.bottom == 0 || end.right == mapW - 1 || end.top == mapH - 1) return true;
            for (Band o : others) if (o != this && o.horizontal() != horizontal() && o.touches(end)) return true;
            return false;
        }
        /** Overlaps or shares an edge with {@code b}. */
        boolean touches(Band b) {
            return left <= b.right + 1 && right >= b.left - 1 && bottom <= b.top + 1 && top >= b.bottom - 1;
        }
    }

    /** The widest band a sweep may take; a route cannot be wider. */
    public static final int MAX_BREADTH = 32;
    /** A street band at least this many breadths long stands on its own; a shorter one must meet another. */
    static final int PILL_LENGTH = 4;

    /** No RNG consumption; straight service runs plus tangent-controlled junction turns. */
    public static List<Route> routes(boolean[][] road, long seed) {
        return routes(road, seed, MAX_BREADTH);
    }

    /**
     * As {@link #routes(boolean[][], long)}, refusing any band broader than
     * {@code maxBreadth}: open ground wider than a street keeps its own paving
     * rather than becoming a square of carriageway.
     */
    public static List<Route> routes(boolean[][] road, long seed, int maxBreadth) {
        return routes(road, null, null, seed, maxBreadth, StreetStyle.ROUND_BARE).routes();
    }

    /**
     * How a city street is finished. {@code squareEnds} ends each street flush
     * with the band it was found in instead of with a round brush end;
     * {@code sidewalks} keeps room for a sidewalk on a side with no footway
     * beside the band (the sidewalk itself is ground, laid by the caller);
     * {@code markings} paints a dashed centre line down each street, broken
     * at every junction and short of each end.
     */
    public record StreetStyle(boolean squareEnds, boolean sidewalks, boolean markings) {
        public static final StreetStyle ROUND_BARE = new StreetStyle(false, false, false);
    }

    /** A swept network: its pavement routes, asphalt before concrete, and the paint laid on them. */
    public record Network(List<Route> routes, List<Marking> markings) {}

    /** A centre-line dash and the gap after it, in cells, and the paint's width. */
    static final double DASH = 1.5, GAP = 1.5, LINE_WIDTH = .14;

    /**
     * A city's street network, whose carriageway is the street less its kerbs
     * and so widens wherever a frontage gives way to a yard. A band holding any
     * {@code city} cell is a street and must also be no broader than
     * {@code maxBreadth}; two more are refused: a <em>bulge</em>, broader than a
     * band running the same way along its whole length, which would swell the
     * road where the kerb steps back; and a <em>pill</em>, a short band not met
     * by a crossing street at both ends, which would float a capsule of asphalt
     * in a gap between buildings or swell a junction into a yard. Both keep
     * their paving. Bands of {@code road} alone are swept as
     * {@link #routes(boolean[][], long)} sweeps them.
     *
     * <p>A street's sidewalk lies on the {@code footway} beside its band where
     * there is one (the kerb, a boulevard's flank, a yard's paving). On a side
     * with none, grass for instance, the asphalt gives up the band's outer cell
     * to it, so long as three cells of asphalt remain; a narrower street goes
     * without on that side.
     */
    public static Network streets(boolean[][] road, boolean[][] city, boolean[][] footway,
                                  long seed, int maxBreadth, StreetStyle style) {
        return routes(road, city, footway, seed, maxBreadth, style);
    }

    private static Network routes(boolean[][] road, boolean[][] city, boolean[][] footway,
                                  long seed, int maxBreadth, StreetStyle style) {
        if (road.length == 0 || road[0].length == 0) return new Network(List.of(), List.of());
        Set<Band> candidates = new LinkedHashSet<>();
        int cap = city == null ? Math.min(maxBreadth, MAX_BREADTH) : MAX_BREADTH;
        bands(road, false, cap, candidates);
        bands(road, true, cap, candidates);
        if (city != null) candidates.removeIf(b -> b.breadth() > maxBreadth && b.holds(city));
        List<Band> bands = candidates.stream().filter(b -> candidates.stream()
                .noneMatch(other -> !other.equals(b) && other.contains(b))).toList();
        if (city != null) {
            List<Band> all = bands;
            bands = all.stream().filter(b -> !b.holds(city) || all.stream().noneMatch(a -> a.alongside(b))).toList();
            // A short band is a street only when each of its ends meets another
            // street; a stub off one road, or across it, opens into a yard, and
            // its turns would swell into the yard.
            List<Band> kept = bands;
            int w = road.length, h = road[0].length;
            bands = kept.stream().filter(b -> !b.holds(city) || b.length() >= PILL_LENGTH * b.breadth()
                    || (b.endMet(true, kept, w, h) && b.endMet(false, kept, w, h))).toList();
        }
        List<Route> routes = new ArrayList<>();
        Map<Band, Carriageway> lanes = new HashMap<>();
        for (Band b : bands) {
            boolean street = city != null && b.holds(city);
            Carriageway c = street ? Carriageway.of(b, footway, style.sidewalks()) : Carriageway.whole(b);
            lanes.put(b, c);
            boolean square = street && style.squareEnds();
            double inset = square ? -.5 : c.width() / 2 - .5;
            Point a = b.horizontal() ? new Point(b.left + inset, c.centre()) : new Point(c.centre(), b.bottom + inset);
            Point z = b.horizontal() ? new Point(b.right - inset, c.centre()) : new Point(c.centre(), b.top - inset);
            // A square is a junction, covered by its through bands. Isolated small
            // squares retain their original paving rather than inventing a road.
            if (a.equals(z) || b.width() == b.height()) continue;
            EndCap end = square ? EndCap.BUTT : EndCap.ROUND;
            add(routes, List.of(a, z), c.width(), seed, Material.ASPHALT, end);
        }
        for (Band h : bands) for (Band v : bands) {
            if (!h.horizontal() || v.horizontal() || v.cx() < h.left || v.cx() > h.right
                    || h.cy() < v.bottom || h.cy() > v.top) continue;
            Carriageway ch = lanes.get(h), cv = lanes.get(v);
            double radius = Math.max(ch.width(), cv.width());
            for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                double x = cv.centre(), y = ch.centre();
                // Both approach arms must actually exist. Never bend a dead end
                // into a phantom street on the other side of a pruned junction.
                if (x + sx * (radius + 1) < h.left || x + sx * (radius + 1) > h.right
                        || y + sy * (radius + 1) < v.bottom || y + sy * (radius + 1) > v.top) continue;
                double mid = radius * (1 - Math.sqrt(.5));
                var turn = List.of(new Point(x + sx * (radius + 1), y),
                        new Point(x + sx * radius, y), new Point(x + sx * mid, y + sy * mid),
                        new Point(x, y + sy * radius), new Point(x, y + sy * (radius + 1)));
                double width = Math.min(ch.width(), cv.width());
                add(routes, turn, width, seed, Material.ASPHALT, EndCap.ROUND);
            }
        }
        List<Marking> paint = new ArrayList<>();
        if (city != null && style.markings())
            for (Band b : bands) if (b.holds(city) && b.width() != b.height()) centreLine(b, lanes.get(b), bands, paint);
        return new Network(List.copyOf(routes), List.copyOf(paint));
    }

    /**
     * Dashes down the middle of a street's asphalt, kept a cell clear of each
     * end and of every crossing street, so a junction is left unpainted. Where
     * two bands running the same way overlap, only the broader (then the
     * longer) one is painted there, so one carriageway carries one line.
     */
    private static void centreLine(Band b, Carriageway c, List<Band> bands, List<Marking> out) {
        boolean horizontal = b.horizontal();
        double from = (horizontal ? b.left : b.bottom) + .5, to = (horizontal ? b.right : b.top) - .5;
        List<double[]> clear = new ArrayList<>();
        for (Band o : bands) {
            if (o == b || o.left > b.right || o.right < b.left || o.bottom > b.top || o.top < b.bottom) continue;
            if (o.horizontal() != horizontal)
                clear.add(horizontal ? new double[]{o.left - 1.5, o.right + 1.5} : new double[]{o.bottom - 1.5, o.top + 1.5});
            else if (o.breadth() > b.breadth() || (o.breadth() == b.breadth() && o.length() > b.length()))
                clear.add(horizontal ? new double[]{o.left - 1, o.right + 1} : new double[]{o.bottom - 1, o.top + 1});
        }
        clear.sort((p, q) -> Double.compare(p[0], q[0]));
        double at = from;
        for (int i = 0; i <= clear.size(); i++) {
            double end = i < clear.size() ? clear.get(i)[0] : to;
            for (double dash = at + .25; dash + DASH <= end; dash += DASH + GAP) {
                Point a = horizontal ? new Point(dash, c.centre()) : new Point(c.centre(), dash);
                Point z = horizontal ? new Point(dash + DASH, c.centre()) : new Point(c.centre(), dash + DASH);
                out.add(new Marking(a, z, LINE_WIDTH));
            }
            if (i < clear.size()) at = Math.max(at, clear.get(i)[1]);
        }
    }

    /** The asphalt across a band: its centre line across the band's axis, and its width. */
    private record Carriageway(double centre, double width) {
        static Carriageway whole(Band b) {
            return new Carriageway(b.horizontal() ? b.cy() : b.cx(), b.breadth());
        }

        /** Give a side's outer cell to the sidewalk where no footway lies beyond it. */
        static Carriageway of(Band b, boolean[][] footway, boolean sidewalks) {
            if (!sidewalks || footway == null) return whole(b);
            int lo = b.horizontal() ? b.bottom : b.left, hi = b.horizontal() ? b.top : b.right;
            int innerLo = lo + (footed(b, footway, lo - 1) ? 0 : 1);
            int innerHi = hi - (footed(b, footway, hi + 1) ? 0 : 1);
            if (innerHi - innerLo + 1 < 3) return whole(b);
            return new Carriageway((innerLo + innerHi) * .5, innerHi - innerLo + 1);
        }

        /** Whether at least half the cell line {@code line} beside the band is footway. */
        private static boolean footed(Band b, boolean[][] footway, int line) {
            int w = footway.length, h = footway[0].length, cells = 0, along = 0;
            int from = b.horizontal() ? b.left : b.bottom, to = b.horizontal() ? b.right : b.top;
            for (int i = from; i <= to; i++, along++) {
                int x = b.horizontal() ? i : line, y = b.horizontal() ? line : i;
                if (x >= 0 && y >= 0 && x < w && y < h && footway[x][y]) cells++;
            }
            return cells * 2 >= along;
        }
    }

    private static void add(List<Route> routes, List<Point> points, double width, long seed,
                            Material material, EndCap cap) {
        routes.add(new Route("generated-" + routes.size(), points, width, 0, .4, seed, material, cap));
    }

    /** Maximal horizontal and vertical rectangles, not the obsolete pre-pruning trunks. */
    private static void bands(boolean[][] road, boolean transpose, int maxBreadth, Set<Band> out) {
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
                if (breadth >= 3 && breadth <= maxBreadth && end - start + 1 >= breadth)
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
