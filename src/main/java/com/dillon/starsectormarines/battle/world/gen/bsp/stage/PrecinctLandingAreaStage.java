package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.ApproachRegion;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Terminal precinct-map pass that publishes the paired shuttle arrival areas an
 * attacking force lands on.
 *
 * <p>The same geometry {@link ConquestLandingAreaStage} authors on the stock
 * recipe's beach — two 5x5 berths a fixed offset either side of a lateral line,
 * stepped along a frontage — with the region replaced. A precinct map has no
 * biome bands and no traversal axis, so there is no shoreline to walk; what it
 * has instead is a statement about where the attack comes from and how far out
 * it lands. {@link ApproachRegion} turns both into the region the berths go in,
 * and carries the approach the stated band faced, so an area is a beachhead
 * rather than a clearing somewhere behind the line — and stays one once the
 * region has been slid inland, off every map edge.
 *
 * <p>{@link SpawnAnchorStage} resolves the same region through the same
 * function, because an arrival area in a region nobody spawns in is not an
 * arrival area.
 *
 * <p>Consumes no RNG: candidates are scanned in stable lateral/depth order after
 * every terrain, wall and emplacement mutation has run. Returns quietly when the
 * map was not built from a plan, and when the region seats nothing; how many it
 * seated is bound under {@link BspKeys#LANDING_AREAS_AUTHORED} either way,
 * because a beachhead that was never authored leaves nothing on the finished map
 * to notice.
 */
public final class PrecinctLandingAreaStage implements GenStage {

    /**
     * How far a berth stays from an emplacement's anchor.
     *
     * <p>A post's own ring cells are already non-walkable and rule themselves
     * out; this is about the ground beside them. Setting a squad down inside a
     * gun's own hardstand is a landing under the muzzle, and the open cells a
     * sparse footprint leaves are exactly where that would happen.
     */
    private static final int POST_CLEARANCE = 6;

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        if (plan == null) return;
        if (!ctx.landingAreas.isEmpty()) {
            throw new IllegalStateException("precinct landing areas already authored");
        }
        ApproachRegion resolved = ApproachRegion.resolve(
                plan, ctx.get(BspKeys.PRECINCT_CLAIM), ctx.width, ctx.height);
        int[] region = resolved.bounds();
        LandingPad.Approach approach = resolved.approach();
        boolean advanceAlongY = approach == LandingPad.Approach.SOUTH
                || approach == LandingPad.Approach.NORTH;

        int lateralLow = (advanceAlongY ? region[0] : region[1])
                + ConquestLandingAreaStage.LATERAL_MARGIN;
        int lateralHigh = (advanceAlongY ? region[2] : region[3])
                - ConquestLandingAreaStage.LATERAL_MARGIN;
        int authored = 0;
        for (int lateral = lateralLow; lateral <= lateralHigh;
             lateral += ConquestLandingAreaStage.LATERAL_STEP) {
            LandingArea area = firstClearArea(ctx, region, approach, advanceAlongY, lateral);
            if (area == null) continue;
            ctx.landingAreas.add(area);
            ctx.landingPads.addAll(area.berths());
            authored++;
        }
        ctx.put(BspKeys.LANDING_AREAS_AUTHORED, authored);
    }

    /**
     * The first legal pair on this lateral line, scanning inward from the side
     * of the region that faces the approach, so what comes back is a beachhead
     * rather than an arbitrary deep cell.
     */
    private static LandingArea firstClearArea(GenContext ctx, int[] region,
                                              LandingPad.Approach approach,
                                              boolean advanceAlongY, int lateral) {
        // A 5x5 berth needs two cells of inset on every side, and the whole area
        // has to sit inside the region the mission named.
        int forwardLow = (advanceAlongY ? region[1] : region[0]) + 2;
        int forwardHigh = (advanceAlongY ? region[3] : region[2]) - 2;
        boolean inward = approach == LandingPad.Approach.SOUTH
                || approach == LandingPad.Approach.WEST;
        int steps = forwardHigh - forwardLow;
        for (int i = 0; i <= steps; i++) {
            int forward = inward ? forwardLow + i : forwardHigh - i;
            int offset = ConquestLandingAreaStage.BERTH_OFFSET;
            int firstX = advanceAlongY ? lateral - offset : forward;
            int firstY = advanceAlongY ? forward : lateral - offset;
            int secondX = advanceAlongY ? lateral + offset : forward;
            int secondY = advanceAlongY ? forward : lateral + offset;
            LandingPad first = LandingPad.conquest(firstX, firstY, approach);
            LandingPad second = LandingPad.conquest(secondX, secondY, approach);
            if (!isLegalBerth(ctx, first) || !isLegalBerth(ctx, second)) continue;
            LandingArea area = new LandingArea(
                    "precinct-arrival-" + lateral + "-" + forward, first, second);
            if (isLegalArea(ctx, region, area)) return area;
        }
        return null;
    }

    private static boolean isLegalBerth(GenContext ctx, LandingPad berth) {
        for (int y = berth.bottom(); y <= berth.top(); y++) {
            for (int x = berth.left(); x <= berth.right(); x++) {
                if (!LandingGround.isOpen(ctx, x, y)) return false;
                if (ctx.topology.getGroundKind(x, y) == GroundKind.WATER) return false;
            }
        }
        return true;
    }

    private static boolean isLegalArea(GenContext ctx, int[] region, LandingArea area) {
        if (area.left < region[0] || area.right > region[2]
                || area.bottom < region[1] || area.top > region[3]) {
            return false;
        }
        if (!LandingGround.isOpen(ctx, area)) return false;
        for (int y = area.bottom; y <= area.top; y++) {
            for (int x = area.left; x <= area.right; x++) {
                if (ctx.topology.getGroundKind(x, y) == GroundKind.WATER) return false;
            }
        }
        for (DefensePost post : ctx.defensePosts) {
            if (post.anchorX >= area.left - POST_CLEARANCE
                    && post.anchorX <= area.right + POST_CLEARANCE
                    && post.anchorY >= area.bottom - POST_CLEARANCE
                    && post.anchorY <= area.top + POST_CLEARANCE) {
                return false;
            }
        }
        return true;
    }
}
