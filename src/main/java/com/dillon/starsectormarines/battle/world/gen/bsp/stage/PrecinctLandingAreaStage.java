package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
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
 * attacking force lands on, and the beachhead compound they make up.
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
 * <p><b>The berths are confined to the landing place when the plan has one.</b>
 * A region is a third of the map and open ground inside it is anything walkable
 * outside a building, which includes the streets of a settlement; a plan that
 * seeded a landing precinct claimed its apron before the town flooded, and that
 * claim is the ground the force is entitled to. Where the plan states none —
 * every mission but Conquest — the region alone is the answer, exactly as
 * before.
 *
 * <p>{@link SpawnAnchorStage} resolves the same region and the same claim,
 * because an arrival area in a region nobody spawns in is not an arrival area.
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

    /**
     * How much holding the beachhead is worth to a reader of the command
     * picture. Below a keep and above a guard post: it is the force's own
     * ground rather than a prize on the way to the objective.
     */
    private static final int BEACHHEAD_WEIGHT = 75;

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        if (plan == null) return;
        if (!ctx.landingAreas.isEmpty()) {
            throw new IllegalStateException("precinct landing areas already authored");
        }
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        ApproachRegion resolved = ApproachRegion.resolve(plan, claim, ctx.width, ctx.height);
        int[] region = resolved.bounds();
        LandingPad.Approach approach = resolved.approach();
        boolean advanceAlongY = approach == LandingPad.Approach.SOUTH
                || approach == LandingPad.Approach.NORTH;

        int landingIndex = claim == null ? -1 : plan.landingIndex();
        int[] place = landingIndex < 0 ? null
                : ApproachRegion.claimBounds(claim, landingIndex, ctx.width, ctx.height);
        int[] scan = place == null ? null : overlap(region, place);
        // A landing place whose claim never met the region is the estimate the
        // plan seeded it on having missed. Recorded rather than thrown: a
        // beachhead in the right region is a worse map, a Conquest with no
        // beachhead at all is no map.
        boolean onItsPlace = scan != null;
        if (!onItsPlace) scan = region;
        if (landingIndex >= 0) ctx.put(BspKeys.LANDING_ON_ITS_PLACE, onItsPlace);

        int lateralLow = advanceAlongY ? scan[0] : scan[1];
        int lateralHigh = advanceAlongY ? scan[2] : scan[3];
        if (!onItsPlace) {
            // The margin keeps a map-edge band's areas out of the corners. A
            // claim has already stopped well short of them.
            lateralLow += ConquestLandingAreaStage.LATERAL_MARGIN;
            lateralHigh -= ConquestLandingAreaStage.LATERAL_MARGIN;
        }
        int authored = 0;
        LandingArea first = null;
        int[] hull = null;
        for (int lateral = lateralLow; lateral <= lateralHigh;
             lateral += ConquestLandingAreaStage.LATERAL_STEP) {
            LandingArea area = firstClearArea(ctx, scan, approach, advanceAlongY, lateral,
                    onItsPlace ? claim : null, landingIndex);
            if (area == null) continue;
            ctx.landingAreas.add(area);
            ctx.landingPads.addAll(area.berths());
            if (first == null) first = area;
            hull = union(hull, area);
            authored++;
        }
        ctx.put(BspKeys.LANDING_AREAS_AUTHORED, authored);
        if (landingIndex >= 0 && first != null) {
            emitBeachhead(ctx, first, place != null ? place : hull);
        }
    }

    /**
     * Publishes the ground the marines came ashore on as a compound they
     * already hold.
     *
     * <p>The footprint is <b>the landing place's own claim</b>, not merely the
     * berths on it: a beachhead is lost when somebody is standing on the ground
     * the shuttles are using, and the apron between two berths is as much that
     * ground as the berths are. Where the plan states no landing place — the
     * fallback, and every mission but Conquest — the areas' own hull stands in.
     *
     * <p>Its default guard is the marines, which is what
     * {@code CompoundService} reads to start it at {@code MARINE_HELD}; the
     * anchor is a berth centre, which is open ground by construction, so the
     * capture room resolves on the first tick.
     *
     * <p>No garrison is requested. Holding the beachhead against a
     * counterattack is a decision for the marine commander with the squads it
     * has, not a slice of the assault reserved by the map.
     *
     * <p>Added to the finished {@link com.dillon.starsectormarines.battle.decision.TacticalMap}
     * rather than to {@code ctx.tactical}: this stage runs after the link pass
     * that built it, because a berth has to be clear of every wall and gun the
     * earlier stages stamped.
     */
    private static void emitBeachhead(GenContext ctx, LandingArea first, int[] footprint) {
        TacticalMap map = ctx.get(BspKeys.TACTICAL_MAP);
        if (map == null) return;
        LandingPad anchor = first.berths().get(0);
        map.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD,
                anchor.centerX, anchor.centerY,
                footprint[0], footprint[1], footprint[2], footprint[3],
                Faction.MARINE, BEACHHEAD_WEIGHT, 0, false));
    }

    /** The two rects' shared ground, or {@code null} when they do not meet. */
    private static int[] overlap(int[] a, int[] b) {
        int x0 = Math.max(a[0], b[0]);
        int y0 = Math.max(a[1], b[1]);
        int x1 = Math.min(a[2], b[2]);
        int y1 = Math.min(a[3], b[3]);
        return x0 > x1 || y0 > y1 ? null : new int[]{x0, y0, x1, y1};
    }

    /** The rect grown to cover one more area. */
    private static int[] union(int[] hull, LandingArea area) {
        if (hull == null) {
            return new int[]{area.left, area.bottom, area.right, area.top};
        }
        return new int[]{
                Math.min(hull[0], area.left), Math.min(hull[1], area.bottom),
                Math.max(hull[2], area.right), Math.max(hull[3], area.top)};
    }

    /**
     * The first legal pair on this lateral line, scanning inward from the side
     * of the region that faces the approach, so what comes back is a beachhead
     * rather than an arbitrary deep cell.
     */
    private static LandingArea firstClearArea(GenContext ctx, int[] region,
                                              LandingPad.Approach approach,
                                              boolean advanceAlongY, int lateral,
                                              int[][] claim, int landingIndex) {
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
            if (isLegalArea(ctx, region, area, claim, landingIndex)) return area;
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

    private static boolean isLegalArea(GenContext ctx, int[] region, LandingArea area,
                                       int[][] claim, int landingIndex) {
        if (area.left < region[0] || area.right > region[2]
                || area.bottom < region[1] || area.top > region[3]) {
            return false;
        }
        if (!LandingGround.isOpen(ctx, area)) return false;
        for (int y = area.bottom; y <= area.top; y++) {
            for (int x = area.left; x <= area.right; x++) {
                if (ctx.topology.getGroundKind(x, y) == GroundKind.WATER) return false;
                // Every cell of the area, berths and the gap between them, is
                // the landing place's own ground. A berth half in a neighbour's
                // claim is a landing in somebody else's district.
                if (claim != null && claim[x][y] != landingIndex) return false;
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
