package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A structure larger than one cell: its footprint geometry, the occupancy it
 * puts on the grid when it is spawned, its body radius, and the footprint it
 * gives back when it dies. One-cell structures are covered by the existing
 * turret suites and must be untouched by any of this.
 */
class MultiCellStructureTest {

    private static final String BATTERY = "structure.fortress-battery-placeholder";

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    void footprintIsCentredOnTheAnchorCell() {
        StructureDef battery = TurretCatalogRegistry.requireStructure(BATTERY);
        assertEquals(9, StructureFootprint.minX(battery, 10));
        assertEquals(4, StructureFootprint.minY(battery, 5));
        assertTrue(StructureFootprint.contains(battery, 10, 10, 9, 11));
        assertTrue(StructureFootprint.contains(battery, 10, 10, 11, 9));
        assertFalse(StructureFootprint.contains(battery, 10, 10, 12, 10));
        assertFalse(StructureFootprint.contains(battery, 10, 10, 10, 8));

        StructureDef vulcan = TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.VULCAN_STRUCTURE_ID);
        assertEquals(10, StructureFootprint.minX(vulcan, 10));
        assertTrue(StructureFootprint.contains(vulcan, 10, 10, 10, 10));
        assertFalse(StructureFootprint.contains(vulcan, 10, 10, 11, 10));
    }

    @Test
    void placementNeedsEveryFootprintCellOnTheMapAndOpen() {
        BattleSimulation sim = openArena(20, 20);
        StructureDef battery = TurretCatalogRegistry.requireStructure(BATTERY);
        NavigationGrid grid = sim.getGrid();
        assertTrue(StructureFootprint.canPlace(grid, battery, 10, 10));
        assertFalse(StructureFootprint.canPlace(grid, battery, 0, 10), "footprint would leave the map");
        assertFalse(StructureFootprint.canPlace(grid, battery, 10, 19), "footprint would leave the map");
        grid.setWalkable(11, 11, false);
        assertFalse(StructureFootprint.canPlace(grid, battery, 10, 10), "a corner cell is already taken");
    }

    @Test
    void spawningSealsTheWholeFootprintAndNothingAroundIt() {
        BattleSimulation sim = openArena(20, 20);
        sim.spawn(MapTurret.create("b0", Faction.DEFENDER, BATTERY, 10, 10));
        NavigationGrid grid = sim.getGrid();
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                boolean inside = x >= 9 && x <= 11 && y >= 9 && y <= 11;
                assertEquals(!inside, grid.isWalkable(x, y), "cell " + x + "," + y);
            }
        }
        assertTrue(grid.isSeeThrough(10, 10), "the body, not the cell, intercepts fire");
    }

    @Test
    void aRouteAcrossTheArenaGoesAroundTheFootprint() {
        BattleSimulation sim = openArena(20, 20);
        sim.spawn(MapTurret.create("b0", Faction.DEFENDER, BATTERY, 10, 10));
        StructureDef battery = TurretCatalogRegistry.requireStructure(BATTERY);
        int[] route = GridPathfinder.findPath(sim.getGrid(), 2, 10, 18, 10);
        assertTrue(route.length >= 2 && route.length % 2 == 0, "a route exists as x,y pairs");
        boolean wentAround = false;
        for (int i = 0; i < route.length; i += 2) {
            assertFalse(StructureFootprint.contains(battery, 10, 10, route[i], route[i + 1]),
                    "route entered the footprint at " + route[i] + "," + route[i + 1]);
            if (Math.abs(route[i + 1] - 10) >= 2) wentAround = true;
        }
        assertTrue(wentAround, "the straight line is blocked, so the route bends round it");
    }

    @Test
    void bodyRadiusIsTheAuthoredCircleOverTheFootprint() {
        BattleSimulation sim = openArena(20, 20);
        long battery = sim.spawn(MapTurret.create("b0", Faction.DEFENDER, BATTERY, 10, 10));
        StructureDef def = TurretCatalogRegistry.requireStructure(BATTERY);
        assertEquals(def.radius, sim.getRoster().radius(battery), 1e-6f);
        assertTrue(def.radius > 1f && def.radius <= 1.5f * (float) Math.sqrt(2.0),
                "a circle that covers most of a 3x3 and not the cells beyond it");
        assertEquals(def.hitHalfHeight, sim.getRoster().hitHalfHeight(battery), 1e-6f);
    }

    @Test
    void destroyingItOpensTheWholeFootprintAsRubble() {
        BattleSimulation sim = openArena(20, 20);
        long battery = sim.spawn(MapTurret.create("b0", Faction.DEFENDER, BATTERY, 10, 10));
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        sim.applyDamage(battery, 1_000_000f, 3.5f, 0f);
        assertFalse(sim.world().isAlive(battery));
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.getTurretDemolitionSystem().isDemolished(battery));
        for (int y = 9; y <= 11; y++) {
            for (int x = 9; x <= 11; x++) {
                assertTrue(sim.getGrid().isWalkable(x, y), "cell " + x + "," + y);
                assertEquals(CellTopology.GroundKind.RUBBLE, sim.getTopology().getGroundKind(x, y));
            }
        }
    }

    @Test
    void aOneCellStructureSpawnedOnOpenFloorStillLeavesItAlone() {
        BattleSimulation sim = openArena(20, 20);
        sim.spawn(MapTurret.create("t0", Faction.DEFENDER, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 10, 10));
        assertTrue(sim.getGrid().isWalkable(10, 10), "carrier-placed emplacements do not seal");
    }
}
