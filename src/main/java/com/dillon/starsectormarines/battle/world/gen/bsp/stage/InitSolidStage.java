package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Step 0 (station) — initialize every cell as solid, non-walkable hull. The
 * inverse of {@link InitFloorStage}: a station is enclosed-with-passages, so
 * the default is wall and rooms/corridors are the carved exception. The
 * un-carved remainder stays non-walkable and is tagged {@code WALL} (+ given
 * wall HP) by {@link FinalizeStage} at the end of the recipe.
 *
 * <p>A freshly allocated {@link com.dillon.starsectormarines.battle.nav.NavigationGrid}
 * is already all-zero (non-walkable, edges closed), so this is technically
 * redundant with the allocation default — but it makes the station recipe a
 * complete description of its own starting state rather than one coupled to how
 * the grid happens to be constructed.
 */
public final class InitSolidStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                ctx.grid.setWalkable(x, y, false);
                ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
            }
        }
    }
}
