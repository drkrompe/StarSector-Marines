package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingApronTest {
    @Test
    void everyWindowFacingKeepsItsApproachPaved() {
        for (Direction facing : Direction.CARDINALS) {
            NavigationGrid grid = new NavigationGrid(7, 7);
            CellTopology topology = new CellTopology(7, 7);
            for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
            topology.setGroundKind(3, 3, CellTopology.GroundKind.INDOOR);
            topology.setBuildingKindHint(3, 3, BuildingKind.RESIDENTIAL);
            grid.placeEdgeBarrier(3, 3, facing, SharedEdgeBarrier.Kind.WINDOW);
            assertTrue(GroundTileSelector.isSidewalkCell(grid, topology, 3 + facing.dx, 3 + facing.dy));
            assertTrue(GroundTileSelector.isSidewalkCell(grid, topology, 2, 2), "apron wraps corners");
            assertFalse(GroundTileSelector.isSidewalkCell(grid, topology, 3, 3), "window recess stays indoors");
            assertFalse(GroundTileSelector.isSidewalkCell(grid, topology, 1, 1), "only one cell of apron");
            grid.damageEdgeBarrier(3, 3, facing, 1000);
            assertTrue(GroundTileSelector.isSidewalkCell(grid, topology, 3 + facing.dx, 3 + facing.dy),
                    "broken glazing does not demolish the sidewalk");
        }
    }
}
