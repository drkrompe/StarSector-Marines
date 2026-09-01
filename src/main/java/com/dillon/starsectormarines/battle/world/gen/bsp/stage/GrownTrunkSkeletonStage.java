package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.SubRect;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.List;

/**
 * Step 1a alternative — plans the trunk skeleton via {@link GrownTrunkPlan}'s
 * recursive junction growth instead of {@link TrunkPlan}'s single fixed
 * crossroad. Selected in place of {@link TrunkSkeletonStage} by
 * {@code BspCityGenerator.useGrownRoads}; every downstream stage still reads
 * {@link BspKeys#TRUNK_PLAN} and sees an ordinary {@link TrunkPlan.Plan}.
 *
 * <p>Ground painting mirrors {@link TrunkSkeletonStage#paintTrunkGround}
 * exactly: each {@link TrunkPlan.TrunkSegment}'s flanks get
 * {@link GroundKind#SIDEWALK} and its inner span gets
 * {@link TrunkPlan.TrunkKind#roadGround}, falling back to all-SIDEWALK when
 * the band is too narrow to host both flanks. A grown skeleton can cross
 * itself anywhere — not just at one authored intersection — so every
 * rectangle where a horizontal segment's band overlaps a vertical segment's
 * band is repainted {@link GroundKind#STREET} afterward, generalizing
 * {@link TrunkSkeletonStage}'s single-intersection punch-through to however
 * many crossings growth produced.
 *
 * <p>Also binds {@link BspKeys#HINTERLAND} with the open ground growth never
 * reached, which {@link TrunkSkeletonStage}'s fixed layout never produces,
 * and paints it {@link GroundKind#GRASS}: unreached ground is authored open
 * country, and leaving it at the pipeline's default street surface would
 * misreport it as pavement.
 */
public final class GrownTrunkSkeletonStage implements GenStage {

    private final GrownTrunkPlan.Profile profile;

    public GrownTrunkSkeletonStage(GrownTrunkPlan.Profile profile) {
        this.profile = profile;
    }

    @Override
    public void run(GenContext ctx) {
        CellTopology topology = ctx.topology;
        GrownTrunkPlan.Result result = GrownTrunkPlan.generate(ctx.width, ctx.height, ctx.rng, profile);
        List<TrunkPlan.TrunkSegment> trunks = result.plan.trunks;
        boolean[][] trunkPainted = new boolean[ctx.width][ctx.height];
        for (int i = 0; i < trunks.size(); i++) {
            paintTrunkGround(topology, trunkPainted, trunks.get(i));
        }
        paintCrossings(topology, trunks);
        paintHinterland(topology, trunkPainted, result.hinterland);
        ctx.put(BspKeys.TRUNK_PLAN, result.plan);
        ctx.put(BspKeys.HINTERLAND, result.hinterland);
    }

    /**
     * Repaints every rectangle where a horizontal segment's band overlaps a
     * vertical segment's band as {@link GroundKind#STREET}. Without this the
     * flank/sidewalk band painted by {@link #paintTrunkGround} would sever
     * whichever road crosses it, the same problem
     * {@link TrunkSkeletonStage#run} solves for its one fixed
     * {@code intersection} rect.
     */
    static void paintCrossings(CellTopology topology, List<TrunkPlan.TrunkSegment> trunks) {
        for (int i = 0; i < trunks.size(); i++) {
            TrunkPlan.TrunkSegment a = trunks.get(i);
            if (!a.horizontal) continue;
            for (int j = 0; j < trunks.size(); j++) {
                TrunkPlan.TrunkSegment b = trunks.get(j);
                if (b.horizontal) continue;
                int x0 = Math.max(a.left, b.left);
                int x1 = Math.min(a.right, b.right);
                int y0 = Math.max(a.top, b.top);
                int y1 = Math.min(a.bottom, b.bottom);
                if (x0 > x1 || y0 > y1) continue;
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        topology.setGroundKind(x, y, GroundKind.STREET);
                    }
                }
            }
        }
    }

    /**
     * Paints one trunk's ground band onto the topology and marks every cell
     * it touches in {@code trunkPainted}, so {@link #paintHinterland} never
     * overwrites a road cell even if a hinterland rect were to clip one.
     * Ground kind selection is identical to
     * {@link TrunkSkeletonStage#paintTrunkGround}: if
     * {@link TrunkPlan.TrunkKind#sidewalkFlankWidth} is non-zero, the outer
     * {@code sidewalkFlankWidth} cells on each side of the band are tagged
     * {@link GroundKind#SIDEWALK} and the inner span is tagged
     * {@link TrunkPlan.TrunkKind#roadGround}. Bands too narrow to host the
     * requested flanks fall back to painting the entire band as
     * {@link GroundKind#SIDEWALK}.
     */
    static void paintTrunkGround(CellTopology topology, boolean[][] trunkPainted, TrunkPlan.TrunkSegment trunk) {
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
                    trunkPainted[x][y] = true;
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
                    trunkPainted[x][y] = true;
                }
            }
        }
    }

    /**
     * Paints every hinterland rect {@link GroundKind#GRASS} — unreached
     * ground is authored open country, and leaving it at the pipeline's
     * default street surface would misreport it as pavement. Skips any cell
     * already marked in {@code trunkPainted} so a road that clips a
     * hinterland rect still wins.
     */
    static void paintHinterland(CellTopology topology, boolean[][] trunkPainted, List<SubRect> hinterland) {
        for (int i = 0; i < hinterland.size(); i++) {
            SubRect rect = hinterland.get(i);
            for (int y = rect.y0; y <= rect.y1; y++) {
                for (int x = rect.x0; x <= rect.x1; x++) {
                    if (trunkPainted[x][y]) continue;
                    topology.setGroundKind(x, y, GroundKind.GRASS);
                }
            }
        }
    }
}
