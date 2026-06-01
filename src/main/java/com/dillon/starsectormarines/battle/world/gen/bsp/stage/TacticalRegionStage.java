package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;

/**
 * Segments the finished map's walkable space into the structural-taxonomy
 * {@link TacticalRegionMap} and binds it under {@link BspKeys#TACTICAL_REGIONS}.
 *
 * <p>Runs <b>after</b> {@link FinalizeStage} so it sees the final walkability +
 * ground kinds (every wall demolition, defense-post seal, and building flood is
 * already applied). Pure analysis — reads the grid/topology, draws no {@code rng},
 * mutates nothing — so adding it to a recipe leaves generated maps byte-identical.
 * Shared by both the conquest and legacy recipes; {@link BspKeys#AXIS} drives the
 * geometric assault-depth attribute (absent → depth comes back UNSET).
 */
public final class TacticalRegionStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, axis));
    }
}
