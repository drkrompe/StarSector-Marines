package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
            CommandTopology rebuilt = service.freezeTopology(sim);

            assertNotSame(first, rebuilt);
            assertFalse(rebuilt.isWalkable(4, 4));
        }
    }
}
