package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.InteriorAnchorFit;

/**
 * Closing stage — reconciles every point of interest's interior anchor against
 * the finished grid. See {@link InteriorAnchorFit} for the guarantee and why it
 * cannot be kept by the fillers that author the anchors.
 *
 * <p>Must be the <em>last</em> entry in every recipe, after the post-finalize
 * taxonomy consumers and spawn stages. Anything that stamps structure after
 * this stage can re-block an anchor it just repaired, which is exactly the
 * class of bug this stage exists to close.
 */
public final class InteriorAnchorFitStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        InteriorAnchorFit.apply(ctx.pois, ctx.grid);
    }
}
