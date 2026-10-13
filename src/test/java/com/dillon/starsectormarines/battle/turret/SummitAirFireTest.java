package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SummitAirFireTest {
    @Test void summitMountClearsOnlyItsOwnHillAndDoesNotOpenGroundSight() {
        var plan = new TerrainPlan(40, 24, TerrainPlan.Finish.GRASS_TOP, 2, 0, 91,
                List.of(new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, 5, List.of(new TerrainPlan.Point(8, 12))),
                        new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, 4, List.of(new TerrainPlan.Point(28, 12)))));
        var field = TerrainField.build(plan);
        var topology = new CellTopology(40, 24);
        topology.setTerrainSurface(TerrainSurface.build(plan, field, topology));
        var grid = new NavigationGrid(40, 24);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 40; x++) {
            grid.setWalkableFloor(x, y);
            if (field.blocked(x, y)) {
                grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, false);
            }
        }
        assertFalse(TurretAim.airFireLineClear(grid, 8, 12, 20, 12, 0, 0));
        assertTrue(TurretAim.summitAirFireLineClear(grid, topology, 8, 12, 20, 12, 0));
        assertFalse(TurretAim.summitAirFireLineClear(grid, topology, 8, 12, 38, 12, 0), "a separate ridge still blocks");
        assertFalse(TurretAim.summitAirFireLineClear(grid, topology, 18, 12, 8, 12, 0), "ground origin gets no summit exemption");
        grid.setWalkable(17, 12, false); grid.setSeeThrough(17, 12, false);
        assertFalse(TurretAim.summitAirFireLineClear(grid, topology, 8, 12, 20, 12, 0), "intervening structures still block");
        new MapEditor(new NavigationService(grid, topology)).flipCellToRubble(8, 12);
        assertFalse(grid.isWalkable(8, 12), "demolishing the mount cannot open the summit");
        assertFalse(grid.isSeeThrough(8, 12));
        assertTrue(grid.hasTag(8, 12, NavigationGrid.CellTag.NATURAL_SOLID));
    }
}
