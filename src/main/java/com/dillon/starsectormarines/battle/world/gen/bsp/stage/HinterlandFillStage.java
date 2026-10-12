package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.SubRect;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;

import java.util.List;

/**
 * Gives the ground the road growth never reached something to be. Reads
 * {@link BspKeys#HINTERLAND} and dresses each region as grassland — weighted
 * grass, dirt and sand with plant and rock scatter — instead of leaving it the
 * flat {@code GRASS} that {@code GrownTrunkSkeletonStage} paints to mark it.
 *
 * <p><b>A hinterland region is not a block, and is deliberately not made one.</b>
 * It would have been less code to emit these rects as {@link BlockLeaf}s into
 * the partition and let the ordinary fill dispatch cover them, but a leaf is a
 * city parcel: labelling would zone it, the size constraints would demote it,
 * and a compound claim could try to build on it. Open country is the absence of
 * a parcel, so it is filled by its own pass and never enters the partition.
 *
 * <p>The scatter is reused rather than reinvented — {@link NatureZoneFiller}
 * already dresses a rect, and {@link BlockLeaf} is only a rect. Its grassland
 * rock pool is small and medium rocks, all passable, so nothing it places can
 * strand anybody; the no-islands law is satisfied by the pool's composition
 * rather than by a check here.
 */
public final class HinterlandFillStage implements GenStage {

    private final NatureZoneFiller grassland = new NatureZoneFiller(BlockKind.NATURE_GRASSLAND);

    @Override
    public void run(GenContext ctx) {
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        int[][] road = ctx.get(BspKeys.PRECINCT_ROAD);
        if (claim != null && road != null) paintUnclaimedGround(ctx, claim, road);
        List<SubRect> hinterland = ctx.get(BspKeys.HINTERLAND);
        if (hinterland == null || hinterland.isEmpty()) return;
        for (int i = 0; i < hinterland.size(); i++) {
            SubRect r = hinterland.get(i);
            boolean touchesEdge = r.x0 <= 1 || r.y0 <= 1
                    || r.x1 >= ctx.width - 2 || r.y1 >= ctx.height - 2;
            grassland.fill(new BlockLeaf(r.x0, r.y0, r.x1, r.y1, touchesEdge), ctx);
        }
    }

    /** Seed thin scraps too; rectangular nature fills cannot cover every claim-edge cell. */
    static void paintUnclaimedGround(GenContext ctx, int[][] claim, int[][] road) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        SurfacePalette surface = profile == null ? SurfacePalette.ROCK : profile.surface();
        int[] asphalt = new int[3];
        for (int age = 0; age < 3; age++) asphalt[age] = ctx.topology.surfaceIndex(Material.ASPHALT.block(age));
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            if (claim[x][y] != GrownTrunkPlan.UNOWNED
                    || ctx.isMadeGround(x, y) || ctx.topology.isWall(x, y)
                    || ctx.topology.hasBuildingKindHint(x, y) || ctx.topology.isFixture(x, y)) continue;
            if (road[x][y] == GrownTrunkPlan.UNOWNED) {
                ctx.topology.setGroundKind(x, y, surface.inlandGround());
            } else {
                // Welded/gate approach cells can fall beyond the original trunk's
                // paint. The surviving road mask owns their surface too.
                ctx.topology.setGroundKind(x, y, GroundKind.STREET);
                ctx.topology.setSurface(x, y, asphalt[RoadRasterizer.age(.4, ctx.seed, x, y)]);
            }
        }
    }
}
