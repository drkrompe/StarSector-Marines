package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface.Marking;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
            Band end = end(first);
            if (end.left == 0 || end.bottom == 0 || end.right == mapW - 1 || end.top == mapH - 1) return true;
            return crossedAt(first, others);
        }
        /** Whether the first (or last) cell line of this band meets a crossing band of {@code others}. */
        boolean crossedAt(boolean first, List<Band> others) {
            Band end = end(first);
            for (Band o : others) if (o != this && o.horizontal() != horizontal() && o.touches(end)) return true;
            return false;
        }
        /** Whether a band of {@code others} covers the cell line just past this band's first (or last) end. */
        boolean carriedOn(boolean first, List<Band> others) {
            Band end = end(first);
            int step = first ? -1 : 1;
            Band past = horizontal()
                    ? new Band(end.left + step, end.bottom, end.right + step, end.top)
                    : new Band(end.left, end.bottom + step, end.right, end.top + step);
            for (Band o : others) {
                if (o != this && o.left <= past.right && o.right >= past.left && o.bottom <= past.top
                        && o.top >= past.bottom) return true;
            }
            return false;
        }
        /** Whether an end of this band runs on into another band, or stops where a crossing band meets it. */
        boolean joined(boolean first, List<Band> others) {
            return carriedOn(first, others) || crossedAt(first, others);
        }
        /**
         * This band less each stretch of its length, longer than it is broad, that a band of
         * {@code others} running the same way overlaps: the pieces between, each longer than it is
         * broad. A shorter overlap is where a jog steps off the stretch before, and stays.
         */
        List<Band> clearOf(Set<Band> others) {
            boolean horizontal = horizontal();
            int lo = horizontal ? left : bottom, hi = horizontal ? right : top;
            boolean[] cut = new boolean[hi - lo + 1];
            for (Band o : others) {
                if (o.horizontal() != horizontal || o.left > right || o.right < left
                        || o.bottom > top || o.top < bottom) continue;
                int from = Math.max(lo, horizontal ? o.left : o.bottom), to = Math.min(hi, horizontal ? o.right : o.top);
                if (to - from + 1 <= breadth()) continue;
                for (int i = from; i <= to; i++) cut[i - lo] = true;
            }
            List<Band> pieces = new ArrayList<>();
            for (int a = lo; a <= hi;) {
                if (cut[a - lo]) { a++; continue; }
                int b = a;
                while (b + 1 <= hi && !cut[b + 1 - lo]) b++;
                if (b - a + 1 > breadth())
                    pieces.add(horizontal ? new Band(a, bottom, b, top) : new Band(left, a, right, b));
                a = b + 1;
            }
            return pieces;
        }
        private Band end(boolean first) {
            return horizontal()
                    ? (first ? new Band(left, bottom, left, top) : new Band(right, bottom, right, top))
                    : (first ? new Band(left, bottom, right, bottom) : new Band(left, top, right, top));
        }
        /** Cells of this band's length that no crossing band of {@code others} covers. */
        int reach(List<Band> others) {
            boolean horizontal = horizontal();
            int lo = horizontal ? left : bottom, hi = horizontal ? right : top, open = 0;
            for (int i = lo; i <= hi; i++) {
                boolean covered = false;
                for (Band o : others) {
                    if (o == this || o.horizontal() == horizontal || !o.touches(this)) continue;
                    covered |= horizontal ? i >= o.left && i <= o.right : i >= o.bottom && i <= o.top;
                }
                if (!covered) open++;
            }
            return open;
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
    /**
     * Breadths a spur must run on past the street it leaves. A shorter one is a
     * yard's mouth opening off the street, not a street of its own, and swept it
     * is a blob of asphalt stepping out of the kerb line.
     */
    static final int SPUR_REACH = 2;
    /** Points along a junction turn's quarter arc, past its two ends. */
    static final int TURN_STEPS = 6;

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
        return routes(road, null, null, null, seed, maxBreadth, StreetStyle.ROUND_BARE).routes();
    }

    /**
     * As {@link #routes(boolean[][], long)}, also sweeping a stretch of road that jogs a cell
     * sideways off the next, where every cell of the stretch is {@code paved} (see
     * {@link #streets(boolean[][], boolean[][], boolean[][], boolean[][], long, int, StreetStyle)}).
     */
    public static List<Route> routes(boolean[][] road, boolean[][] paved, long seed) {
        return routes(road, null, null, paved, seed, MAX_BREADTH, StreetStyle.ROUND_BARE).routes();
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
     * road where the kerb steps back; and a <em>pill</em>, a short band met by
     * no crossing street at either end, which would float a capsule of asphalt
     * in a gap between buildings. Both keep their paving. A short band met at
     * one end only, by a street that stands on its own, is a <em>spur</em>: a
     * dead end off that street into a yard or lot, swept to where its street
     * ends, its mouth turned only where the turn stays on street or footway
     * rather than being cut off at the yard's edge. Bands of {@code road} alone are swept as
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
        return routes(road, city, footway, null, seed, maxBreadth, style);
    }

    /**
     * As {@link #streets(boolean[][], boolean[][], boolean[][], long, int, StreetStyle)}, also
     * sweeping a stretch of road that jogs a cell sideways off the next. Each cell line the two
     * stretches share runs on past the jog into the other, so no run of the road widens whole to a
     * band there; the stretch is swept only where every cell of it is {@code paved}, as a street the
     * town laid, and a strip of road across bare ground keeps its ground. A paved street linking two
     * swept ones is swept the same way where its lines run on along one of them past it.
     */
    public static Network streets(boolean[][] road, boolean[][] city, boolean[][] footway, boolean[][] paved,
                                  long seed, int maxBreadth, StreetStyle style) {
        return routes(road, city, footway, paved, seed, maxBreadth, style);
    }

    private static Network routes(boolean[][] road, boolean[][] city, boolean[][] footway, boolean[][] paved,
                                  long seed, int maxBreadth, StreetStyle style) {
        if (road.length == 0 || road[0].length == 0) return new Network(List.of(), List.of());
        Set<Band> candidates = new LinkedHashSet<>();
        int cap = city == null ? Math.min(maxBreadth, MAX_BREADTH) : MAX_BREADTH;
        bands(road, null, false, cap, candidates);
        bands(road, null, true, cap, candidates);
        Kept kept = keep(candidates, road, city, maxBreadth);
        List<Band> bands = kept.bands();
        Set<Band> spurs = kept.spurs();
        if (paved != null) {
            // A jogged stretch fills a gap between roads that are swept: past each of its ends the road
            // carries on, as a crossing street or the next stretch of the jog. So does a link, a street
            // whose lines run on along a swept band past it: it is the part's length off that band, and
            // ends where the band begins or where a crossing street meets it. Neither displaces a swept
            // band, by holding it or by running narrower along it and so making it a bulge; each is
            // held to the same rules as any band, and is swept only if they keep it and leave every
            // other band as it was. One the road did not carry on past would float a stub of asphalt.
            Set<Band> parts = new LinkedHashSet<>();
            bands(road, paved, false, cap, parts);
            bands(road, paved, true, cap, parts);
            Set<Band> jogged = new LinkedHashSet<>();
            Set<Band> drawn = new LinkedHashSet<>(bands);
            for (Band part : parts) for (Band b : part.clearOf(drawn)) {
                if (!candidates.contains(b) && drawn.stream().noneMatch(c -> b.contains(c) || b.alongside(c))) jogged.add(b);
            }
            if (!jogged.isEmpty()) {
                Set<Band> all = new LinkedHashSet<>(candidates);
                all.addAll(jogged);
                List<Band> swept = bands, joined = new ArrayList<>(bands);
                for (Band b : keep(all, road, city, maxBreadth).bands()) {
                    if (jogged.contains(b) && b.joined(true, swept) && b.joined(false, swept)) joined.add(b);
                }
                bands = joined;
            }
        }
        List<Route> routes = new ArrayList<>();
        Map<Band, Carriageway> lanes = new HashMap<>();
        for (Band b : bands) {
            boolean street = city != null && b.holds(city);
            lanes.put(b, street ? Carriageway.of(b, footway, style.sidewalks()) : Carriageway.whole(b));
        }
        Map<Band, int[]> ends = city == null ? Map.of() : junctionEnds(bands, lanes);
        for (Band b : bands) {
            boolean street = city != null && b.holds(city);
            Carriageway c = lanes.get(b);
            boolean square = street && style.squareEnds();
            double inset = square ? -.5 : c.width() / 2 - .5;
            int lo = lo(b, ends), hi = hi(b, ends);
            Point a = b.horizontal() ? new Point(lo + inset, c.centre()) : new Point(c.centre(), lo + inset);
            Point z = b.horizontal() ? new Point(hi - inset, c.centre()) : new Point(c.centre(), hi - inset);
            // A square is a junction, covered by its through bands. Isolated small
            // squares retain their original paving rather than inventing a road.
            if (a.equals(z) || b.width() == b.height()) continue;
            EndCap end = square ? EndCap.BUTT : EndCap.ROUND;
            add(routes, List.of(a, z), c.width(), seed, Material.ASPHALT, end);
        }
        for (Band h : bands) for (Band v : bands) {
            // Whether the two meet is a question of their bands; a street trimmed to
            // the centre of the one it ends at still meets it. The arm test below
            // uses the trimmed ends.
            if (!h.horizontal() || v.horizontal() || v.cx() < h.left || v.cx() > h.right
                    || h.cy() < v.bottom || h.cy() > v.top) continue;
            Carriageway ch = lanes.get(h), cv = lanes.get(v);
            double radius = Math.max(ch.width(), cv.width());
            for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                double x = cv.centre(), y = ch.centre();
                // Both approach arms must actually exist. Never bend a dead end
                // into a phantom street on the other side of a pruned junction.
                if (x + sx * (radius + 1) < lo(h, ends) || x + sx * (radius + 1) > hi(h, ends)
                        || y + sy * (radius + 1) < lo(v, ends) || y + sy * (radius + 1) > hi(v, ends)) continue;
                // A spur's mouth may open straight onto its yard; a kerb radius
                // there would be cut off square at the yard's edge.
                if ((spurs.contains(h) || spurs.contains(v))
                        && !paved(road, footway, x, y, x + sx * (radius + 1), y + sy * (radius + 1))) continue;
                // The arc is sampled finely: the rasterizer's Catmull-Rom spline
                // through a coarse arc overshoots it beside each tail, a kink in the
                // kerb line where the turn rejoins the street.
                List<Point> turn = new ArrayList<>();
                turn.add(new Point(x + sx * (radius + 1), y));
                for (int k = 0; k <= TURN_STEPS; k++) {
                    double theta = Math.PI / 2 * k / TURN_STEPS;
                    turn.add(new Point(x + sx * radius * (1 - Math.sin(theta)), y + sy * radius * (1 - Math.cos(theta))));
                }
                turn.add(new Point(x, y + sy * (radius + 1)));
                double width = Math.min(ch.width(), cv.width());
                add(routes, turn, width, seed, Material.ASPHALT, EndCap.ROUND);
            }
        }
        if (city != null) for (Band b : bands) if (b.holds(city)) tapers(b, bands, lanes, ends, routes, seed);
        List<Marking> paint = new ArrayList<>();
        if (city != null && style.markings())
            for (Band b : bands) if (b.holds(city) && b.width() != b.height()) centreLine(b, lanes.get(b), bands, ends, paint);
        return new Network(List.copyOf(routes), List.copyOf(paint));
    }

    /**
     * Dashes down the middle of a street's asphalt, kept a cell clear of each
     * end and of every crossing street, so a junction is left unpainted. Where
     * two bands running the same way overlap, only the broader (then the
     * longer) one is painted there, so one carriageway carries one line.
     */
    private static void centreLine(Band b, Carriageway c, List<Band> bands, Map<Band, int[]> ends, List<Marking> out) {
        boolean horizontal = b.horizontal();
        double from = lo(b, ends) + .5, to = hi(b, ends) - .5;
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

    /**
     * Cells past a crossing street's band that a street ending there may run on
     * and still be ending at it. A street's band is a maximal run of street
     * cells, so one that ends at a T reaches across the street it meets to the
     * far kerb row, and often a cell or two into a yard mouth beyond; swept
     * square to its band, it put a tongue of asphalt across the through road's
     * far sidewalk at every T.
     */
    static final int TEE_OVERRUN = 3;

    /**
     * Where each street that ends at another really ends. At a T, where the
     * street it meets runs on past it both ways, it stops at that street's
     * centre line and the through road's asphalt covers the rest. At a corner,
     * where the other street ends at it too, each runs to the outer edge of the
     * other's asphalt, so the corner is paved square and nothing runs on into
     * the kerb beyond. Only an end that stops within the other street's band
     * plus {@link #TEE_OVERRUN} is moved, and only when the street continues on
     * the near side; a street that runs on through is a crossing and keeps its
     * length. Each value is the band's {@code {lo, hi}} along its own direction.
     */
    private static Map<Band, int[]> junctionEnds(List<Band> bands, Map<Band, Carriageway> lanes) {
        Map<Band, int[]> out = new HashMap<>();
        for (Band b : bands) {
            boolean horizontal = b.horizontal();
            int lo = horizontal ? b.left : b.bottom, hi = horizontal ? b.right : b.top;
            double line = horizontal ? b.cy() : b.cx();
            int newLo = lo, newHi = hi;
            for (Band c : bands) {
                if (c == b || c.horizontal() == horizontal) continue;
                // The crossing street's extent along b's line, and its band across b's direction.
                int alongLo = horizontal ? c.bottom : c.left, alongHi = horizontal ? c.top : c.right;
                if (line < alongLo || line > alongHi) continue;
                int crossLo = horizontal ? c.left : c.bottom, crossHi = horizontal ? c.right : c.top;
                // c runs on past b's band on both sides: a T. Otherwise b turns into c at a
                // corner; a cell or two of c beyond b is the same overrun b has into c.
                int bandLo = horizontal ? b.bottom : b.left, bandHi = horizontal ? b.top : b.right;
                boolean through = alongLo < bandLo - TEE_OVERRUN && alongHi > bandHi + TEE_OVERRUN;
                Carriageway asphalt = lanes.get(c);
                double centre = asphalt.centre();
                int farLo = (int) Math.ceil(centre - asphalt.width() / 2 + .5);
                int farHi = (int) Math.floor(centre + asphalt.width() / 2 - .5);
                if (hi >= crossLo && hi <= crossHi + TEE_OVERRUN && lo < crossLo)
                    newHi = Math.min(newHi, through ? (int) Math.floor(centre) : farHi);
                if (lo <= crossHi && lo >= crossLo - TEE_OVERRUN && hi > crossHi)
                    newLo = Math.max(newLo, through ? (int) Math.ceil(centre) : farLo);
            }
            if ((newLo != lo || newHi != hi) && newHi > newLo) out.put(b, new int[]{newLo, newHi});
        }
        return out;
    }

    /** Cells along which a street that widens beside another merges into it. */
    static final int TAPER = 6;

    /**
     * Where a street ends beside a parallel street that runs on past its end
     * (two arms grown side by side, so the road widens there), a square end is a
     * step across the carriageway. A short diagonal stroke from the ending
     * street's centre into the other's turns the step into a taper. An end that
     * meets a crossing street is a junction and keeps its square end.
     */
    private static void tapers(Band b, List<Band> bands, Map<Band, Carriageway> lanes, Map<Band, int[]> ends,
                               List<Route> routes, long seed) {
        boolean horizontal = b.horizontal();
        int lo = lo(b, ends), hi = hi(b, ends);
        int sideLo = horizontal ? b.bottom : b.left, sideHi = horizontal ? b.top : b.right;
        for (int dir : new int[]{-1, 1}) {
            int end = dir < 0 ? lo : hi;
            if (ends.containsKey(b) && (dir < 0 ? lo != (horizontal ? b.left : b.bottom)
                    : hi != (horizontal ? b.right : b.top))) continue;
            if (meetsCrossing(b, end, bands)) continue;
            for (Band a : bands) {
                if (a == b || a.horizontal() != horizontal) continue;
                int aSideLo = horizontal ? a.bottom : a.left, aSideHi = horizontal ? a.top : a.right;
                if (aSideLo != sideHi + 1 && aSideHi != sideLo - 1) continue;
                int aLo = lo(a, ends), aHi = hi(a, ends);
                // a runs on past b's end by a taper's length, and alongside b at the end.
                boolean past = dir < 0 ? aLo <= end - TAPER && aHi >= end + 1 : aHi >= end + TAPER && aLo <= end - 1;
                if (!past) continue;
                double from = lanes.get(b).centre(), to = lanes.get(a).centre();
                double width = Math.min(lanes.get(b).width(), lanes.get(a).width());
                double start = end + (dir < 0 ? 1 : -1), stop = end + dir * TAPER;
                Point p = horizontal ? new Point(start, from) : new Point(from, start);
                Point q = horizontal ? new Point(stop, to) : new Point(to, stop);
                add(routes, List.of(p, q), width, seed, Material.ASPHALT, EndCap.ROUND);
                break;
            }
        }
    }

    /** Whether a perpendicular band crosses b's line at its end cell {@code end}. */
    private static boolean meetsCrossing(Band b, int end, List<Band> bands) {
        boolean horizontal = b.horizontal();
        double line = horizontal ? b.cy() : b.cx();
        for (Band c : bands) {
            if (c == b || c.horizontal() == horizontal) continue;
            int alongLo = horizontal ? c.bottom : c.left, alongHi = horizontal ? c.top : c.right;
            int crossLo = horizontal ? c.left : c.bottom, crossHi = horizontal ? c.right : c.top;
            if (line >= alongLo && line <= alongHi && end >= crossLo - 1 && end <= crossHi + 1) return true;
        }
        return false;
    }

    /** Whether every cell between two corners is street or footway, so a sweep over them is not cut off. */
    private static boolean paved(boolean[][] road, boolean[][] footway, double x0, double y0, double x1, double y1) {
        int w = road.length, h = road[0].length;
        for (long x = Math.round(Math.min(x0, x1)); x <= Math.round(Math.max(x0, x1)); x++)
            for (long y = Math.round(Math.min(y0, y1)); y <= Math.round(Math.max(y0, y1)); y++) {
                if (x < 0 || y < 0 || x >= w || y >= h) return false;
                if (!road[(int) x][(int) y] && (footway == null || !footway[(int) x][(int) y])) return false;
            }
        return true;
    }

    private static int lo(Band b, Map<Band, int[]> ends) {
        int[] e = ends.get(b);
        return e != null ? e[0] : b.horizontal() ? b.left : b.bottom;
    }

    private static int hi(Band b, Map<Band, int[]> ends) {
        int[] e = ends.get(b);
        return e != null ? e[1] : b.horizontal() ? b.right : b.top;
    }

    /** The bands a street network keeps of {@code candidates}, and which of them are spurs. */
    private record Kept(List<Band> bands, Set<Band> spurs) {}

    /** The city's rules over {@code candidates}: no broad street, no band held by another, no bulge or pill. */
    private static Kept keep(Set<Band> found, boolean[][] road, boolean[][] city, int maxBreadth) {
        Set<Band> candidates = new LinkedHashSet<>(found);
        if (city != null) candidates.removeIf(b -> b.breadth() > maxBreadth && b.holds(city));
        List<Band> bands = candidates.stream().filter(b -> candidates.stream()
                .noneMatch(other -> !other.equals(b) && other.contains(b))).toList();
        Set<Band> spurs = new HashSet<>();
        if (city != null) {
            List<Band> all = bands;
            bands = all.stream().filter(b -> !b.holds(city) || all.stream().noneMatch(a -> a.alongside(b))).toList();
            // A short band is a through street when each of its ends meets
            // another street. One met at a single end by a street that stands on
            // its own is a dead end off that street, a spur: it runs into a yard
            // or a lot, and left paved it reads as a stub of street with no road
            // on it. One that meets no street floats between buildings.
            List<Band> kept = bands;
            int w = road.length, h = road[0].length;
            List<Band> streets = kept.stream().filter(b -> !b.holds(city) || b.length() >= PILL_LENGTH * b.breadth()
                    || (b.endMet(true, kept, w, h) && b.endMet(false, kept, w, h))).toList();
            for (Band b : kept)
                if (!streets.contains(b) && (b.crossedAt(true, streets) || b.crossedAt(false, streets))
                        && b.reach(streets) >= SPUR_REACH * b.breadth()) spurs.add(b);
            bands = kept.stream().filter(b -> streets.contains(b) || spurs.contains(b)).toList();
        }
        return new Kept(bands, spurs);
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

    /**
     * Maximal horizontal and vertical rectangles, not the obsolete pre-pruning trunks. A run whose
     * whole length is too narrow for a band may still hold one along part of it: a road that jogs a
     * cell sideways is two bands offset by a cell, and each run down the jog's shared lines carries
     * on past it into the other, so widened whole it never reaches three cells. With {@code paved},
     * such a part is a band where every cell of it is paved; without, it is not looked for.
     */
    private static void bands(boolean[][] road, boolean[][] paved, boolean transpose, int maxBreadth,
                              Set<Band> out) {
        int length = transpose ? road[0].length : road.length;
        int rows = transpose ? road.length : road[0].length;
        for (int row = 0; row < rows; row++) for (int start = 0; start < length;) {
            if (!at(road, transpose, start, row)) { start++; continue; }
            int end = start;
            while (end + 1 < length && at(road, transpose, end + 1, row)) end++;
            if (end - start >= 2 && !band(road, null, transpose, start, end, row, rows, maxBreadth, out)
                    && paved != null) {
                // The stretches of the run three lines broad, each widened as a run of its own.
                for (int core = Math.max(0, row - 2); core <= row && core + 2 < rows; core++) {
                    for (int a = start; a <= end;) {
                        if (!full(road, transpose, a, a, core) || !full(road, transpose, a, a, core + 1)
                                || !full(road, transpose, a, a, core + 2)) { a++; continue; }
                        int b = a;
                        while (b + 1 <= end && full(road, transpose, b + 1, b + 1, core)
                                && full(road, transpose, b + 1, b + 1, core + 1)
                                && full(road, transpose, b + 1, b + 1, core + 2)) b++;
                        if (b - a >= 2) band(road, paved, transpose, a, b, row, rows, maxBreadth, out);
                        a = b + 1;
                    }
                }
            }
            start = end + 1;
        }
    }

    /**
     * Widens the run {@code start..end} of {@code row} into a band, if it makes one, kept only where
     * every cell of it is {@code paved} when that is given.
     */
    private static boolean band(boolean[][] road, boolean[][] paved, boolean transpose, int start, int end, int row,
                                int rows, int maxBreadth, Set<Band> out) {
        int lo = row, hi = row;
        while (lo > 0 && full(road, transpose, start, end, lo - 1)) lo--;
        while (hi + 1 < rows && full(road, transpose, start, end, hi + 1)) hi++;
        int breadth = hi - lo + 1;
        if (breadth < 3) return false;
        boolean laid = true;
        for (int r = lo; r <= hi && laid && paved != null; r++) laid = full(paved, transpose, start, end, r);
        if (laid && breadth <= maxBreadth && end - start + 1 >= breadth)
            out.add(transpose ? new Band(lo, start, hi, end) : new Band(start, lo, end, hi));
        return true;
    }

    private static boolean full(boolean[][] road, boolean transpose, int start, int end, int row) {
        for (int x = start; x <= end; x++) if (!at(road, transpose, x, row)) return false;
        return true;
    }

    private static boolean at(boolean[][] road, boolean transpose, int x, int y) {
        return transpose ? road[y][x] : road[x][y];
    }
}
