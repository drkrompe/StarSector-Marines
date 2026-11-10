package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A volcanic town's answer to its lava river ({@code voids.md}, Lava flows), GL-free: which end of the river the melt
 * comes from, and the diversion berm the town raised against it. The river is fed from its end farthest from the
 * town, so the melt runs down past it; the berm is an engineered bank one tier high on the town's side of the river,
 * upstream of the town where there is room and slanting downstream off square to the river, so a breakout coming
 * down that bank meets its face and is turned from the streets; where the town crowds the river it swings round to
 * a levee alongside. An older flow that broke out above the berm is laid ending against it ({@link #aim}).
 */
public final class LavaBerm {

    /**
     * The berm's width across, cells, and its ramp's: a one-tier engineered bank, the storm bank's
     * ({@link StormBank#THICKNESS}); a slimmer berm leaves its ramp no crest to climb to.
     */
    public static final int THICKNESS = StormBank.THICKNESS, RAMP = StormBank.RAMP;
    /** The berm's lengths tried, cells: the shortest still a run the ridge planner raises. UNTUNED. */
    static final int[] LENGTHS = {40, 32, 27};
    /** How far from the river the berm's middle may stand, cells. UNTUNED. */
    static final int NEAREST = 10, FARTHEST = 24;
    /** The slant off square to the river a berm is best at, degrees: a diversion wall. UNTUNED. */
    static final int BEST_SLANT = 40;
    /** How far from the town's own ground the berm's middle may stand, cells. UNTUNED. */
    static final int TOWN_REACH = 20;
    /** How much a cell farther from the town counts against a placement, against a cell more of berm's two. UNTUNED. */
    static final double TOWN_PULL = .6;
    /** How much a degree off {@link #BEST_SLANT} counts against a placement. UNTUNED. */
    static final double SLANT_PULL = .4;
    /** Headings tried, degrees apart. */
    static final int HEADING_STEP = 15;
    /** How far upstream of the berm's near end the older flow aimed at it breaks out, cells. UNTUNED. */
    static final int AIM_UPSTREAM = 10;
    /** The planner's apron round a band's faces, as {@link RidgePlanner#heldGround} counts it. */
    private static final int APRON = PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1;

    private LavaBerm() { }

    /** The berm (a {@link RidgePlanner.Relief#BERM} band) and the river's source, as a point near its far end. */
    public record Plan(RidgePlanner.Band band, float sourceX, float sourceY) { }

    /** The river's cell farthest from the town at {@code (townX, townY)}: the melt comes from there. Null with none. */
    public static int[] source(int w, int h, boolean[] lava, int townX, int townY) {
        int[] best = null;
        long far = -1;
        for (int i = 0; i < lava.length; i++) {
            if (!lava[i]) continue;
            long dx = i % w - townX, dy = i / w - townY, d = dx * dx + dy * dy;
            if (d > far) { far = d; best = new int[]{i % w, i / w}; }
        }
        return best;
    }

    /**
     * The berm between the river ({@code lava}) and the town seeded at {@code (townX, townY)}, whose own ground is
     * {@code town}, the river fed from {@link #source}: of every placement on the town's side of the river, its middle
     * {@link #NEAREST} to {@link #FARTHEST} cells off it and within {@link #TOWN_REACH} of the town's ground, every
     * heading and every length, the best whose held ground
     * ({@link RidgePlanner#heldGround}) {@code free} allows, weighing its length, its nearness to the town, its standing
     * upstream of the town's nearest point on the river and slanting downstream from it, and its slant near
     * {@link #BEST_SLANT} off square to the river. Null when none fits.
     */
    public static Plan plan(int w, int h, boolean[] lava, int townX, int townY, IntPredicate town,
                            IntPredicate free) {
        List<Plan> ranked = candidates(w, h, lava, townX, townY, town, free, 1);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /**
     * The {@code most} best berms {@link #plan} would choose among, best first, each on ground {@code free} allows:
     * for a caller that asks more of a berm than its ground (the ridge planner's own proof) to take the first it
     * accepts.
     */
    public static List<Plan> candidates(int w, int h, boolean[] lava, int townX, int townY, IntPredicate town,
                                        IntPredicate free, int most) {
        int[] src = source(w, h, lava, townX, townY);
        if (src == null) return List.of();
        int[] near = nearest(w, lava, townX, townY);
        double ux = townX - near[0], uy = townY - near[1], toTown = Math.hypot(ux, uy);
        if (toTown < 1e-6) return List.of();
        ux /= toTown;
        uy /= toTown;
        // Along the river toward its source, square to the way to the town.
        double sx = src[0] - near[0], sy = src[1] - near[1];
        double along = sx * ux + sy * uy;
        double tx = sx - along * ux, ty = sy - along * uy, tl = Math.hypot(tx, ty);
        if (tl < 1e-6) return List.of();
        tx /= tl;
        ty /= tl;
        float[] clear = clearance(w, h, free);
        float[] fromLava = clearance(w, h, i -> !lava[i]);
        float[] fromTown = clearance(w, h, town.negate());
        int reach = (int) Math.ceil(THICKNESS / 2.0) + APRON;
        List<double[]> scored = new ArrayList<>();
        List<RidgePlanner.Band> bands = new ArrayList<>();
        for (int length : LENGTHS) {
            for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++) {
                int ci = cy * w + cx;
                float off = fromLava[ci];
                if (off < NEAREST || off > FARTHEST || clear[ci] <= reach || fromTown[ci] > TOWN_REACH) continue;
                // On the town's side of the river: toward the town from the river's nearest point.
                if ((cx - near[0]) * ux + (cy - near[1]) * uy <= 0) continue;
                double up = (cx - near[0]) * tx + (cy - near[1]) * ty;
                for (int heading = 0; heading < 180; heading += HEADING_STEP) {
                    double hx = Math.cos(Math.toRadians(heading)), hy = Math.sin(Math.toRadians(heading));
                    double ax = cx + .5 - hx * length / 2.0, ay = cy + .5 - hy * length / 2.0;
                    double bx = cx + .5 + hx * length / 2.0, by = cy + .5 + hy * length / 2.0;
                    if (!inside(ax, ay, w, h) || !inside(bx, by, w, h)) continue;
                    // Square to the river is along the way to the town; the slant is how far the berm turns off it.
                    double slant = Math.toDegrees(Math.acos(Math.min(1, Math.abs(hx * ux + hy * uy))));
                    // The berm runs from its river end, the end nearer the river.
                    boolean aFirst = fromLava[cell(w, h, ax, ay)] <= fromLava[cell(w, h, bx, by)];
                    double px = aFirst ? ax : bx, py = aFirst ? ay : by, qx = aFirst ? bx : ax, qy = aFirst ? by : ay;
                    double dx = qx - px, dy = qy - py;
                    // A diversion wall slants downstream from the river, so what comes down the bank is turned away.
                    boolean downstream = dx * tx + dy * ty < 0;
                    double score = length * 2 + Math.max(-20, Math.min(20, up)) * .5
                            - Math.abs(slant - BEST_SLANT) * SLANT_PULL - (downstream ? 0 : 6)
                            - fromTown[ci] * TOWN_PULL;
                    if (!clearAlong(clear, w, h, ax, ay, bx, by, reach)) continue;
                    // The ramp climbs from the berm's downstream face, away from the melt's source.
                    int side = (dx * (src[1] - py) - dy * (src[0] - px)) > 0 ? -1 : 1;
                    RidgePlanner.Band band = new RidgePlanner.Band(
                            List.of(new Point(px, py), new Point(qx, qy)), THICKNESS, side, RAMP).berm();
                    scored.add(new double[]{score, bands.size()});
                    bands.add(band);
                }
            }
        }
        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        List<Plan> out = new ArrayList<>();
        for (double[] sc : scored) {
            if (out.size() >= most) break;
            RidgePlanner.Band band = bands.get((int) sc[1]);
            if (fits(band, w, h, free)) out.add(new Plan(band, src[0] + .5f, src[1] + .5f));
        }
        return out;
    }

    /**
     * Where the older flow that ponds against the berm breaks out of the river, and its heading: from the town bank's
     * cell nearest a point {@link #AIM_UPSTREAM} cells upstream of the berm's river end, toward the berm's middle.
     * {@code {x, y, heading in radians, cells to the berm's middle}}; null when the river has no such bank.
     */
    public static float[] aim(int w, int h, boolean[] lava, Plan plan, IntPredicate bank) {
        Point a = plan.band().path().get(0), b = plan.band().path().get(1);
        double tx = plan.sourceX() - a.x(), ty = plan.sourceY() - a.y(), tl = Math.hypot(tx, ty);
        if (tl < 1e-6) return null;
        double px = a.x() + tx / tl * AIM_UPSTREAM, py = a.y() + ty / tl * AIM_UPSTREAM;
        double mx = (a.x() + b.x()) / 2, my = (a.y() + b.y()) / 2;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < lava.length; i++) {
            if (lava[i] || !bank.test(i)) continue;
            int x = i % w, y = i / w;
            boolean edge = false;
            for (int k = 0; k < 4 && !edge; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                edge = nx >= 0 && ny >= 0 && nx < w && ny < h && lava[ny * w + nx];
            }
            if (!edge) continue;
            double d = (x - px) * (x - px) + (y - py) * (y - py);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (best < 0) return null;
        float x = best % w + .5f, y = best / w + .5f;
        return new float[]{x, y, (float) Math.atan2(my - y, mx - x), (float) Math.hypot(mx - x, my - y)};
    }

    /** The berm's own cells, as a mask: those within half its thickness of its line. What a flow ponds against. */
    public static boolean[] core(Plan plan, int w, int h) {
        Point a = plan.band().path().get(0), b = plan.band().path().get(1);
        double r = THICKNESS / 2.0 + .5, dx = b.x() - a.x(), dy = b.y() - a.y(), len2 = dx * dx + dy * dy;
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double px = x + .5 - a.x(), py = y + .5 - a.y();
            double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / len2));
            double ex = px - dx * t, ey = py - dy * t;
            out[y * w + x] = ex * ex + ey * ey <= r * r;
        }
        return out;
    }

    /** Each cell's distance to the nearest cell {@code open} refuses, about Euclidean (chamfer 1 and 1.414). */
    public static float[] clearance(int w, int h, IntPredicate open) {
        float[] d = new float[w * h];
        float big = w + h;
        for (int i = 0; i < d.length; i++) d[i] = open.test(i) ? big : 0f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (x > 0) d[i] = Math.min(d[i], d[i - 1] + 1f);
            if (y > 0) d[i] = Math.min(d[i], d[i - w] + 1f);
            if (x > 0 && y > 0) d[i] = Math.min(d[i], d[i - w - 1] + 1.4142f);
            if (x < w - 1 && y > 0) d[i] = Math.min(d[i], d[i - w + 1] + 1.4142f);
        }
        for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--) {
            int i = y * w + x;
            if (x < w - 1) d[i] = Math.min(d[i], d[i + 1] + 1f);
            if (y < h - 1) d[i] = Math.min(d[i], d[i + w] + 1f);
            if (x < w - 1 && y < h - 1) d[i] = Math.min(d[i], d[i + w + 1] + 1.4142f);
            if (x > 0 && y < h - 1) d[i] = Math.min(d[i], d[i + w - 1] + 1.4142f);
        }
        return d;
    }

    /** Whether every point along a line, a cell apart, has more than {@code reach} cells of clearance. */
    private static boolean clearAlong(float[] clear, int w, int h, double ax, double ay, double bx, double by,
                                      int reach) {
        double len = Math.hypot(bx - ax, by - ay);
        for (double t = 0; t <= len; t += 1) {
            double x = ax + (bx - ax) * t / len, y = ay + (by - ay) * t / len;
            if (clear[cell(w, h, x, y)] <= reach) return false;
        }
        return true;
    }

    private static int cell(int w, int h, double x, double y) {
        int cx = Math.max(0, Math.min(w - 1, (int) Math.floor(x)));
        int cy = Math.max(0, Math.min(h - 1, (int) Math.floor(y)));
        return cy * w + cx;
    }

    private static boolean fits(RidgePlanner.Band band, int w, int h, IntPredicate free) {
        boolean[][] held = RidgePlanner.heldGround(List.of(band), w, h, 0);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            if (held[x][y] && !free.test(y * w + x)) return false;
        }
        return true;
    }

    private static int[] nearest(int w, boolean[] lava, int x0, int y0) {
        int[] best = null;
        long near = Long.MAX_VALUE;
        for (int i = 0; i < lava.length; i++) {
            if (!lava[i]) continue;
            long dx = i % w - x0, dy = i / w - y0, d = dx * dx + dy * dy;
            if (d < near) { near = d; best = new int[]{i % w, i / w}; }
        }
        return best;
    }

    private static boolean inside(double x, double y, int w, int h) {
        return x >= 1 && y >= 1 && x < w - 1 && y < h - 1;
    }
}
