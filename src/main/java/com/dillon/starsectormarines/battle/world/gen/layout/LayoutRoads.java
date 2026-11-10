package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.road.BendFlood;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Carves a layout's {@link Road}s into the grown road network, once the places
 * have claimed their ground, their arteries are cut and the network is welded
 * ({@code mission-layout.md}). A road is road-owner cells like any grown arm,
 * so the stages after it gate, pave and graph it as they do every road; it is
 * carved only on open country (no place's ground, not the surveyed country,
 * nothing the layout holds), and only where it joins the network: a stretch
 * cut off by another place's ground is left out rather than welded across it.
 */
public final class LayoutRoads {

    private LayoutRoads() {}

    /**
     * Carves every road of the plan's layout, mutating the road-owner grid
     * {@code owner}; returns each as laid.
     * {@code rural} may be null.
     */
    public static List<Laid> carve(PrecinctPlan plan, int[][] claim, int[][] owner,
                                   RuralLayout rural, int width, int height) {
        return carve(plan, claim, owner, rural, width, height, null);
    }

    /**
     * As above, with ground no layout road is laid across ({@code barred}, or null for none): sealed ground and the
     * knolls' rings ({@code elevation-nouns.md}), which a road reaches beyond only by the passes and gates already
     * cut through them.
     */
    public static List<Laid> carve(PrecinctPlan plan, int[][] claim, int[][] owner,
                                   RuralLayout rural, int width, int height, boolean[][] barred) {
        List<Laid> out = new ArrayList<>();
        boolean[][] held = plan.layout().keepClear(width, height);
        int[][] bounds = null;
        for (Piece piece : plan.layout().pieces()) {
            if (!(piece instanceof Road r)) continue;
            if (bounds == null) bounds = bounds(claim, plan.precincts().size(), width, height);
            int[] a = resolve(plan, claim, owner, bounds, r.from(), width, height);
            int[] b = resolve(plan, claim, owner, bounds, r.to(), width, height);
            if (a == null || b == null) {
                out.add(Laid.refused(r.name(), Laid.Kind.ROAD, r.required(),
                        "no place " + (a == null ? r.from().place() : r.to().place())));
                continue;
            }
            int[] v = r.via() == null ? null : resolve(plan, claim, owner, bounds, r.via(), width, height);
            if (r.via() != null && v == null) {
                out.add(Laid.refused(r.name(), Laid.Kind.ROAD, r.required(), "no place " + r.via().place()));
                continue;
            }
            int who = index(plan, r.from().place());
            if (v == null) a = inLine(a, r.from(), bounds[who], b, width, height);
            // A road to a walled square place's gate ends at the place's ground, which the place's wall opens onto
            // or the place's arms reach out to: that is where it joins the network, even where no other road runs
            // near. (Carried on into the place, onto its outline, it gave the wall a gap it could not dress.)
            int toGate = entered(plan, r.to());
            List<int[]> path = new ArrayList<>(RoadCourse.path(a, r.from().side(), v == null ? b : v));
            if (v != null) {
                List<int[]> on = RoadCourse.path(v, r.from().side(), b);
                path.addAll(on.subList(1, on.size()));
            }
            List<int[]> course = RoadCourse.cells(path, r.width());
            List<int[]> carved = new ArrayList<>();
            List<int[]> anchors = new ArrayList<>();
            int planned = 0, onRoad = 0;
            for (int[] c : course) {
                int x = c[0], y = c[1];
                if (x <= 0 || y <= 0 || x >= width - 1 || y >= height - 1) continue;
                planned++;
                int ground = claim[x][y];
                if (ground != GrownTrunkPlan.UNOWNED && !(r.through() && settled(plan, ground))) continue;
                if (rural != null && rural.inCountry(x, y)) continue;
                if (held != null && held[x][y]) continue;
                if (barred != null && barred[x][y] && owner[x][y] == GrownTrunkPlan.UNOWNED) continue;
                if (owner[x][y] != GrownTrunkPlan.UNOWNED) {
                    onRoad++;
                    continue;
                }
                // Across a settled place's ground it is that place's street.
                owner[x][y] = ground == GrownTrunkPlan.UNOWNED ? who : ground;
                carved.add(c);
                if (toGate >= 0 && beside(claim, toGate, x, y, width, height)) anchors.add(c);
                // A road laid through that reaches the map's edge is a road off the map, joined by the weld.
                if (r.through() && (x == 1 || y == 1 || x == width - 2 || y == height - 2)) anchors.add(c);
            }
            // A stretch the places' ground cut off from the network is not left as a stranded road, nor welded
            // across somebody's ground to reach it: it is not laid.
            carved = connected(owner, carved, anchors, width, height);
            // Its wall opens onto the road, so the road is carried on through the gate to the place's own roads: a
            // gate onto ground with no road behind it leaves the road a stub from the map's edge to a wall.
            if (toGate >= 0 && !carved.isEmpty()) {
                enter(claim, owner, barred, toGate, besideOf(claim, toGate, carved, width, height), width, height);
            }
            if (carved.isEmpty()) {
                out.add(Laid.refused(r.name(), Laid.Kind.ROAD, r.required(), "no open country on its course"));
                continue;
            }
            Laid.Status status = carved.size() + onRoad >= MissionLayoutStage.WHOLE * planned ? Laid.Status.LAID : Laid.Status.PARTIAL;
            String reason = status == Laid.Status.LAID ? ""
                    : "carved " + carved.size() + " of " + planned + " cells (" + onRoad + " already road); the rest is other ground's";
            out.add(new Laid(r.name(), Laid.Kind.ROAD, status, reason, r.required(), carved,
                    List.of(a, b), List.of()));
        }
        return out;
    }

