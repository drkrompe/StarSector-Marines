package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A parked road vehicle is a prop, not a vehicle. It has no body, no tick and no
 * entity — it is scenery that happens to be truck-shaped — so it is an ordinary
 * registry doodad placed from a pool, and everything that used to be a parallel
 * mechanism for it (its own list on the simulation, its own render system, its
 * own footprint stamp, its own cover path) is gone.
 */
class ParkedVehicleDoodadTest {

    private static final int W = 60;
    private static final int H = 40;
    /** Centre of a 3x3 blockhouse in the corner furthest from anything parked. */
    private static final int BLOCKHOUSE = 2;

    private static MapResult openStreetMap() {
        return openStreetMap(false);
    }

    /**
     * @param orphan when true the blockhouse in the far corner has its middle
     *               cell left walkable, so the map carries one walkable cell
     *               nothing can reach. The walls are stamped either way, so the
     *               two maps differ in that cell alone.
     */
    private static MapResult openStreetMap(boolean orphan) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        for (int y = BLOCKHOUSE - 1; y <= BLOCKHOUSE + 1; y++) {
            for (int x = BLOCKHOUSE - 1; x <= BLOCKHOUSE + 1; x++) {
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
            }
        }
        if (orphan) {
            grid.setWalkableFloor(BLOCKHOUSE, BLOCKHOUSE);
            topology.setWall(BLOCKHOUSE, BLOCKHOUSE, false);
        }
        return new MapResult(grid, topology, 10, 12, 30, 12,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, Collections.emptyList());
    }

    @Test
    void everyParkedVehicleIsADoodadFromTheAuthoredPool() {
        MapResult map = openStreetMap();

        List<Doodad> parked = BattleSetup.stampVehicles(map, new Random(11L));

        assertFalse(parked.isEmpty(), "an open street map should park some vehicles");
        List<DoodadDef> pool = GenMappingRegistry.installed().doodadPool("PARKED_VEHICLES");
        assertEquals(6, pool.size(), "the authored pool is the six truck props");
        for (Doodad vehicle : parked) {
            assertEquals(3, vehicle.footprintCellsX);
            assertEquals(2, vehicle.footprintCellsY);
            assertEquals(Doodad.COVER_HEAVY, vehicle.cover,
                    "a truck is the best cover on an open street");
            assertTrue(pool.stream().anyMatch(def ->
                            def.sheetPath.equals(vehicle.sheetPath)
                                    && def.col == vehicle.tile.col
                                    && def.row == vehicle.tile.row),
                    "every placement resolves to a pool member's authored source cell");
        }
    }

    @Test
    void aTruckStopsMovementButNotSightOrFire() {
        MapResult map = openStreetMap();
        NavigationGrid grid = map.grid;

        List<Doodad> parked = BattleSetup.stampVehicles(map, new Random(11L));
        Doodad truck = parked.get(0);

        for (int dy = 0; dy < truck.footprintCellsY; dy++) {
            for (int dx = 0; dx < truck.footprintCellsX; dx++) {
                int x = truck.cellX + dx;
                int y = truck.cellY + dy;
                assertFalse(grid.isWalkable(x, y), "nobody walks through a truck");
                assertTrue(grid.isSeeThrough(x, y), "but a marine shoots over the hood");
                assertTrue(grid.isEdgeCoverSuppressed(x, y),
                        "the prop's authored cover is the only cover it publishes");
            }
        }

        // A sight line straight along the truck's own row survives it.
        int row = truck.cellY;
        assertTrue(grid.hasLineOfSight(truck.cellX - 3, row,
                        truck.cellX + truck.footprintCellsX + 2, row),
                "a parked truck is not a wall across the street");
    }

    /**
     * Ground stranded somewhere else does not empty the street.
     *
     * <p>The connectivity guard has to ask whether <em>this truck</em> strands
     * ground, not whether any ground anywhere is stranded. Those are the same
     * question only on a map that is whole to begin with, and no generated one
     * is: an interior not yet given its doorway, a cell a fill sealed. Under the
     * map-wide form every candidate anchor on the map is refused, so the pass
     * parks nothing at all and raises neither an exception nor a log line —
     * which looks exactly like a stamper that was never called.
     *
     * <p>The blockhouse stands in both maps; the only difference is the cell at
     * its centre, walled in the control and walkable-but-unreachable here.
     */
    @Test
    void anOrphanPocketElsewhereDoesNotEmptyTheStreet() {
        int whole = BattleSetup.stampVehicles(openStreetMap(false), new Random(11L)).size();
        int orphaned = BattleSetup.stampVehicles(openStreetMap(true), new Random(11L)).size();

        assertTrue(whole > 0, "the control parked nothing, so this measures nothing");
        assertEquals(whole, orphaned, "the street took " + whole + " vehicles on an unbroken map "
                + "and " + orphaned + " on the same map with one cell sealed off in a far corner, "
                + "so the guard is answering a question about the map rather than about the stamp");
    }

    @Test
    void aTruckPublishesItsAuthoredCoverAndNotTheGridsWallCover() {
        MapResult map = openStreetMap();
        NavigationGrid grid = map.grid;

        List<Doodad> parked = BattleSetup.stampVehicles(map, new Random(11L));
        Doodad truck = parked.get(0);
        int beside = truck.cellY - 1;

        // The grid's own wall-derived cover is suppressed on the footprint, so a
        // cell beside the truck reads zero until the prop publishes its level.
        assertEquals(0, grid.getCoverAtFacing(truck.cellX, beside, NavigationGrid.FACING_N),
                "suppression keeps the hull from doubling as a wall");
    }
}
