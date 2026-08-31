package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.Plan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.SubRect;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkSegment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Grows a city's road skeleton as a recursive junction graph instead of
 * stamping {@link TrunkPlan}'s single fixed crossroad.
 *
 * <p>The unit of growth is a <b>junction</b> — a point of degree 3 or 4 — not a
 * tile. Each arm draws its own length and carries its own road class, so
 * nothing in the output repeats at a constant pitch; what stays fixed is only
 * that junctions have three or four ways out, which is true of real ones. Arms
 * terminate three ways: into an existing band (a T), at the map edge (a
 * perimeter exit), or at a fresh junction one class down. Growth stops when the
 * junction budget runs out, which is the whole density knob.
 *
 * <p><b>Unreached ground is the point, not a failure.</b> A budget that cannot
 * fill the map leaves large open regions between the branches it did grow.
 * Those become {@link Result#hinterland} rather than sub-rects, so nobody
 * partitions them into city blocks — which is how one generator produces a
 * city, a town, and a road through farmland without a separate map family or a
 * hardcoded biome band.
 *
 * <p>What separates the two is {@link Profile#frontageDepth} — distance from a
 * grown band — and not the size of the region. Sorting whole maximal rectangles
 * by area was tried first and does not produce settlement: on a sparsely grown
 * map the largest free rectangle is half the map, so it came out as a built
 * half and a field half rather than as buildings along the roads. Depth from a
 * street is what "frontage" actually means.
 *
 * <p><b>An arm that reaches the edge is painted all the way to it.</b>
 * {@code RoadGraphBuilder} promotes a perimeter cell to a convoy entry node
 * only when the band inside it is wide enough to carry a centerline of depth
 * three, so an arm that stopped one cell short of the perimeter would cost the
 * map every off-map entry point without failing anything.
 *
 * <p>Output is a drop-in {@link Plan}, so {@code BspPartitionStage} and every
 * other {@link BspKeys#TRUNK_PLAN} consumer works unchanged.
 */
public final class GrownTrunkPlan {

    /** Density and shape knobs for one growth pass. */
    public static final class Profile {
        /** Junctions to spend before growth stops. The primary density control. */
        public final int junctionBudget;
        /** Chance an arm that ran its full length ends in a new junction rather than a dead end. */
        public final float branchChance;
        /** Arm length bounds, as a fraction of the map's shorter dimension. */
        public final float armLenLoFrac;
        public final float armLenHiFrac;
        /** Chance a junction is 4-way rather than 3-way. */
        public final float fourWayChance;
        /**
         * How deep from a road building may reach. Ground further than this
         * from any band is hinterland. {@link Integer#MAX_VALUE} builds
         * everywhere, which is the dense-city case.
         *
         * <p>This is a depth rather than an area threshold because sorting
         * whole maximal rectangles by size does not produce settlement: on a
         * sparsely grown map the largest free rectangle is half the map, so
         * the map came out as a built side and a field side rather than as
         * buildings along the roads with open country behind them.
         */
        public final int frontageDepth;
        /**
         * How this settlement joins the rest of its world. {@link SettlementLink#ROAD}
         * makes growth guarantee an arterial off the map edge even when it never
         * grew that far on its own.
         */
        public final SettlementLink link;

        public Profile(int junctionBudget, float branchChance, float armLenLoFrac,
                       float armLenHiFrac, float fourWayChance, int frontageDepth) {
            this(junctionBudget, branchChance, armLenLoFrac, armLenHiFrac,
                    fourWayChance, frontageDepth, SettlementLink.ROAD);
        }

        public Profile(int junctionBudget, float branchChance, float armLenLoFrac,
                       float armLenHiFrac, float fourWayChance, int frontageDepth,
                       SettlementLink link) {
            this.junctionBudget = junctionBudget;
            this.branchChance = branchChance;
            this.armLenLoFrac = armLenLoFrac;
            this.armLenHiFrac = armLenHiFrac;
            this.fourWayChance = fourWayChance;
            this.frontageDepth = frontageDepth;
            this.link = (link == null) ? SettlementLink.ROAD : link;
        }

        /** Arm length does not vary with density — see {@link #of}. */
        private static final float ARM_LO_FRAC = 0.14f;
        private static final float ARM_HI_FRAC = 0.30f;

        /** At or above this density the map is built everywhere and has no hinterland. */
        private static final float FULLY_BUILT_AT = 0.95f;

        /**
         * The single density control: 0 is a road through open country, 1 is
         * dense urban sprawl. Everything else is derived, because hand-tuned
         * profiles disagreed with each other.
         *
         * <p><b>Arm length is deliberately held constant.</b> It is the one
         * parameter that must not scale with density, and scaling it is what
         * made the first three profiles non-monotonic: a sparse profile with
         * longer arms spreads a thin ribbon of frontage across the whole map
         * instead of making a smaller settlement, so it measured as
         * <em>denser</em> than the profile above it on some seeds. Fewer
         * junctions at a fixed reach is what "smaller town" means; longer reach
         * at fewer junctions just means "same city, worse roads".
         */
        public static Profile of(float density) {
            return of(density, SettlementLink.ROAD);
        }

        /** As {@link #of(float)}, for a settlement whose lifeline is stated rather than assumed. */
        public static Profile of(float density, SettlementLink link) {
            float d = Math.max(0f, Math.min(1f, density));
            return new Profile(
                    Math.round(lerp(3f, 20f, d)),
                    lerp(0.45f, 0.90f, d),
                    ARM_LO_FRAC,
                    ARM_HI_FRAC,
                    lerp(0.25f, 0.55f, d),
                    d >= FULLY_BUILT_AT ? Integer.MAX_VALUE : Math.round(lerp(6f, 24f, d)),
                    link);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        /** Dense urban sprawl — the closest analogue to what BSP produces today. */
        public static Profile city() {
            return of(1.0f);
        }

        /** A settlement with open ground around it. */
        public static Profile town() {
            return of(0.55f);
        }

        /** A road or two through mostly open country. */
        public static Profile hamlet() {
            return of(0.2f);
        }
    }

    /** A grown plan plus the ground growth never reached. */
    public static final class Result {
        public final Plan plan;
        /** Open regions left unpartitioned — fields, scrub, whatever the terrain pass makes of them. */
        public final List<SubRect> hinterland;

        public Result(Plan plan, List<SubRect> hinterland) {
            this.plan = plan;
            this.hinterland = hinterland;
        }
    }

    /** Class ladder. A junction's children grow one step down; the bottom rung repeats. */
    private static final TrunkKind[] LADDER = { TrunkKind.PRIMARY, TrunkKind.SECONDARY };

    /**
     * Minimum gap between a new junction and any unrelated existing band, so
     * parallel roads never touch.
     *
     * <p>It must stay below {@link #MIN_ARM_LEN} minus a band's half-width, or
     * a junction at the end of a shortest arm is still inside its own parent
     * junction's clearance and gets refused. Set to six against a seven-cell
     * minimum arm, that refused every grandchild on the map and growth stopped
     * dead at depth two while reporting a budget of eighteen.
     */
    private static final int JUNCTION_CLEARANCE = 4;

    /** Shortest arm the growth will draw. See {@link #JUNCTION_CLEARANCE} for why it has a floor. */
    private static final int MIN_ARM_LEN = 9;

    /** Smallest rect worth handing to BSP; below this it is just road verge. */
    private static final int MIN_SUBRECT_DIM = Bsp.LEAF_MIN;

    private GrownTrunkPlan() {}

    private static final class Junction {
        final int x, y, depth;
        Junction(int x, int y, int depth) { this.x = x; this.y = y; this.depth = depth; }
    }

    public static Result generate(int width, int height, Random rng, Profile profile) {
        boolean[][] road = new boolean[width][height];
        for (int x = 0; x < width; x++)  { road[x][0] = true; road[x][height - 1] = true; }
        for (int y = 0; y < height; y++) { road[0][y] = true; road[width  - 1][y] = true; }

        // Painted arm bands only. The perimeter ring is a map-edge reservation
        // rather than a street anyone builds along, and measuring frontage from
        // it makes every cell on the map near a road: the hinterland collapses
        // to whatever sliver sits further than the depth from the border.
        boolean[][] bands = new boolean[width][height];
        List<TrunkSegment> trunks = new ArrayList<>();
        int shortDim = Math.min(width, height);
        int lenLo = Math.max(MIN_ARM_LEN, Math.round(shortDim * profile.armLenLoFrac));
        int lenHi = Math.max(lenLo + 1, Math.round(shortDim * profile.armLenHiFrac));

        // Seed junction, jittered across the middle third so the centre is not a tell.
        int seedX = width  / 3 + rng.nextInt(Math.max(1, width  / 3));
        int seedY = height / 3 + rng.nextInt(Math.max(1, height / 3));
        SubRect centre = bandRect(seedX, seedY, TrunkKind.PRIMARY.width, width, height);

        Deque<Junction> frontier = new ArrayDeque<>();
        frontier.add(new Junction(seedX, seedY, 0));
        int spent = 0;

        while (!frontier.isEmpty() && spent < profile.junctionBudget) {
            Junction j = frontier.poll();
            spent++;
            TrunkKind kind = LADDER[Math.min(j.depth, LADDER.length - 1)];

            boolean[] dirs = pickDirections(rng, profile.fourWayChance);
            for (int d = 0; d < 4; d++) {
                if (!dirs[d]) continue;
                int drawn = lenLo + rng.nextInt(lenHi - lenLo + 1);
                Arm arm = walkArm(road, width, height, j.x, j.y, d, drawn, kind.width);
                if (arm == null) continue;
                paintBand(road, arm.rect);
                paintBand(bands, arm.rect);
                trunks.add(new TrunkSegment(arm.rect.x0, arm.rect.y0, arm.rect.x1, arm.rect.y1,
                        kind, d == DIR_E || d == DIR_W));

                boolean canBranch = arm.ranFull
                        && spent + frontier.size() < profile.junctionBudget
                        && rng.nextFloat() < profile.branchChance
                        && clearFor(road, width, height, arm.endX, arm.endY, arm.rect);
                if (canBranch) frontier.add(new Junction(arm.endX, arm.endY, j.depth + 1));
            }
        }

        if (profile.link == SettlementLink.ROAD && !anySegmentLeavesMap(trunks, width, height)) {
            linkOffMap(road, bands, trunks, seedX, seedY, width, height, rng);
        }

        List<SubRect> subRects = new ArrayList<>();
        List<SubRect> hinterland = new ArrayList<>();
        boolean[][] beyondFrontage = beyondFrontage(bands, width, height, profile.frontageDepth);
        decompose(orOf(road, beyondFrontage, width, height), width, height, subRects);
        decompose(orOf(road, notOf(beyondFrontage, width, height), width, height), width, height, hinterland);

        return new Result(new Plan(road, subRects, trunks, centre, width, height), hinterland);
    }

    // ---- arm growth -------------------------------------------------------

    private static final int DIR_N = 0, DIR_E = 1, DIR_S = 2, DIR_W = 3;
    private static final int[] DX = { 0, 1, 0, -1 };
    private static final int[] DY = { 1, 0, -1, 0 };

    private static final class Arm {
        final SubRect rect; final int endX, endY; final boolean ranFull;
        Arm(SubRect rect, int endX, int endY, boolean ranFull) {
            this.rect = rect; this.endX = endX; this.endY = endY; this.ranFull = ranFull;
        }
    }

    /**
     * Walks one arm outward along its centreline. Stops one cell past first
     * contact with an existing band so the two actually join rather than
     * leaving a one-cell seam, and runs clean through to the map edge when it
     * gets that far — see the class note on perimeter exits.
     */
    private static Arm walkArm(boolean[][] road, int w, int h, int cx, int cy,
                               int dir, int drawn, int bandWidth) {
        int hw = bandWidth / 2;
        int steps = 0;
        int x = cx, y = cy;
        boolean ranFull = true;
        boolean reachedEdge = false;
        for (int i = 1; i <= drawn; i++) {
            int nx = cx + DX[dir] * i;
            int ny = cy + DY[dir] * i;
            if (nx <= hw || ny <= hw || nx >= w - 1 - hw || ny >= h - 1 - hw) {
                reachedEdge = true;
                ranFull = false;
                break;
            }
            x = nx; y = ny; steps = i;
            // First contact with a band we did not start on ends the arm as a T.
            if (i > hw + 1 && road[nx][ny]) { ranFull = false; break; }
        }
        if (steps < MIN_SUBRECT_DIM && !reachedEdge) return null;

        int x0 = Math.min(cx, x), x1 = Math.max(cx, x);
        int y0 = Math.min(cy, y), y1 = Math.max(cy, y);
        if (dir == DIR_E || dir == DIR_W) { y0 -= hw; y1 += hw; } else { x0 -= hw; x1 += hw; }

        // Run out to the perimeter itself so the band carries a convoy entry node.
        if (reachedEdge) {
            if (dir == DIR_E) x1 = w - 1;
            else if (dir == DIR_W) x0 = 0;
            else if (dir == DIR_N) y1 = h - 1;
            else y0 = 0;
        }
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        x1 = Math.min(w - 1, x1); y1 = Math.min(h - 1, y1);
        if (x1 - x0 + 1 < MIN_SUBRECT_DIM && y1 - y0 + 1 < MIN_SUBRECT_DIM) return null;
        return new Arm(new SubRect(x0, y0, x1, y1), x, y, ranFull);
    }

    /**
     * True when a new junction here would not crowd a band it is not attached
     * to. The arm we arrived on is exempt <em>as a band</em>, not as a
     * centreline: it has already been painted at its full five or seven cells,
     * so testing only its middle row rejects every child junction on the map
     * and growth collapses to the seed's own arms.
     */
    private static boolean clearFor(boolean[][] road, int w, int h, int cx, int cy, SubRect ownArm) {
        int edgeGap = TrunkKind.PRIMARY.width;
        if (cx < edgeGap || cy < edgeGap || cx >= w - edgeGap || cy >= h - edgeGap) return false;
        for (int dy = -JUNCTION_CLEARANCE; dy <= JUNCTION_CLEARANCE; dy++) {
            for (int dx = -JUNCTION_CLEARANCE; dx <= JUNCTION_CLEARANCE; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                if (!road[x][y]) continue;
                // The perimeter ring is everywhere near an edge and would otherwise
                // veto every junction that grew outward; the edge gap above is the
                // real constraint on how close to the border a junction may sit.
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) continue;
                boolean ours = x >= ownArm.x0 && x <= ownArm.x1 && y >= ownArm.y0 && y <= ownArm.y1;
                if (!ours) return false;
            }
        }
        return true;
    }

    private static boolean[] pickDirections(Random rng, float fourWayChance) {
        boolean[] dirs = { true, true, true, true };
        if (rng.nextFloat() >= fourWayChance) dirs[rng.nextInt(4)] = false;
        return dirs;
    }

    private static SubRect bandRect(int cx, int cy, int bandWidth, int w, int h) {
        int hw = bandWidth / 2;
        return new SubRect(Math.max(0, cx - hw), Math.max(0, cy - hw),
                Math.min(w - 1, cx + hw), Math.min(h - 1, cy + hw));
    }

    private static void paintBand(boolean[][] road, SubRect r) {
        for (int y = r.y0; y <= r.y1; y++) {
            for (int x = r.x0; x <= r.x1; x++) road[x][y] = true;
        }
    }


    // ---- the off-map link -------------------------------------------------

    /** True when some band already runs out to the perimeter. */
    private static boolean anySegmentLeavesMap(List<TrunkSegment> trunks, int w, int h) {
        for (int i = 0; i < trunks.size(); i++) {
            TrunkSegment t = trunks.get(i);
            if (t.left <= 0 || t.top <= 0 || t.right >= w - 1 || t.bottom >= h - 1) return true;
        }
        return false;
    }

    /**
     * Drives one arterial from the settlement centre out to the nearest map
     * edge, so a settlement that never grew that far is still joined to the
     * planetary network.
     *
     * <p><b>It bends.</b> A road laid straight from the middle of a town to the
     * edge of the world reads as a runway rather than as a road; one right-angle
     * turn on the way reads as terrain the surveyors went around, and costs
     * nothing, since a segment is a rectangle and an L is two of them. A drawn
     * offset of zero degenerates to the straight case on its own, which is fine
     * — some roads really do run straight — so the bend is drawn rather than
     * forced.
     *
     * <p>The band is {@link TrunkKind#PRIMARY} because a link to the outside is
     * an arterial, and because the road graph only promotes a perimeter cell to
     * an off-map entry node when the band inside it is wide enough to carry a
     * centreline of any depth.
     */
    private static void linkOffMap(boolean[][] road, boolean[][] bands, List<TrunkSegment> trunks,
                                   int cx, int cy, int w, int h, Random rng) {
        int hw = TrunkKind.PRIMARY.width / 2;
        int distW = cx, distE = w - 1 - cx, distS = cy, distN = h - 1 - cy;
        int min = Math.min(Math.min(distW, distE), Math.min(distS, distN));
        int dir = (min == distE) ? DIR_E : (min == distW) ? DIR_W : (min == distN) ? DIR_N : DIR_S;

        boolean horizontal = (dir == DIR_E || dir == DIR_W);
        int span = horizontal ? h : w;
        int here = horizontal ? cy : cx;
        int lo = hw + 1, hi = span - 2 - hw;
        int bend = here;
        if (hi > lo) {
            int reach = Math.max(1, span / 6);
            bend = clamp(here - reach + rng.nextInt(2 * reach + 1), lo, hi);
        }

        // The connector along the other axis, when the bend actually moved.
        if (bend != here) {
            int runLo = Math.max(0, Math.min(here, bend) - hw);
            int runHi = Math.min(span - 1, Math.max(here, bend) + hw);
            SubRect connector = horizontal
                    ? new SubRect(cx - hw, runLo, cx + hw, runHi)
                    : new SubRect(runLo, cy - hw, runHi, cy + hw);
            emit(road, bands, trunks, clampRect(connector, w, h), !horizontal);
        }

        // The run out to the edge, on the bend's line.
        SubRect out;
        if (dir == DIR_E)      out = new SubRect(cx - hw, bend - hw, w - 1,   bend + hw);
        else if (dir == DIR_W) out = new SubRect(0,       bend - hw, cx + hw, bend + hw);
        else if (dir == DIR_N) out = new SubRect(bend - hw, cy - hw, bend + hw, h - 1);
        else                   out = new SubRect(bend - hw, 0,       bend + hw, cy + hw);
        emit(road, bands, trunks, clampRect(out, w, h), horizontal);
    }

    private static void emit(boolean[][] road, boolean[][] bands, List<TrunkSegment> trunks,
                             SubRect r, boolean horizontal) {
        paintBand(road, r);
        paintBand(bands, r);
        trunks.add(new TrunkSegment(r.x0, r.y0, r.x1, r.y1, TrunkKind.PRIMARY, horizontal));
    }

    private static SubRect clampRect(SubRect r, int w, int h) {
        return new SubRect(clamp(r.x0, 0, w - 1), clamp(r.y0, 0, h - 1),
                clamp(r.x1, 0, w - 1), clamp(r.y1, 0, h - 1));
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ---- region decomposition ---------------------------------------------

    /**
     * Manhattan distance from every cell to the nearest road, thresholded at
     * {@code depth}. True means "further from a road than building reaches".
     */
    private static boolean[][] beyondFrontage(boolean[][] road, int w, int h, int depth) {
        boolean[][] beyond = new boolean[w][h];
        if (depth == Integer.MAX_VALUE) return beyond;
        int[][] dist = new int[w][h];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (road[x][y]) queue.add(new int[] { x, y });
                else dist[x][y] = Integer.MAX_VALUE;
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d], ny = c[1] + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (dist[nx][ny] != Integer.MAX_VALUE) continue;
                dist[nx][ny] = dist[c[0]][c[1]] + 1;
                queue.add(new int[] { nx, ny });
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (dist[x][y] != Integer.MAX_VALUE && dist[x][y] > depth) beyond[x][y] = true;
            }
        }
        return beyond;
    }

    private static boolean[][] orOf(boolean[][] a, boolean[][] b, int w, int h) {
        boolean[][] out = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) out[x][y] = a[x][y] || b[x][y];
        }
        return out;
    }

    private static boolean[][] notOf(boolean[][] a, int w, int h) {
        boolean[][] out = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) out[x][y] = !a[x][y];
        }
        return out;
    }

    /** Peels a blocked mask's free space into rectangles, largest first. */
    private static void decompose(boolean[][] blocked, int w, int h, List<SubRect> out) {
        boolean[][] taken = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) taken[x][y] = blocked[x][y];
        }
        while (true) {
            SubRect best = largestFreeRect(taken, w, h);
            if (best == null) break;
            out.add(best);
            for (int y = best.y0; y <= best.y1; y++) {
                for (int x = best.x0; x <= best.x1; x++) taken[x][y] = true;
            }
        }
    }

    /** Maximal all-free axis-aligned rectangle, by the standard largest-rectangle-in-histogram sweep. */
    private static SubRect largestFreeRect(boolean[][] taken, int w, int h) {
        int[] heights = new int[w];
        int[] stack = new int[w + 1];
        int bestArea = 0;
        SubRect best = null;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) heights[x] = taken[x][y] ? 0 : heights[x] + 1;
            int top = 0;
            for (int x = 0; x <= w; x++) {
                int cur = (x == w) ? 0 : heights[x];
                while (top > 0 && heights[stack[top - 1]] >= cur) {
                    int hgt = heights[stack[--top]];
                    int left = (top == 0) ? 0 : stack[top - 1] + 1;
                    int wdt = x - left;
                    if (hgt >= MIN_SUBRECT_DIM && wdt >= MIN_SUBRECT_DIM && hgt * wdt > bestArea) {
                        bestArea = hgt * wdt;
                        best = new SubRect(left, y - hgt + 1, x - 1, y);
                    }
                }
                stack[top++] = x;
            }
        }
        return best;
    }
}
