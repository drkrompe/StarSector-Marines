package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderTopologyCacheTest {

    @Test
    void immutableTopologyIsReusedUntilNavigationRevisionChanges() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(grid.getWidth(), grid.getHeight()))) {
            CommanderService service = new CommanderService();
            CommandTopology first = service.freezeTopology(sim);
            assertSame(first, service.freezeTopology(sim));

            grid.setWalkable(4, 4, false);
            grid.setDoorway(3, 4, true);
            grid.setEdgePassable(3, 4, Direction.E, false);
            CommandTopology rebuilt = service.freezeTopology(sim);

            assertNotSame(first, rebuilt);
            assertTrue(first.isWalkable(4, 4));
            assertFalse(rebuilt.isWalkable(4, 4));
            assertTrue(rebuilt.isDoorwayCell(grid.index(3, 4)));
            NavigationGrid frozen = grid.copyNavigationTopology();
            assertFalse(frozen.isEdgePassable(3, 4, Direction.E));
            grid.setEdgePassable(3, 4, Direction.E, true);
            assertFalse(frozen.isEdgePassable(3, 4, Direction.E));
        }
    }
}
