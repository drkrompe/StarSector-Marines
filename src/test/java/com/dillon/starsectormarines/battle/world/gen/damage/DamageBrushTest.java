package com.dillon.starsectormarines.battle.world.gen.damage;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.*;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DamageBrushTest {
    @Test void anAreaBendsAWholePropOnceAndLiveDamageSpendsItsRemainingHealth() {
        var grid = new NavigationGrid(8, 8); var topology = new CellTopology(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) { grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.COURTYARD); }
        try (var navigation = new NavigationService(grid, topology, false)) {
            var editor = new MapEditor(navigation); var props = new DoodadService(grid); editor.setDoodads(props);
            var bed = new Doodad(3, 3, TileRegistry.installed().doodad("doodad.residential-bed-h"));
            for (int y = 3; y < 3 + bed.footprintCellsY; y++) for (int x = 3; x < 3 + bed.footprintCellsX; x++) { grid.setWalkable(x, y, false); topology.setFixture(x, y, true); }
            props.addDoodad(bed);
            var layer = new Layer("damage", true, List.of(new Stroke(Mode.BATTERED, 3, List.of(new Point(3, 3), new Point(4, 3), new Point(3, 3)))));
            DamageBrush.apply(new DamagePlan(8, 8, 7, List.of(layer)), grid, topology, editor);
            assertEquals("doodad.residential-bed-h-damaged", props.getDoodads().get(0).definitionId());
            assertFalse(editor.damageWall(3, 3, 64)); assertTrue(editor.damageWall(3, 3, 1));
            assertEquals("doodad.residential-bed-h-destroyed", props.getDoodads().get(0).definitionId());
            assertTrue(grid.isWalkable(3, 3)); assertFalse(topology.isFixture(3, 3));
            assertEquals(0, props.getDoodadLevelOnCell(3, 3));
            assertEquals(CellTopology.GroundKind.COURTYARD, topology.getGroundKind(3, 3));
        }
    }
    @Test void ruinBreachesWallsAndRoofsWhileScarPreservesThem() {
        var grid = new NavigationGrid(8, 8); var topology = new CellTopology(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) { grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR); }
        grid.setWalkable(4, 4, false); grid.setWallHp(4, 4, 100); topology.setWall(4, 4, true); topology.setBuildingId(4, 4, 1); topology.setBuildingId(4, 5, 1);
        try (var navigation = new NavigationService(grid, topology, false)) {
            var editor = new MapEditor(navigation);
            var scar = new Layer("scar", true, List.of(new Stroke(Mode.SCAR, 2, List.of(new Point(4, 4)))));
            DamageBrush.apply(new DamagePlan(8, 8, 1, List.of(scar)), grid, topology, editor);
            assertFalse(grid.isWalkable(4, 4)); assertFalse(topology.isRoofDestroyed(4, 5));
            var ruin = new Layer("ruin", true, List.of(new Stroke(Mode.RUIN, 2, List.of(new Point(4, 4)))));
            DamageBrush.apply(new DamagePlan(8, 8, 1, List.of(ruin)), grid, topology, editor);
            assertTrue(grid.isWalkable(4, 4)); assertFalse(topology.isWall(4, 4)); assertTrue(topology.isRoofDestroyed(4, 5));
            assertNotNull(topology.getWallRemnant(4, 4));
        }
    }
}
