package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 1b — run BSP independently inside each sub-rect between trunks/edges.
 * All sub-rects share one road mask so connectivity falls out by construction:
 * every BSP road frame butts against the trunk or perimeter cells already
 * marked road in the mask.
 *
 * <p>Reads {@link BspKeys#TRUNK_PLAN}; binds the resulting
 * {@link Bsp.Partition} under {@link BspKeys#PARTITION}.
 */
public final class BspPartitionStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        List<BlockLeaf> leaves = new ArrayList<>();
        for (TrunkPlan.SubRect r : plan.subRects) {
            Bsp.partitionRect(r.x0, r.y0, r.x1, r.y1, ctx.width, ctx.height,
                    Bsp.ROAD_WIDTH_MAX_WITH_TRUNKS, plan.roadCells, leaves, ctx.rng);
        }
        ctx.put(BspKeys.PARTITION,
                new Bsp.Partition(leaves, plan.roadCells, ctx.width, ctx.height));
    }
}
