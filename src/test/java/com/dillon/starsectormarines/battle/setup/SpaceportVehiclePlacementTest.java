package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.MapVehicle;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceportVehiclePlacementTest {

    @Test
    void serviceTrafficUsesApronButLeavesEveryBerthClear() {
        NavigationGrid grid = new NavigationGrid(40, 24);
        CellTopology topology = new CellTopology(40, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 40; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
            }
        }
        LandingPad first = LandingPad.spaceport(10, 12, LandingPad.Approach.EAST);
        LandingPad second = LandingPad.spaceport(28, 12, LandingPad.Approach.WEST);
        MapResult map = new MapResult(grid, topology, 10, 12, 30, 12,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, List.of(first, second));

        List<MapVehicle> vehicles = BattleSetup.stampVehicles(map, new Random(7L));

        assertFalse(vehicles.isEmpty(), "civilian apron should receive service traffic");
        assertTrue(first.isClear(grid, topology));
        assertTrue(second.isClear(grid, topology));
        for (MapVehicle vehicle : vehicles) {
            for (int dy = 0; dy < vehicle.kind.footprintCellsY; dy++) {
                for (int dx = 0; dx < vehicle.kind.footprintCellsX; dx++) {
                    int x = vehicle.cellX + dx;
                    int y = vehicle.cellY + dy;
                    assertFalse(first.contains(x, y));
                    assertFalse(second.contains(x, y));
                }
            }
        }
    }
}