    /**
     * Where a road leaves {@code from}'s side in line with where it is going ({@code b}): a road leaving a place's
     * side with no gate and no offset across it may leave from anywhere along that side, so it leaves opposite its
     * other end where that lies within the side's span, and runs to it straight rather than by a dog-leg whose
     * cross leg is a jog of a few cells. A road off the map's edge to a gate runs straight in from the edge.
     * Otherwise {@code a} as resolved.
     */
    static int[] inLine(int[] a, Endpoint from, int[] bounds, int[] b, int width, int height) {
        if (from.gate()) return a;
        Side side = from.side();
        if (side.dy != 0) {
            if (from.dx() != 0 || b[0] < bounds[0] || b[0] > bounds[2]) return a;
            return new int[]{Math.max(1, Math.min(width - 2, b[0])), a[1]};
        }
        if (from.dy() != 0 || b[1] < bounds[1] || b[1] > bounds[3]) return a;
        return new int[]{a[0], Math.max(1, Math.min(height - 2, b[1]))};
    }

    /**
     * The carved cells joined to the rest of the network through road, or to an {@code anchor}: a cell carved against
     * the ground of the walled place the road leads to, whose gate or arms it meets there. The others are given back
     * to open ground. Pre-existing road is every road cell not carved.
     */
    static List<int[]> connected(int[][] owner, List<int[]> carved, List<int[]> anchors, int width, int height) {
        boolean[][] mine = new boolean[width][height];
        for (int[] c : carved) mine[c[0]][c[1]] = true;
        boolean[][] seen = new boolean[width][height];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int[] c : anchors) {
            if (!mine[c[0]][c[1]] || seen[c[0]][c[1]]) continue;
            seen[c[0]][c[1]] = true;
            open.add(c);
        }
        for (int[] c : carved) {
            for (int[] d : STEPS) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= width || y >= height || mine[x][y]) continue;
                if (owner[x][y] != GrownTrunkPlan.UNOWNED && !seen[c[0]][c[1]]) {
                    seen[c[0]][c[1]] = true;
                    open.add(c);
                }
            }
        }
        while (!open.isEmpty()) {
            int[] c = open.poll();
            for (int[] d : STEPS) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= width || y >= height || !mine[x][y] || seen[x][y]) continue;
                seen[x][y] = true;
                open.add(new int[]{x, y});
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int[] c : carved) {
            if (seen[c[0]][c[1]]) out.add(c);
            else owner[c[0]][c[1]] = GrownTrunkPlan.UNOWNED;
        }
        return out;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The cells of {@code carved} beside {@code who}'s ground. */
    static List<int[]> besideOf(int[][] claim, int who, List<int[]> carved, int width, int height) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : carved) if (beside(claim, who, c[0], c[1], width, height)) out.add(c);
        return out;
    }

    /**
     * Carries a road that ends against {@code who}'s ground at {@code mouth} on through it to {@code who}'s own
     * roads: the shortest way across its ground from the mouth to the nearest of them, cut
     * {@link PrecinctBoundary#DRIVABLE_GATE_WIDTH} wide as that place's road, so the gate its wall opens there leads
     * somewhere. Nothing is cut where a road of the place already meets the mouth, or where the place has no roads
     * of its own to reach. Returns the cells cut.
     */
    static List<int[]> enter(int[][] claim, int[][] owner, boolean[][] barred, int who, List<int[]> mouth,
                             int width, int height) {
        List<int[]> cut = new ArrayList<>();
        if (mouth.isEmpty()) return cut;
        int[][] start = new int[width][height];
        for (int[] column : start) Arrays.fill(column, -1);
        for (int[] c : mouth) {
            for (int[] d : STEPS) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= width || y >= height || claim[x][y] != who) continue;
                if (owner[x][y] != GrownTrunkPlan.UNOWNED) return cut;
            }
        }
        // From the middle of the mouth, so the way in is centred on the road that reaches the gate; a mouth with no
        // middle (a road one or two cells wide there) starts from all of it.
        for (boolean middle : new boolean[]{true, false}) {
            for (int[] c : mouth) {
                if (c[0] < 0 || c[1] < 0 || c[0] >= width || c[1] >= height || claim[c[0]][c[1]] == who) continue;
                if (!middle || inMouth(mouth, c[0] - 1, c[1]) && inMouth(mouth, c[0] + 1, c[1])
                        || inMouth(mouth, c[0], c[1] - 1) && inMouth(mouth, c[0], c[1] + 1)) start[c[0]][c[1]] = 0;
            }
            boolean any = false;
            for (int[] c : mouth) any |= c[0] >= 0 && c[1] >= 0 && c[0] < width && c[1] < height && start[c[0]][c[1]] == 0;
            if (any) break;
        }
        // The way in starts outside the gate and pays for its bends, so it runs straight in through the gate and
        // turns cleanly onto the place's road, rather than turning along the wall's inside or stepping a staircase
        // across the yard toward a road that lies off both axes.
        BendFlood flood = BendFlood.from(start, (x, y) -> claim[x][y] != who
                || barred != null && barred[x][y] && owner[x][y] == GrownTrunkPlan.UNOWNED ? -1 : 1, BendFlood.BEND);
        int[] found = null;
        int best = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (claim[x][y] != who || owner[x][y] == GrownTrunkPlan.UNOWNED) continue;
            int c = flood.cost(x, y);
            if (c < best) { best = c; found = new int[]{x, y}; }
        }
        if (found == null) return cut;
        List<int[]> way = flood.back(found[0], found[1]);
        int half = PrecinctBoundary.DRIVABLE_GATE_WIDTH / 2;
        for (int[] c : way.subList(1, way.size())) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dy = -half; dy <= half; dy++) {
                    int x = c[0] + dx, y = c[1] + dy;
                    if (x < 0 || y < 0 || x >= width || y >= height || claim[x][y] != who
                            || owner[x][y] != GrownTrunkPlan.UNOWNED) continue;
                    if (barred != null && barred[x][y]) continue;
                    owner[x][y] = who;
                    cut.add(new int[]{x, y});
                }
            }
        }
        return cut;
    }

    private static boolean inMouth(List<int[]> mouth, int x, int y) {
        for (int[] c : mouth) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    /**
     * The index of the walled square place ({@link Fortification#square}) an endpoint leads to through its gate: a
     * road that reaches its ground is joined to it, since its wall opens onto the road or its arms reach out to it;
     * {@code -1} for any other endpoint.
     */
    static int entered(PrecinctPlan plan, Endpoint e) {
        if (!e.gate()) return -1;
        int who = index(plan, e.place());
        if (who < 0) return -1;
        Precinct place = plan.precincts().get(who);
        return place.boundary() == Precinct.Boundary.WALLED && place.fortification() != null
                && place.fortification().square() ? who : -1;
    }

    /** Whether place {@code who} is settled ground a road laid through may cross: a place with no wall round it. */
    static boolean settled(PrecinctPlan plan, int who) {
        if (who < 0 || who >= plan.precincts().size()) return false;
        Precinct place = plan.precincts().get(who);
        return !place.isProgrammed() && place.boundary() != Precinct.Boundary.WALLED;
    }

    /** Whether {@code (x, y)} touches {@code who}'s ground along an edge. */
    static boolean beside(int[][] claim, int who, int x, int y, int width, int height) {
        for (int[] d : STEPS) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < width && ny < height && claim[nx][ny] == who) return true;
        }
        return false;
    }

    /** Where an endpoint lies on the grown ground, as {@link #carve} resolves it; {@code null} for no such place. */
    static int[] endpoint(PrecinctPlan plan, int[][] claim, int[][] owner, Endpoint e, int width, int height) {
        return resolve(plan, claim, owner, bounds(claim, plan.precincts().size(), width, height), e, width, height);
    }

    private static int[] resolve(PrecinctPlan plan, int[][] claim, int[][] owner, int[][] bounds, Endpoint e,
                                 int width, int height) {
        int who = index(plan, e.place());
        if (who < 0 || bounds[who][2] < bounds[who][0]) return null;
        List<List<int[]>> gates = new ArrayList<>();
        if (e.gate()) {
            int max = plan.precincts().get(who).fortification() == null ? Integer.MAX_VALUE
                    : plan.precincts().get(who).fortification().gates();
            for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, who, width, height, max)) {
                gates.add(gate.cells());
            }
        }
        int[] p = RoadCourse.point(bounds[who], e.side(), gates, e.dx(), e.dy(),
                (x, y) -> x >= 0 && y >= 0 && x < width && y < height && claim[x][y] == who);
        p[0] = Math.max(1, Math.min(width - 2, p[0]));
        p[1] = Math.max(1, Math.min(height - 2, p[1]));
        return p;
    }

    private static int index(PrecinctPlan plan, String place) {
        for (int i = 0; i < plan.precincts().size(); i++) if (plan.precincts().get(i).name().equals(place)) return i;
        return -1;
    }

    private static int[][] bounds(int[][] claim, int places, int width, int height) {
        int[][] out = new int[places][];
        for (int i = 0; i < places; i++) out[i] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int w = claim[x][y];
                if (w < 0 || w >= places) continue;
                out[w][0] = Math.min(out[w][0], x);
                out[w][1] = Math.min(out[w][1], y);
                out[w][2] = Math.max(out[w][2], x);
                out[w][3] = Math.max(out[w][3], y);
            }
        }
        return out;
    }
}
