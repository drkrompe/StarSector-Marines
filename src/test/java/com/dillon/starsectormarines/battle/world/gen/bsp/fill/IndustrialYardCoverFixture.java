package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import java.util.Random;

/** A one-slot freight yard exercises production placement without unrelated scenery. */
public final class IndustrialYardCoverFixture {
    private IndustrialYardCoverFixture() {}
    public static Doodad placeCrates(NavigationGrid grid, CellTopology topology, int x, int y) {
        GenContext context = new GenContext(grid, topology, new Random(42),
                grid.getWidth(), grid.getHeight(), 42);
        IndustrialYardFiller.furnishArea(new BlockLeaf(x, y, x, y, false), context, true, 0);
        if (context.doodads.size() != 1
                || !"doodad.industrial-crate-stack".equals(context.doodads.get(0).definitionId()))
            throw new IllegalStateException("Freight yard no longer begins with a crate stack");
        return context.doodads.get(0);
    }
}
