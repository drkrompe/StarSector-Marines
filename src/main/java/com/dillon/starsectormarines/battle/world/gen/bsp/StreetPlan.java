package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.SubRect;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkSegment;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A town's streets planned as one network once they have grown ({@code mapgen-nouns.md}, Street plan). Growth lays
 * arms from junctions at random lengths and stops each where it runs into something, so a town comes out with short
 * dead ends, crossings pulled apart into two offset tees and streets crowding a neighbour running the same way; each
 * of those reads as a road nobody planned. The plan reads the grown streets ({@link TrunkSegment}s, one width each)
 * and improves the network with local moves, each kept only where it leaves the network in as few pieces as before
 * and lays no street on ground growth was barred from:
 *
 * <ul>
 *   <li>a <em>stub</em>, a street met at one end only and shorter than {@link #STUB_BREADTHS} breadths (and
 *       {@link #STUB_MIN} cells), is taken up;</li>
 *   <li>two side streets leaving one street on opposite sides within {@link #SNAP} cells of each other are made one
 *       <em>cross</em>: one is moved across onto the other's line where its far end still meets what it met;</li>
 *   <li>a street running beside a longer one the same way with less than {@link #MIN_BLOCK} cells between them, so
 *       no building could stand between, is taken up.</li>
 * </ul>
 *
 * <p>Moves run until none applies. The plan changes streets and their ground only: the road-owner grid, the road
 * mask and the segments growth hands on, and the hinterland where a street was taken up. It draws no random number.
 * {@code -Dbattle.mapgen.streetPlan=false} is the control.
 */
public final class StreetPlan {

    private StreetPlan() {}

    /**
     * TEMPORARY switch while the grown and the planned street networks are compared ({@code switches.md}). Remove it,
     * with the unplanned path, once the plan is proven on the story map pools and the owner's review.
     */
    public static final String PROPERTY = "battle.mapgen.streetPlan";

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));
    }

    /** Breadths under which a dead end is a stub. */
    static final int STUB_BREADTHS = 4;
    /** Cells under which any dead end is a stub. */
    static final int STUB_MIN = 12;
    /** Cells apart under which two side streets on opposite sides are one crossing pulled apart. */
    static final int SNAP = 10;
    /** Cells between two streets running the same way under which no block stands between them. */
    static final int MIN_BLOCK = 6;
    /** Passes of the moves at most; each pass applies every move it can. */
    static final int PASSES = 6;

    /** What the plan did, for the map's log. */
    public record Result(GrownTrunkPlan.Grown grown, int stubs, int crosses, int crowded) {}

    /**
     * Plans {@code grown}'s streets. {@code barred} is ground no street may be moved onto (or null for none). The
     * owner grid is changed in place; the road mask and segments are new.
     */
    public static Result plan(GrownTrunkPlan.Grown grown, boolean[][] barred) {
        TrunkPlan.Plan p = grown.plan();
        int w = p.width, h = p.height;
        Net net = new Net(p.roadCells, grown.owner(), new ArrayList<>(p.trunks), barred, w, h);
        List<SubRect> hinterland = new ArrayList<>(grown.hinterland());
        int stubs = 0, crosses = 0, crowded = 0;
        for (int pass = 0; pass < PASSES; pass++) {
            int before = stubs + crosses + crowded;
            stubs += net.pruneStubs(hinterland);
            crosses += net.alignTees(hinterland);
            crowded += net.pruneCrowded(hinterland);
            if (stubs + crosses + crowded == before) break;
        }
        TrunkPlan.Plan planned = new TrunkPlan.Plan(net.road, p.subRects, List.copyOf(net.segments), p.intersection, w, h);
        return new Result(new GrownTrunkPlan.Grown(planned, hinterland, net.owner), stubs, crosses, crowded);
    }

    /**
     * Where a road leaving {@code (x, y)} toward {@code side} meets the planned streets square: the foot on the
     * centre line of the nearest street ahead running across its way, at right angles, so the road runs straight
     * out and ends as a tee (or a cross, where a street already leaves the far side on its line). Null where none
     * does: no street lies across the way ahead, a street running the same way is reached first (the road would run
     * into its end), or another street meets the found one within {@link #SNAP} cells of the foot off its line, so
     * the tee would make an offset pair. A caller keeps its own road where it gets null.
     *
     * @param streets the planned streets ({@code grown.plan().trunks})
     * @return {@code {x, y}} of the foot, or null
     */
    public static int[] meet(int x, int y, Side side, List<TrunkSegment> streets) {
        boolean eastWest = side.dx != 0;
        int along = eastWest ? y : x;               // the road's line, held across the street it meets
        int from = eastWest ? x : y;
        int sign = eastWest ? side.dx : side.dy;
        TrunkSegment best = null;
        int bestDist = Integer.MAX_VALUE;
        boolean bestAcross = false;
        for (TrunkSegment s : streets) {
            int lo = eastWest ? s.top : s.left, hi = eastWest ? s.bottom : s.right;
            if (along < lo || along > hi) continue;  // not on the road's line
            int near = sign > 0 ? (eastWest ? s.left : s.top) - from : from - (eastWest ? s.right : s.bottom);
            if (near < 1 || near >= bestDist) continue;
            best = s;
            bestDist = near;
            bestAcross = s.horizontal != eastWest;
        }
        if (best == null || !bestAcross) return null;
        for (TrunkSegment o : streets) {
            if (o == best || o.horizontal == best.horizontal) continue;
            if (Net.side(best, o) == 0 && !Net.crosses(best, o)) continue;
            int offset = Math.abs(Net.centre(o) - along);
            if (offset >= 1 && offset <= SNAP) return null;
        }
        int foot = Net.centre(best);
        return eastWest ? new int[]{foot, y} : new int[]{x, foot};
    }

    /** The streets as they are being planned: the road mask, its owners and the segments that paint them. */
    static final class Net {
        final boolean[][] road;
        final int[][] owner;
        final List<TrunkSegment> segments;
        final boolean[][] barred;
        final int w, h;

        Net(boolean[][] road, int[][] owner, List<TrunkSegment> segments, boolean[][] barred, int w, int h) {
            this.road = new boolean[w][];
            for (int x = 0; x < w; x++) this.road[x] = road[x].clone();
            this.owner = owner;
            this.segments = segments;
            this.barred = barred;
            this.w = w;
            this.h = h;
        }

        int pruneStubs(List<SubRect> hinterland) {
            int done = 0;
            for (int i = 0; i < segments.size(); i++) {
                TrunkSegment s = segments.get(i);
                boolean lo = met(s, true), hi = met(s, false);
                if (lo == hi) continue;
                int length = length(s), breadth = breadth(s);
                if (length >= Math.max(STUB_MIN, STUB_BREADTHS * breadth)) continue;
                if (remove(i, hinterland)) { done++; i--; }
            }
            return done;
        }

        int pruneCrowded(List<SubRect> hinterland) {
            int done = 0;
            for (int i = 0; i < segments.size(); i++) {
                TrunkSegment s = segments.get(i);
                boolean crowded = false;
                for (TrunkSegment o : segments) {
                    if (o == s || o.horizontal != s.horizontal || length(o) <= length(s)) continue;
                    int along = s.horizontal ? Math.min(s.right, o.right) - Math.max(s.left, o.left) + 1
                            : Math.min(s.bottom, o.bottom) - Math.max(s.top, o.top) + 1;
                    if (along * 2 < length(s)) continue;
                    int gap = s.horizontal ? Math.max(o.top - s.bottom, s.top - o.bottom) - 1
                            : Math.max(o.left - s.right, s.left - o.right) - 1;
                    if (gap >= 0 && gap < MIN_BLOCK) { crowded = true; break; }
                }
                if (crowded && remove(i, hinterland)) { done++; i--; }
            }
            return done;
        }

        int alignTees(List<SubRect> hinterland) {
            int done = 0;
            for (int i = 0; i < segments.size(); i++) {
                for (int j = 0; j < segments.size(); j++) {
                    if (i == j || i >= segments.size() || j >= segments.size()) continue;
                    TrunkSegment a = segments.get(i), b = segments.get(j);
                    if (a.horizontal != b.horizontal) continue;
                    TrunkSegment c = common(a, b);
                    if (c == null) continue;
                    int offset = centre(b) - centre(a);
                    if (offset == 0 || Math.abs(offset) > SNAP) continue;
                    // Move a onto b's line; only a side street free at its far end moves.
                    if (move(i, offset, c, hinterland)) { done++; break; }
                }
            }
            return done;
        }

        /** The street {@code a} and {@code b} both end on, from opposite sides, or null. */
        private TrunkSegment common(TrunkSegment a, TrunkSegment b) {
            for (TrunkSegment c : segments) {
                if (c == a || c == b || c.horizontal == a.horizontal) continue;
                int sa = side(c, a), sb = side(c, b);
                if (sa != 0 && sb != 0 && sa == -sb) return c;
            }
            return null;
        }

        /**
         * Which side of the crossing street {@code c} the street {@code s} leaves from, having one end on it: -1 it
         * runs off below or left of {@code c}, 1 above or right, 0 when it does not end on {@code c}.
         */
        static int side(TrunkSegment c, TrunkSegment s) {
            int cLo = c.horizontal ? c.top : c.left, cHi = c.horizontal ? c.bottom : c.right;
            int sLo = s.horizontal ? s.left : s.top, sHi = s.horizontal ? s.right : s.bottom;
            int sAcrossLo = s.horizontal ? s.top : s.left, sAcrossHi = s.horizontal ? s.bottom : s.right;
            int cAlongLo = c.horizontal ? c.left : c.top, cAlongHi = c.horizontal ? c.right : c.bottom;
            if (sAcrossLo < cAlongLo || sAcrossHi > cAlongHi) return 0;
            boolean startsOn = sLo >= cLo - 1 && sLo <= cHi + 1 && sHi > cHi;
            boolean endsOn = sHi >= cLo - 1 && sHi <= cHi + 1 && sLo < cLo;
            return startsOn ? 1 : endsOn ? -1 : 0;
        }

        /** Whether {@code s} runs right through {@code c}, out on both sides. */
        static boolean crosses(TrunkSegment c, TrunkSegment s) {
            int cLo = c.horizontal ? c.top : c.left, cHi = c.horizontal ? c.bottom : c.right;
            int sLo = s.horizontal ? s.left : s.top, sHi = s.horizontal ? s.right : s.bottom;
            int sAcross = centre(s);
            int cAlongLo = c.horizontal ? c.left : c.top, cAlongHi = c.horizontal ? c.right : c.bottom;
            return sLo < cLo && sHi > cHi && sAcross >= cAlongLo && sAcross <= cAlongHi;
        }

        static int centre(TrunkSegment s) {
            return s.horizontal ? (s.top + s.bottom) / 2 : (s.left + s.right) / 2;
        }

        static int length(TrunkSegment s) {
            return s.horizontal ? s.right - s.left + 1 : s.bottom - s.top + 1;
        }

        static int breadth(TrunkSegment s) {
            return s.horizontal ? s.bottom - s.top + 1 : s.right - s.left + 1;
        }

        /** Whether the first (or last) end of {@code s} meets the map's edge or another street. */
        boolean met(TrunkSegment s, boolean first) {
            int end = s.horizontal ? (first ? s.left : s.right) : (first ? s.top : s.bottom);
            int limit = s.horizontal ? w - 1 : h - 1;
            if (end <= 1 || end >= limit - 1) return true;
            int past = end + (first ? -1 : 1);
            for (TrunkSegment o : segments) {
                if (o == s) continue;
                boolean covers = s.horizontal
                        ? o.left <= past && o.right >= past && o.top <= s.bottom && o.bottom >= s.top
                        : o.top <= past && o.bottom >= past && o.left <= s.right && o.right >= s.left;
                boolean crosses = o.horizontal != s.horizontal && (s.horizontal
                        ? o.left <= end && o.right >= end && o.top <= s.bottom && o.bottom >= s.top
                        : o.top <= end && o.bottom >= end && o.left <= s.right && o.right >= s.left);
                if (covers || crosses) return true;
            }
            return false;
        }

        /** Takes up segment {@code i} where the network keeps its pieces; returns whether it was. */
        boolean remove(int i, List<SubRect> hinterland) {
            TrunkSegment s = segments.get(i);
            int before = pieces();
            List<int[]> freed = new ArrayList<>();
            List<Integer> was = new ArrayList<>();
            for (int x = s.left; x <= s.right; x++) for (int y = s.top; y <= s.bottom; y++) {
                if (coveredByOther(s, x, y) || x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1) continue;
                freed.add(new int[]{x, y});
                was.add(owner[x][y]);
                road[x][y] = false;
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
            if (pieces() > before) {
                for (int k = 0; k < freed.size(); k++) {
                    int[] c = freed.get(k);
                    road[c[0]][c[1]] = true;
                    owner[c[0]][c[1]] = was.get(k);
                }
                return false;
            }
            segments.remove(i);
            hinterland.add(new SubRect(s.left, s.top, s.right, s.bottom));
            return true;
        }

        /** Moves segment {@code i} {@code offset} cells across, still ending on {@code c}; returns whether it did. */
        boolean move(int i, int offset, TrunkSegment c, List<SubRect> hinterland) {
            TrunkSegment s = segments.get(i);
            TrunkSegment moved = s.horizontal
                    ? new TrunkSegment(s.left, s.top + offset, s.right, s.bottom + offset, s.kind, true)
                    : new TrunkSegment(s.left + offset, s.top, s.right + offset, s.bottom, s.kind, false);
            if (moved.left <= 0 || moved.top <= 0 || moved.right >= w - 1 || moved.bottom >= h - 1) return false;
            if (side(c, moved) == 0) return false;
            int who = GrownTrunkPlan.UNOWNED;
            for (int x = s.left; x <= s.right && who == GrownTrunkPlan.UNOWNED; x++)
                for (int y = s.top; y <= s.bottom && who == GrownTrunkPlan.UNOWNED; y++) who = owner[x][y];
            for (int x = moved.left; x <= moved.right; x++) for (int y = moved.top; y <= moved.bottom; y++) {
                if (barred != null && barred[x][y]) return false;
                // It lands on open ground or the street it ends on, never across another street.
                if (road[x][y] && !inside(c, x, y) && !inside(s, x, y)) return false;
            }
            int before = pieces();
            boolean[][] road0 = new boolean[w][];
            int[][] owner0 = new int[w][];
            for (int x = 0; x < w; x++) { road0[x] = road[x].clone(); owner0[x] = owner[x].clone(); }
            for (int x = s.left; x <= s.right; x++) for (int y = s.top; y <= s.bottom; y++) {
                if (coveredByOther(s, x, y)) continue;
                road[x][y] = false;
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
            for (int x = moved.left; x <= moved.right; x++) for (int y = moved.top; y <= moved.bottom; y++) {
                road[x][y] = true;
                if (owner[x][y] == GrownTrunkPlan.UNOWNED) owner[x][y] = who;
            }
            // Its far end (its first when it runs off below or left of c) still meets what it met.
            boolean far = side(c, s) < 0;
            boolean metBefore = met(s, far);
            segments.set(i, moved);
            if (pieces() > before || met(moved, far) != metBefore) {
                segments.set(i, s);
                for (int x = 0; x < w; x++) { road[x] = road0[x]; owner[x] = owner0[x]; }
                return false;
            }
            hinterland.add(new SubRect(s.left, s.top, s.right, s.bottom));
            return true;
        }

        private static boolean inside(TrunkSegment s, int x, int y) {
            return x >= s.left && x <= s.right && y >= s.top && y <= s.bottom;
        }

        private boolean coveredByOther(TrunkSegment s, int x, int y) {
            for (TrunkSegment o : segments) if (o != s && inside(o, x, y)) return true;
            return false;
        }

        /** Pieces of road off the map's perimeter ring, joined by edges. */
        int pieces() {
            boolean[][] seen = new boolean[w][h];
            int n = 0;
            ArrayDeque<int[]> open = new ArrayDeque<>();
            for (int x = 1; x < w - 1; x++) for (int y = 1; y < h - 1; y++) {
                if (!road[x][y] || seen[x][y]) continue;
                n++;
                seen[x][y] = true;
                open.add(new int[]{x, y});
                while (!open.isEmpty()) {
                    int[] c = open.poll();
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], ny = c[1] + d[1];
                        if (nx < 1 || ny < 1 || nx >= w - 1 || ny >= h - 1 || !road[nx][ny] || seen[nx][ny]) continue;
                        seen[nx][ny] = true;
                        open.add(new int[]{nx, ny});
                    }
                }
            }
            return n;
        }
    }
}
