package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileCover;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleSetupNatureCoverTest {

    @Test
    void mapInstallPublishesNatureCoverIntoSimulation() {
        NavigationGrid grid = new NavigationGrid(7, 5);
        CellTopology topology = new CellTopology(7, 5);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        TileDef large = TileRegistry.installed().tile("nature.rock-large-1");
        topology.setNatureOverlayIndex(3, 2, large.index);
        MapResult map = new MapResult(grid, topology, 1, 2, 5, 2,
                Collections.emptyList(), Collections.emptyList());

        BattleSimulation sim = BattleSetup.buildMap(map, Collections.emptyList(),
                Collections.emptyList(), 17L).sim();

        assertTrue(sim.getDoodads().isEmpty(), "nature overlays must retain their own render path");
        assertEquals(TileCover.HEAVY.level(),
                sim.getGrid().getCoverAtFacing(2, 2, NavigationGrid.FACING_E));
        assertEquals(0, sim.getDoodadCoverAt(3, 2),
                "window-like large rock must not also publish crossed-prop cover");
    }
}
