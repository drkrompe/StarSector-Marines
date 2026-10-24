package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a plan's knolls stand ({@link Landscape.Knoll}, {@code elevation-nouns.md}, Knolls), worked out before
 * anything grows from the places' seeds alone, so growth, claims, the gates and the terrace all read one answer.
 *
 * <p>A knoll's top is a box round its place's seed, moved in from the map's edge as far as its ring and the
 * terrace's look past its outline need. It stands only on ground no other place starts from: a knoll whose top or
 * ring comes within {@link #SEED_ROOM} cells of another place's seed, or runs into another knoll, is refused before
 * growth, and its place grows as it would have.
 */
public final class KnollGround {

    private KnollGround() {}

    /** Cells kept between a knoll's ring and the map's edge: the terrace planner looks this far past its outline. */
    static final int EDGE = 6;
    /** Cells round another place's seed a knoll's ground keeps off, so that place has room to start. */
    static final int SEED_ROOM = 14;
    /** Cells, at least, between the seed and its top's edge once the top is moved in from the map's edge. */
    static final int SEED_INSET = 4;
    /**
     * Cells inside the top's edge the place claims nothing on, so it builds nothing there. The terrace's cliff is
     * drawn into its margin (three cells deep on a south side), and a place built out to the top's edge would leave
     * the strip of top beyond it cut off between its walls, the cliff and the ramps; a lane round the top joins them.
     */
    public static final int LANE = 3;
    /** Cells across a gate's road: a grown arm's, so a tank's ramp carries it. */
    public static final int GATE_WIDTH = 5;

    /**
     * A knoll as it stands on this map: its place's index, its top as an inclusive box and its ring's width, or
     * why it does not stand ({@code refusal}, with the other fields then meaningless).
     */
    public record Resolved(Landscape.Knoll knoll, int place, int x0, int y0, int x1, int y1, String refusal) {
        public boolean stands() { return refusal == null; }

        /** Whether a cell is on the knoll's top. */
        public boolean onTop(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }

        /** Whether a cell is where the place may build: its top less the lane round its edge ({@link #LANE}). */
        public boolean buildable(int x, int y) {
            return x >= x0 + LANE && x <= x1 - LANE && y >= y0 + LANE && y <= y1 - LANE;
        }

        /** Whether a cell is on the knoll's ground: its top or its ring. */
        public boolean onGround(int x, int y) {
            int r = knoll.ring();
            return x >= x0 - r && x <= x1 + r && y >= y0 - r && y <= y1 + r;
        }

        /** Whether a cell is on the ring kept open round the top. */
        public boolean onRing(int x, int y) { return onGround(x, y) && !onTop(x, y); }

        static Resolved refused(Landscape.Knoll knoll, int place, String why) {
            return new Resolved(knoll, place, 0, 0, -1, -1, why);
        }
    }

    /** Every knoll the plan's landscape asks for, in its order, standing or refused. */
    public static List<Resolved> resolve(PrecinctPlan plan, int width, int height) {
        return resolve(plan.landscape().knolls(), plan.precincts(), width, height);
    }

    /** The knolls on a map of this size among these places; a later knoll that meets an earlier one is refused. */
    static List<Resolved> resolve(List<Landscape.Knoll> knolls, List<Precinct> places, int width, int height) {
        List<Resolved> out = new ArrayList<>();
        for (Landscape.Knoll knoll : knolls) {
            Resolved r = resolve(knoll, places, width, height);
            if (r.stands()) {
                for (Resolved earlier : out) {
                    if (!earlier.stands()) continue;
                    if (earlier.place() == r.place()) {
                        r = Resolved.refused(knoll, r.place(), "its place already stands on " + earlier.knoll().name());
                        break;
                    }
                    if (overlap(earlier, r)) {
                        r = Resolved.refused(knoll, r.place(), "its ground runs into " + earlier.knoll().name());
                        break;
                    }
                }
            }
            out.add(r);
        }
        return out;
    }

    private static Resolved resolve(Landscape.Knoll knoll, List<Precinct> places, int width, int height) {
        int place = -1;
        for (int i = 0; i < places.size(); i++) if (places.get(i).name().equals(knoll.place())) place = i;
        if (place < 0) return Resolved.refused(knoll, -1, "no place named " + knoll.place());
        Precinct p = places.get(place);
        if (p.isProgrammed()) return Resolved.refused(knoll, place, "a programmed place packs its own ground");
        int reach = knoll.ring() + EDGE;
        int[] xs = span(p.seedX(), knoll.halfWidth(), reach, width);
        int[] ys = span(p.seedY(), knoll.halfHeight(), reach, height);
        if (xs == null || ys == null) return Resolved.refused(knoll, place, "too near the map's edge");
        Resolved r = new Resolved(knoll, place, xs[0], ys[0], xs[1], ys[1], null);
        for (int i = 0; i < places.size(); i++) {
            if (i == place) continue;
            Precinct other = places.get(i);
            if (near(r, other.seedX(), other.seedY())) {
                return Resolved.refused(knoll, place, "its ground comes within " + SEED_ROOM + " cells of "
                        + other.name() + "'s seed");
            }
        }
        return r;
    }

    /**
     * The top's inclusive extent along one axis: {@code half} cells either side of the seed, moved in until
     * {@code reach} cells lie between it and each edge; null when the map is too small, or the move would leave
     * the seed within {@link #SEED_INSET} of the top's edge.
     */
    static int[] span(int seed, int half, int reach, int size) {
        int lo = seed - half, hi = seed + half;
        if (hi - lo + 1 + 2 * reach > size) return null;
        if (lo < reach) { hi += reach - lo; lo = reach; }
        if (hi > size - 1 - reach) { lo -= hi - (size - 1 - reach); hi = size - 1 - reach; }
        if (seed < lo + SEED_INSET || seed > hi - SEED_INSET) return null;
        return new int[]{lo, hi};
    }

    private static boolean near(Resolved r, int x, int y) {
        int margin = r.knoll().ring() + SEED_ROOM;
        return x >= r.x0() - margin && x <= r.x1() + margin && y >= r.y0() - margin && y <= r.y1() + margin;
    }

    private static boolean overlap(Resolved a, Resolved b) {
        int ra = a.knoll().ring(), rb = b.knoll().ring();
        return a.x0() - ra <= b.x1() + rb && b.x0() - rb <= a.x1() + ra
                && a.y0() - ra <= b.y1() + rb && b.y0() - rb <= a.y1() + ra;
    }

    /**
     * Growth bounds per seed for {@link GrownTrunkPlan#grow}: the standing knoll's top and ring as
     * {@code {x0, y0, x1, y1, ring}} at its place's index, null elsewhere; null when no knoll stands.
     */
    public static int[][] bounds(List<Resolved> knolls, int places) {
        int[][] out = null;
        for (Resolved r : knolls) {
            if (!r.stands()) continue;
            if (out == null) out = new int[places][];
            out[r.place()] = new int[]{r.x0(), r.y0(), r.x1(), r.y1(), r.knoll().ring()};
        }
        return out;
    }

    /**
     * Cuts the knoll's gates into the road grid: for each side it names, a road {@link #GATE_WIDTH} cells across
     * on the line through its place's seed, from the place's outermost street on that line out across the ring to
     * open country beyond it, owned by the place. Straight across the ring, so the terrace's cliff meets each road
     * square on and ramps it down. Cells another place's road already holds are left as they are.
     *
     * @param owner the road grid, indexed {@code [x][y]}, holding a place index or {@link GrownTrunkPlan#UNOWNED}
     */
    public static void carveGates(int[][] owner, Resolved knoll, int seedX, int seedY, int width, int height) {
        if (!knoll.stands()) return;
        int half = GATE_WIDTH / 2, ring = knoll.knoll().ring();
        for (Direction d : knoll.knoll().gates()) {
            boolean alongY = d.dy != 0;
            // The road's middle line, kept far enough inside the top that the whole road leaves through its side.
            int line = alongY ? clamp(seedX, knoll.x0() + half, knoll.x1() - half)
                    : clamp(seedY, knoll.y0() + half, knoll.y1() - half);
            int start = alongY ? seedY : seedX;
            int edge = alongY ? (d.dy > 0 ? knoll.y1() : knoll.y0()) : (d.dx > 0 ? knoll.x1() : knoll.x0());
            int step = alongY ? d.dy : d.dx;
            // From the top's edge back toward the seed, the first of the place's streets on the road's line.
            for (int t = edge; t != start; t -= step) {
                if (crosses(owner, knoll.place(), line, t, alongY, half, width, height)) {
                    start = t;
                    break;
                }
            }
            int size = alongY ? height : width;
            int end = Math.max(1, Math.min(size - 2, edge + step * (ring + 2)));
            for (int t = start; t != end + step; t += step) {
                for (int a = line - half; a <= line + half; a++) {
                    int x = alongY ? a : t, y = alongY ? t : a;
                    if (x < 0 || y < 0 || x >= width || y >= height) continue;
                    if (owner[x][y] == GrownTrunkPlan.UNOWNED) owner[x][y] = knoll.place();
                }
            }
        }
    }

    private static boolean crosses(int[][] owner, int place, int line, int t, boolean alongY, int half,
                                   int width, int height) {
        for (int a = line - half; a <= line + half; a++) {
            int x = alongY ? a : t, y = alongY ? t : a;
            if (x >= 0 && y >= 0 && x < width && y < height && owner[x][y] == place) return true;
        }
        return false;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
