package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.MapVehicle;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceportVehiclePlacementTest {

    @Test
    void groundCrewSpawnOnServiceSideOutsideEveryBerth() {
        NavigationGrid grid = openStripedGrid(52, 24);
        CellTopology topology = new CellTopology(52, 24);
        paintStriped(topology, 52, 24);
        LandingPad first = LandingPad.spaceport(14, 12, LandingPad.Approach.EAST);
        LandingPad second = LandingPad.spaceport(38, 12, LandingPad.Approach.WEST);
        MapResult map = map(grid, topology, List.of(first, second));
        List<ParkedAircraft> parked = BattleSetup.stampParkedAircraft(
                map, Collections.emptyList(), new Random(4L));
        BattleSimulation sim = new BattleSimulation(grid, topology);

        int count = BattleSetup.spawnSpaceportGroundCrew(
                sim, map, parked, new Random(4L));

        assertTrue(count == 4);
        int engineers = 0;
        int civilians = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            assertTrue(sim.identity().name(unit).startsWith("port-crew-"));
            assertTrue(sim.identity().faction(unit) == Faction.CIVILIAN);
            if (sim.identity().type(unit) == UnitType.ENGINEER) engineers++;
            if (sim.identity().type(unit) == UnitType.CIVILIAN) civilians++;
            int x = sim.world().cellX(unit);
            int y = sim.world().cellY(unit);
            assertFalse(first.contains(x, y));
            assertFalse(second.contains(x, y));
            assertTrue(grid.isWalkable(x, y));
        }
        assertTrue(engineers == 2);
        assertTrue(civilians == 2);
    }

    @Test
    void generatedTierOnePortHasCivilianOccupancyAfterDefaultDeploymentReservation() {
        TargetProfile profile = new TargetProfile(5, 6, 1, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT));
        MapResult map = new BspCityGenerator().generate(80, 80, 42L, null, profile);
        List<LandingPad> deployment = LandingPadSelector.select(map, 3, 8);

        List<ParkedAircraft> parked = BattleSetup.stampParkedAircraft(
                map, deployment, new Random(42L));

        assertTrue(deployment.size() == 3);
        for (LandingPad pad : deployment) {
            assertTrue(pad.purpose == LandingPad.Purpose.CIVILIAN_SPACEPORT,
                    "default deployment should use an authored port berth, not fallback pavement");
            assertTrue(pad.isClear(map.grid, map.topology));
        }
        assertFalse(parked.isEmpty(),
                "default deployment should leave a civilian berth occupied");
    }

    @Test
    void civilianCraftOccupyOnlySurplusBerthsAndLeaveSafetyRingOpen() {
        NavigationGrid grid = openStripedGrid(52, 24);
        CellTopology topology = new CellTopology(52, 24);
        paintStriped(topology, 52, 24);
        LandingPad reservedA = LandingPad.spaceport(7, 12, LandingPad.Approach.EAST);
        LandingPad reservedB = LandingPad.spaceport(19, 12, LandingPad.Approach.EAST);
        LandingPad surplusA = LandingPad.spaceport(31, 12, LandingPad.Approach.WEST);
        LandingPad surplusB = LandingPad.spaceport(43, 12, LandingPad.Approach.WEST);
        MapResult map = map(grid, topology,
                List.of(reservedA, reservedB, surplusA, surplusB));

        List<ParkedAircraft> parked = BattleSetup.stampParkedAircraft(
                map, List.of(reservedA, reservedB), new Random(9L));

        assertTrue(parked.size() == 2);
        assertTrue(reservedA.isClear(grid, topology));
        assertTrue(reservedB.isClear(grid, topology));
        assertFalse(surplusA.isClear(grid, topology));
        assertFalse(surplusB.isClear(grid, topology));
        for (ParkedAircraft aircraft : parked) {
            assertFalse(grid.isWalkable(aircraft.centerX, aircraft.centerY));
            assertTrue(grid.isWalkable(aircraft.centerX - 2, aircraft.centerY));
            assertTrue(grid.isWalkable(aircraft.centerX + 2, aircraft.centerY));
            assertTrue(grid.isWalkable(aircraft.centerX, aircraft.centerY - 2));
            assertTrue(grid.isWalkable(aircraft.centerX, aircraft.centerY + 2));
        }
    }

    @Test
    void serviceTrafficUsesApronButLeavesEveryBerthClear() {
        NavigationGrid grid = openStripedGrid(40, 24);
        CellTopology topology = new CellTopology(40, 24);
        paintStriped(topology, 40, 24);
        LandingPad first = LandingPad.spaceport(10, 12, LandingPad.Approach.EAST);
        LandingPad second = LandingPad.spaceport(28, 12, LandingPad.Approach.WEST);
        MapResult map = map(grid, topology, List.of(first, second));

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

    private static NavigationGrid openStripedGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static void paintStriped(CellTopology topology, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
            }
        }
    }

    private static MapResult map(NavigationGrid grid, CellTopology topology,
                                 List<LandingPad> pads) {
        return new MapResult(grid, topology, 10, 12, 30, 12,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, pads);
    }
}
