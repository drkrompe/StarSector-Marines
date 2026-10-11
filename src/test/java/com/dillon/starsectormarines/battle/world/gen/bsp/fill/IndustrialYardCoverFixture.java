package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import java.util.Random;

/** Production equipment placement without invoking the yard's terrain/layout recipe. */
public final class IndustrialYardCoverFixture {
    private IndustrialYardCoverFixture() {}
    public static Doodad placeCrates(NavigationGrid grid, CellTopology topology, int x, int y) {
        GenContext context = new GenContext(grid, topology, new Random(42),
                grid.getWidth(), grid.getHeight(), 42);
        return IndustrialYardFiller.placeProp(context, "doodad.industrial-crate-stack", x, y, true);
    }
}
