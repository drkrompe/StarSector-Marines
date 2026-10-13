package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalvagePlacementTest {
    @Test void excludesLandingObjectivesAndDisconnectedSitesWithoutChangingNavigation() {
        var grid = new NavigationGrid(120, 80);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 100; x++) grid.setWalkableFloor(x,y);
        grid.setWalkableFloor(110,40);
        var pads = List.of(new LandingPad(5,40,2,2, LandingPad.Approach.WEST));
        var candidates = List.of(new SalvagePlacement.Cell(10,40), new SalvagePlacement.Cell(60,40),
                new SalvagePlacement.Cell(110,40), new SalvagePlacement.Cell(40,10),
                new SalvagePlacement.Cell(75,10), new SalvagePlacement.Cell(75,70));
        var targets = List.of(new SalvagePlacement.Cell(60,40));
        var first = SalvagePlacement.place(grid,pads,candidates,targets,42);
        var second = SalvagePlacement.place(grid,pads,candidates,targets,42);
        assertEquals(3, first.size());
        assertEquals(first.stream().map(c -> c.cellX + "," + c.cellY).toList(),
                second.stream().map(c -> c.cellX + "," + c.cellY).toList());
        for (var c : first) {
            assertTrue(grid.arePathConnected(5,40,c.cellX,c.cellY,false));
            assertNotEquals(40, c.cellY);
        }
        assertFalse(grid.isWalkable(100,40));
        assertTrue(SalvagePlacement.place(grid,List.of(),candidates,targets,42).isEmpty());
    }
}
