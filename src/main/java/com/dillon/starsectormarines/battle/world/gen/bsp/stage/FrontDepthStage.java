package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;

/**
 * Says where the front is, once the map is finished.
 *
 * <p>The closing statement of the one fact the defender reinforcement layer
 * needs and neither recipe used to publish: how far each cell is from the thing
 * the battle is about. It runs on both recipes that can have an objective so
 * they cannot disagree about what a front is — from the biome strips where a
 * band layout was painted, and from the objective precinct's own claim where a
 * map was grown out of places.
 *
 * <p>It reads no grid and mutates nothing, so its position among the closing
 * stages is free. It is placed immediately before {@code InteriorAnchorFitStage}
 * for the reason that stage's own comment gives: fitting the POI anchors
 * against the finished grid must stay genuinely last in every recipe.
 *
 * <p>Binds nothing at all when there is no front to state — no biome layer, and
 * either no plan, no claim, or no programmed place on it. A map with nothing to
 * hold is not a map with a front of depth zero everywhere; it is a map the
 * front-line layer should decline to install on.
 */
public final class FrontDepthStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        BiomeMap biomes = ctx.get(BspKeys.BIOME_MAP);
        if (biomes != null) {
            ctx.put(BspKeys.FRONT_DEPTH, FrontDepth.fromBiomes(biomes));
            return;
        }
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        if (plan == null || claim == null) return;
        Precinct objective = plan.objective();
        if (objective == null) return;
        FrontDepth depth = FrontDepth.fromObjective(
                claim, plan.precincts().indexOf(objective), ctx.width, ctx.height);
        if (depth != null) ctx.put(BspKeys.FRONT_DEPTH, depth);
    }
}
