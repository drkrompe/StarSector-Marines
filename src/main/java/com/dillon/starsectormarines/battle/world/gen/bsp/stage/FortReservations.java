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
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps open ground for the forts a mission sited as barriers. A fort is stamped after the town, and by then the middle
 * of a Conquest map is a grid of roads and claimed places with no pocket big enough for a fort: ground on the approach
 * is exactly what leftover placement never finds. So, before the road network has grown and the town has claimed, the
 * ground round each fort's target is reserved: no road arm grows into it and the town's zoned places give up their claim
 * to it. {@code FortStage} later stands the fort on it.
 *
 * <p>Only a zoned (town) place gives ground up. A programmed place (the keep's installation, the landing, a lane's
 * compound) is prior and keeps every cell it claimed, and a reservation never lands on one's seed. Inert unless the
 * mission declared forts and chose {@link FortSitingPolicy#COVER_THE_APPROACH}, so every other map is byte-identical to
 * one generated without it.
 */
final class FortReservations {

    /** Clear ground kept beyond a fort's footprint on every side, in cells (a little more than the placement margin). */
    private static final int SLACK = 5;
    /** A reservation leaves the ground within this many cells of any place's seed alone: that place has to grow. */
    private static final int SEED_CLEARANCE = 14;

    private FortReservations() {}

    /** The reserved boxes as centre and half-extent, empty when the mission sited nothing or the map has no two ends. */
    private static List<int[]> boxes(GenContext ctx, PrecinctPlan plan) {
        List<int[]> out = new ArrayList<>();
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (profile == null) return out;
        GroundDefences declared = profile.declaredDefences();
        if (declared.siting() != FortSitingPolicy.COVER_THE_APPROACH) return out;
        Precinct landing = plan.landingPlace();
        Precinct objective = plan.objective();
        if (landing == null || objective == null) return out;
        FortSiting.Anchors anchors = new FortSiting.Anchors(
                new FortSiting.Point(objective.seedX(), objective.seedY()),
                new FortSiting.Point(landing.seedX(), landing.seedY()), ctx.width, ctx.height);
        for (FortKind kind : FortKind.values()) {
            int count = declared.fortCount(kind);
            if (count <= 0) continue;
            FortPlan fort = FortPlan.of(kind);
            int half = Math.max(fort.width(), fort.height()) / 2 + SLACK;
            List<FortSiting.Point> targets = new ArrayList<>();
            if (kind == FortKind.BATTERY_FORT) {
                targets.addAll(FortSiting.batteryTargets(anchors, count));
            } else {
                for (int i = 0; i < count; i++) targets.add(FortSiting.shieldTarget(anchors));
            }
            for (FortSiting.Point t : targets) out.add(new int[]{t.x(), t.y(), half});
        }
        return out;
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
