package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctClaim;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps open ground for the forts a mission sited as barriers. A fort is stamped after the town, and by then the middle
 * of a Conquest map is a grid of roads and claimed places with no pocket big enough for a fort: ground on the approach
 * is exactly what leftover placement never finds. So, before the road network has grown and the town has claimed, the
 * ground for <em>every</em> declared fort is reserved: no road arm grows into it and the town's zoned places give up
 * their claim to it. {@code FortStage} later stands each fort on its reserved ground.
 *
 * <p>A fort's nominal ground (see {@link FortSiting}) is often a programmed place's: a lane's compounds and the garrison
 * itself claim wide ground that is not known until growth has run, and they never give it up. So, before anything is
 * grown, the programmed places' ground is estimated and each target slides along the approach (a shield fort: to the
 * nearest free ground) to a box none of them is expected to hold. The slid targets are published for the stage under
 * {@link BspKeys#FORT_TARGETS}, so the ground kept clear and the ground aimed at are one answer.
 *
 * <p>Only a zoned (town) place gives ground up. A programmed place (the keep's installation, the landing, a lane's
 * compound) is prior and keeps every cell it claimed, and a reservation never lands on one's seed. Inert unless the
 * mission declared forts and chose {@link FortSitingPolicy#COVER_THE_APPROACH}, so every other map is byte-identical to
 * one generated without it.
 */
public final class FortReservations {

    /** Clear ground kept beyond a fort's footprint on every side, in cells (a little more than the placement margin). */
    private static final int SLACK = 5;
    /** A reservation leaves the ground within this many cells of any place's seed alone: that place has to grow. */
    private static final int SEED_CLEARANCE = 14;
    /** UNTUNED: the ground a programmed place is expected to take beyond its program, as a share and a flat road allowance. */
    private static final float PROGRAM_SPREAD = 1.3f;
    private static final int PROGRAM_ROADS = 800;
    /** The part of a reserved box that must be free of programmed places, beyond the fort's own half-extent. */
    private static final int FIT_SLACK = 2;

    private FortReservations() {}

    /**
     * The battle's two ends as the plan states them: the keep's place and the landing place, by their seeds. The
     * reservation (before anything is grown) and the stage that stands the forts both read the ends from here, so the
     * ground kept clear is the ground the fort is aimed at. {@code null} when the plan states either no landing or no
     * objective.
     */
    public static FortSiting.Anchors anchors(PrecinctPlan plan, int width, int height) {
        Precinct landing = plan.landingPlace();
        Precinct objective = plan.objective();
        if (landing == null || objective == null) return null;
        return new FortSiting.Anchors(new FortSiting.Point(objective.seedX(), objective.seedY()),
                new FortSiting.Point(landing.seedX(), landing.seedY()), width, height);
    }

    /**
     * The ground reserved for each declared fort, by kind and in declaration order, computed once and kept on the
     * context: empty when the mission sited nothing or the map has no two ends.
     */
    private static Map<FortKind, List<FortSiting.Point>> sites(GenContext ctx, PrecinctPlan plan) {
        Map<FortKind, List<FortSiting.Point>> cached = ctx.get(BspKeys.FORT_TARGETS);
        if (cached != null) return cached;
        Map<FortKind, List<FortSiting.Point>> out = new EnumMap<>(FortKind.class);
        ctx.put(BspKeys.FORT_TARGETS, out);
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (profile == null) return out;
        GroundDefences declared = profile.declaredDefences();
        if (declared.siting() != FortSitingPolicy.COVER_THE_APPROACH) return out;
        FortSiting.Anchors anchors = anchors(plan, ctx.width, ctx.height);
        if (anchors == null) return out;
        int[][] sums = sums(programmedGround(plan, ctx.width, ctx.height));
        List<FortSiting.Point> taken = new ArrayList<>();
        for (FortKind kind : FortKind.values()) {
            int count = declared.fortCount(kind);
            if (count <= 0) continue;
            FortPlan fort = FortPlan.of(kind);
            int fitHalf = Math.max(fort.width(), fort.height()) / 2 + FIT_SLACK;
            boolean battery = kind == FortKind.BATTERY_FORT;
            List<FortSiting.Point> nominal = new ArrayList<>();
            if (battery) {
                nominal.addAll(FortSiting.batteryTargets(anchors, count));
            } else {
                for (int i = 0; i < count; i++) nominal.add(FortSiting.shieldTarget(anchors));
            }
            List<FortSiting.Point> chosen = new ArrayList<>();
            for (FortSiting.Point target : nominal) {
                FortSiting.Point at = FortSiting.slide(anchors, target, fitHalf, battery,
                        (x, y, half) -> hasGround(sums, x, y, half), taken);
                chosen.add(at);
                taken.add(at);
            }
            out.put(kind, chosen);
        }
        return out;
    }

    /** The reserved boxes as centre and half-extent, empty when the mission sited nothing or the map has no two ends. */
    private static List<int[]> boxes(GenContext ctx, PrecinctPlan plan) {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<FortKind, List<FortSiting.Point>> entry : sites(ctx, plan).entrySet()) {
            FortPlan fort = FortPlan.of(entry.getKey());
            int half = Math.max(fort.width(), fort.height()) / 2 + SLACK;
            for (FortSiting.Point t : entry.getValue()) out.add(new int[]{t.x(), t.y(), half});
        }
        return out;
    }

    /**
     * An estimate, before anything is grown, of the ground the plan's programmed places will claim: each is pooled from
     * its seed up to what its program needs plus a spread for the road that grows through it. The real claim is only
     * known after growth, and growth is what the reservation has to shape, so this stands in for it.
     */
    private static boolean[][] programmedGround(PrecinctPlan plan, int width, int height) {
        List<Precinct> precincts = plan.precincts();
        int[][] owner = new int[width][height];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        int[] budget = new int[precincts.size()];
        for (int i = 0; i < budget.length; i++) {
            Precinct p = precincts.get(i);
            budget[i] = p.isProgrammed() ? Math.round(p.program().envelopeArea() * PROGRAM_SPREAD) + PROGRAM_ROADS : 0;
        }
        int[][] claim = PrecinctClaim.byKind(precincts).assign(owner, width, height, budget);
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int who = claim[x][y];
                out[x][y] = who != GrownTrunkPlan.UNOWNED && precincts.get(who).isProgrammed();
            }
        }
        return out;
    }

    private static int[][] sums(boolean[][] grid) {
        int w = grid.length;
        int h = grid[0].length;
        int[][] s = new int[w + 1][h + 1];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) s[x + 1][y + 1] = s[x][y + 1] + s[x + 1][y] - s[x][y] + (grid[x][y] ? 1 : 0);
        }
        return s;
    }

    /** Whether the box about {@code (cx, cy)} holds any programmed ground, and so is not free to reserve. */
    private static boolean hasGround(int[][] s, int cx, int cy, int half) {
        int x0 = Math.max(0, cx - half);
        int y0 = Math.max(0, cy - half);
        int x1 = Math.min(s.length - 1, cx + half + 1);
        int y1 = Math.min(s[0].length - 1, cy + half + 1);
        return s[x1][y1] - s[x0][y1] - s[x1][y0] + s[x0][y0] > 0;
    }

    /** Ground no road arm may grow into, or {@code null} when the mission reserved none. */
    static boolean[][] keepClear(GenContext ctx, PrecinctPlan plan) {
        List<int[]> boxes = boxes(ctx, plan);
        if (boxes.isEmpty()) return null;
        boolean[][] mask = new boolean[ctx.width][ctx.height];
        for (int[] box : boxes) {
            for (int x = Math.max(0, box[0] - box[2]); x <= Math.min(ctx.width - 1, box[0] + box[2]); x++) {
                for (int y = Math.max(0, box[1] - box[2]); y <= Math.min(ctx.height - 1, box[1] + box[2]); y++) {
                    if (!nearASeed(plan, x, y)) mask[x][y] = true;
                }
            }
        }
        return mask;
    }

    /** Releases the town's claim on the reserved ground; changes nothing when the mission reserved none. */
    static void release(GenContext ctx, PrecinctPlan plan, int[][] claim) {
        for (int[] box : boxes(ctx, plan)) {
            for (int x = Math.max(0, box[0] - box[2]); x <= Math.min(claim.length - 1, box[0] + box[2]); x++) {
                for (int y = Math.max(0, box[1] - box[2]); y <= Math.min(claim[x].length - 1, box[1] + box[2]); y++) {
                    int owner = claim[x][y];
                    if (owner == GrownTrunkPlan.UNOWNED) continue;
                    if (plan.precincts().get(owner).isProgrammed()) continue;
                    claim[x][y] = GrownTrunkPlan.UNOWNED;
                }
            }
        }
    }

    private static boolean nearASeed(PrecinctPlan plan, int x, int y) {
        for (Precinct p : plan.precincts()) {
            if (Math.abs(p.seedX() - x) <= SEED_CLEARANCE && Math.abs(p.seedY() - y) <= SEED_CLEARANCE) return true;
        }
        return false;
    }
}
