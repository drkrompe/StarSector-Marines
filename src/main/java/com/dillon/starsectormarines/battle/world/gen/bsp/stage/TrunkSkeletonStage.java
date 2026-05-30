package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Step 1a — plan the trunk skeleton. Trunks (the city's arterials) get painted
 * into the shared road mask before BSP runs, plus the matching
 * {@link GroundKind} onto the topology so the renderer reads them as boulevards
 * rather than ordinary back-streets. Wide trunks
 * ({@link TrunkPlan.TrunkKind#sidewalkFlankWidth} &gt; 0) drop an explicit
 * SIDEWALK band on each side so the surface reads as "road in the middle, brick
 * sidewalk on the curbs" rather than relying on render-time wall adjacency that
 * can't see across the trunk's full width.
 *
 * <p>Binds the {@link TrunkPlan.Plan} under {@link BspKeys#TRUNK_PLAN} for the
 * partition / zoning / pedestrian / road-graph stages downstream.
 */
public final class TrunkSkeletonStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        CellTopology topology = ctx.topology;
        TrunkPlan.Plan plan = TrunkPlan.generate(ctx.width, ctx.height, ctx.rng);
        for (TrunkPlan.TrunkSegment trunk : plan.trunks) {
            paintTrunkGround(topology, trunk);
        }
        // Punch the road core through the intersection. Without this, the
        // PRIMARY trunk's outer SIDEWALK flank band would extend across the
        // SECONDARY trunk's vehicular path, breaking the road at the
        // crossing. Repainting the intersection rect as STREET keeps the
        // sidewalks butted up to the intersection edges and lets the road
        // continue uninterrupted across.
        for (int y = plan.intersection.y0; y <= plan.intersection.y1; y++) {
            for (int x = plan.intersection.x0; x <= plan.intersection.x1; x++) {
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        ctx.put(BspKeys.TRUNK_PLAN, plan);
    }

    /**
     * Paints one trunk's ground band onto the topology. If
     * {@link TrunkPlan.TrunkKind#sidewalkFlankWidth} is non-zero, the outer
     * {@code sidewalkFlankWidth} cells on each side of the band are tagged
     * {@link GroundKind#SIDEWALK} and the inner span is tagged
     * {@link TrunkPlan.TrunkKind#roadGround} — producing a "boulevard"
     * topology of road core + brick sidewalk curbs in one pass at gen time.
     *
     * <p>Bands too narrow to host the requested flanks (i.e.
     * {@code width <= 2*flank}) fall back to painting the entire band as
     * {@link GroundKind#SIDEWALK} so the configured kind still wins out
     * over the default {@link GroundKind#STREET} from step 0.
     */
    private static void paintTrunkGround(CellTopology topology, TrunkPlan.TrunkSegment trunk) {
        int flank = trunk.kind.sidewalkFlankWidth;
        int bandWidth = trunk.horizontal
                ? (trunk.bottom - trunk.top + 1)
                : (trunk.right - trunk.left + 1);
        boolean noRoadCore = bandWidth <= 2 * flank;
        if (trunk.horizontal) {
            for (int y = trunk.top; y <= trunk.bottom; y++) {
                int distFromEdge = Math.min(y - trunk.top, trunk.bottom - y);
                GroundKind kind = (flank > 0 && (noRoadCore || distFromEdge < flank))
                        ? GroundKind.SIDEWALK
                        : trunk.kind.roadGround;
                for (int x = trunk.left; x <= trunk.right; x++) {
                    topology.setGroundKind(x, y, kind);
                }
            }
        } else {
            for (int x = trunk.left; x <= trunk.right; x++) {
                int distFromEdge = Math.min(x - trunk.left, trunk.right - x);
                GroundKind kind = (flank > 0 && (noRoadCore || distFromEdge < flank))
                        ? GroundKind.SIDEWALK
                        : trunk.kind.roadGround;
                for (int y = trunk.top; y <= trunk.bottom; y++) {
                    topology.setGroundKind(x, y, kind);
                }
            }
        }
    }
}
