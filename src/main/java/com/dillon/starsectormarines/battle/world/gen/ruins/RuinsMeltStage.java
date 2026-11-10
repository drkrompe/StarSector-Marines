package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Cuts a foundry's melt ({@code ruins-expedition.md}, A forge works has a foundry): the pour channels and casting beds
 * {@link RuinsDelveStage} planned ({@link RuinsKeys#MELT}) become a {@link NaturalVoidZoning#FOUNDRY} drop. A drop
 * is unwalkable, see-through, gives no edge cover and is never a wall, so it is cut after {@code FinalizeStage},
 * which takes every unwalkable cell left standing for a wall, as the outdoor landscape cuts its drops after it.
 * A map that already draws a drop or a backdrop keeps its own and no melt. The landscape after this stage then lays
 * no drop of its own, a map carrying one look.
 */
public final class RuinsMeltStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        boolean[] melt = ctx.get(RuinsKeys.MELT);
        CellTopology topology = ctx.topology;
        if (melt == null || topology.naturalVoid() != null || topology.voidBackdrop() != null) return;
        NavigationGrid grid = ctx.grid;
        int w = ctx.width;
        for (int i = 0; i < melt.length; i++) {
            if (!melt[i]) continue;
            int x = i % w, y = i / w;
            grid.setWalkable(x, y, false);
            grid.setTag(x, y, NavigationGrid.CellTag.SEE_THROUGH, true);
            grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
            grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
            topology.setGroundKind(x, y, GroundKind.VOID);
            topology.setSurface(x, y, 0);
        }
        topology.setNaturalVoid(NaturalVoidZoning.FOUNDRY.look((int) ctx.seed));
    }
}
