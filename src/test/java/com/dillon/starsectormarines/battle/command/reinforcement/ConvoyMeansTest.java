package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvoyMeansTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;

    @Test
    void southToNorthConquestEntersFromNorthAndDropsBehindMinimumFront() {
        BattleSimulation sim = openSim();
        assertRouteExists(sim, 15, HEIGHT - 3, 15, 28);
        TacticalNode target = target(15, 10);
        sim.setTacticalMap(new TacticalMap(List.of(target)));
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(15, 10));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result);
        VehicleMission mission = onlyMission(sim);
        assertTrue(mission.inboundY[0] > HEIGHT,
                "Conquest convoy must begin beyond the defender north edge");
        assertTrue(mission.lzY >= 20f,
                "drop must remain behind the commander-authored front band");
        assertEquals("conquest-defender", mission.commandClaim.issuer());
        assertEquals(target, mission.assignNode);
        assertTrue(mission.commandOwnsObjective);

        sim.spawn(new EntitySpec("battle-keeps-running", Faction.MARINE,
                UnitType.MARINE, 5, 5).moveSpeed(0f).health(10_000f));
        mission.state = VehicleState.LANDED;
        mission.deboardCountdown = 0f;
        for (int i = 0; i < 200 && mission.marinesRemaining > 0; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        Squad delivered = sim.getSquad(mission.squadId);
        assertNotNull(delivered);
        assertEquals(target, delivered.assignedNode);
        assertEquals(AssignmentKind.HOLD_NODE,
                delivered.assignedObjective.kind());
        CommandDirective claim = sim.getSquadCommandDirective(delivered.id);
        assertEquals(CommandAuthority.MISSION_COMMAND, claim.authority());
        assertEquals("conquest-defender", claim.issuer());
        assertEquals(VehicleType.HEAVY_APC.capacity,
                sim.squadMemberCount(delivered.id));

    }

    @Test
    void westToEastConquestEntersFromEast() {
        BattleSimulation sim = openSim();
        assertRouteExists(sim, WIDTH - 3, 15, 28, 15);
        ConvoyMeans means = means(TraversalAxis.WEST_TO_EAST,
                eastGraph(), 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(10, 15));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result);
        VehicleMission mission = onlyMission(sim);
        assertTrue(mission.inboundX[0] > WIDTH,
                "Conquest convoy must begin beyond the defender east edge");
        assertTrue(mission.lzX >= 20f);
    }

    @Test
    void missingDefenderRearRoadRejectsWithoutCreatingVehicle() {
        BattleSimulation sim = openSim();
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                eastGraph(), 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(15, 10));

        assertEquals(ReinforcementDispatchResult.REJECTED, result);
        assertEquals(0, sim.getConvoyVehicleIds().length);
    }

    @Test
    void blockedStraightRearIngressRejectsInsteadOfSnappingSideways() {
        BattleSimulation sim = openSim();
        sim.getGrid().setWalkable(15, HEIGHT - 3, false);
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(15, 10));

        assertEquals(ReinforcementDispatchResult.REJECTED, result);
        assertEquals(0, sim.getConvoyVehicleIds().length);
    }

    @Test
    void snappedDropInsideBuildingIsRejected() {
        BattleSimulation sim = openSim();
        sim.getGrid().setWalkable(15, 29, false);
        sim.getTopology().setBuildingId(14, 27, 1);
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(15, 28));

        assertEquals(ReinforcementDispatchResult.REJECTED, result);
        assertEquals(0, sim.getConvoyVehicleIds().length);
    }

    @Test
    void objectiveLostConvoySquadReceivesCommanderOwnedZoneClear() {
        BattleSimulation sim = openSim();
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);
        ReinforcementRequest request = new ReinforcementRequest(
                Faction.DEFENDER,
                ReinforcementRequest.Reason.OBJECTIVE_LOST,
                ReinforcementRequest.Strength.SMALL,
                15, 28, 15, 10);

        assertEquals(ReinforcementDispatchResult.COMMITTED,
                means.dispatch(sim, request));
        VehicleMission mission = onlyMission(sim);
        assertNull(mission.assignNode);
        assertTrue(mission.assignZoneId >= 0);
        sim.spawn(new EntitySpec("battle-keeps-running", Faction.MARINE,
                UnitType.MARINE, 5, 5).moveSpeed(0f).health(10_000f));
        mission.state = VehicleState.LANDED;
        mission.deboardCountdown = 0f;
        for (int i = 0; i < 20 && mission.squadId == Squad.NO_SQUAD; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        Squad delivered = sim.getSquad(mission.squadId);
        assertEquals(AssignmentKind.CLEAR_ZONE,
                delivered.assignedObjective.kind());
        assertEquals(mission.assignZoneId,
                delivered.assignedObjective.targetZoneId());
        assertEquals(CommandAuthority.MISSION_COMMAND,
                sim.getSquadCommandDirective(delivered.id).authority());
    }

    @Test
    void rejectedFirstJunctionFallsThroughToNextCompleteRoute() {
        BattleSimulation sim = openSim();
        RoadGraph.Node entry = new RoadGraph.Node(0, 15, HEIGHT - 1, true);
        // This high-degree candidate ranks first but snaps to a pose whose APC
        // body still hangs off-map, so full route proof must reject it.
        RoadGraph.Node bad = new RoadGraph.Node(1, 15, HEIGHT - 2, false);
        RoadGraph.Node badLeft = new RoadGraph.Node(2, 10, HEIGHT - 2, false);
        RoadGraph.Node badRight = new RoadGraph.Node(3, 20, HEIGHT - 2, false);
        RoadGraph.Node good = new RoadGraph.Node(4, 15, 28, false);
        RoadGraph.Node goodBranch = new RoadGraph.Node(5, 21, 28, false);
        RoadGraph graph = graph(List.of(entry, bad, badLeft, badRight,
                        good, goodBranch),
                edge(0, entry, bad), edge(1, bad, badLeft),
                edge(2, bad, badRight), edge(3, bad, good),
                edge(4, good, goodBranch));
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH, graph, 20);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(15, HEIGHT - 1));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result);
        assertTrue(onlyMission(sim).lzY < HEIGHT - 3,
                "dispatch must use the later fully drivable junction");
    }

    @Test
    void legacyMissionWithoutPolicyKeepsDefenderSideEntryAndReinforcementOwnership() {
        BattleSimulation sim = openSim();
        ConvoyMeans means = new ConvoyMeans(
                eastGraph(), TraversalAxis.SOUTH_TO_NORTH);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(28, 15));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result);
        VehicleMission mission = onlyMission(sim);
        assertTrue(mission.inboundX[0] > WIDTH,
                "legacy policy may still use a lateral defender-side entry");
        assertEquals(CommandAuthority.REINFORCEMENT,
                mission.commandClaim.authority());
        assertTrue(!mission.commandOwnsObjective);
    }

    @Test
    void canonicalConquestMapsCommitFromDefenderRearOnBothAxes() {
        assertCanonicalRearDispatch(TraversalAxis.SOUTH_TO_NORTH);
        assertCanonicalRearDispatch(TraversalAxis.WEST_TO_EAST);
    }

    private static ConvoyMeans means(TraversalAxis axis, RoadGraph graph,
                                     int minimumForward) {
        ConvoyDeploymentPolicy policy = request ->
                new ConvoyDeployment(request.rallyX, request.rallyY,
                        minimumForward, true, request.hasObjective(),
                        SquadCommandClaim.mission(
                                "conquest-defender", "test relief"));
        return new ConvoyMeans(graph, axis, null, RiskLevel.LOW, policy);
    }

    private static VehicleMission onlyMission(BattleSimulation sim) {
        long[] ids = sim.getConvoyVehicleIds();
        assertEquals(1, ids.length);
        VehicleMission mission = sim.convoyMission(ids[0]);
        assertNotNull(mission);
        return mission;
    }

    private static RoadGraph northGraph() {
        RoadGraph.Node entry = new RoadGraph.Node(0, 15, HEIGHT - 1, true);
        RoadGraph.Node destination = new RoadGraph.Node(1, 15, 28, false);
        RoadGraph.Node branch = new RoadGraph.Node(2, 21, 28, false);
        return graph(entry, destination, branch);
    }

    private static RoadGraph eastGraph() {
        RoadGraph.Node entry = new RoadGraph.Node(0, WIDTH - 1, 15, true);
        RoadGraph.Node destination = new RoadGraph.Node(1, 28, 15, false);
        RoadGraph.Node branch = new RoadGraph.Node(2, 28, 21, false);
        return graph(entry, destination, branch);
    }

    private static RoadGraph graph(RoadGraph.Node entry,
                                   RoadGraph.Node destination,
                                   RoadGraph.Node branch) {
        RoadGraph.Edge inbound = edge(0, entry, destination);
        RoadGraph.Edge spur = edge(1, destination, branch);
        return new RoadGraph(List.of(entry, destination, branch),
                List.of(inbound, spur));
    }

    private static RoadGraph graph(List<RoadGraph.Node> nodes,
                                   RoadGraph.Edge... edges) {
        return new RoadGraph(nodes, List.of(edges));
    }

    private static RoadGraph.Edge edge(int id, RoadGraph.Node a,
                                       RoadGraph.Node b) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        int x = a.cellX;
        int y = a.cellY;
        xs.add(x);
        ys.add(y);
        while (x != b.cellX || y != b.cellY) {
            x += Integer.compare(b.cellX, x);
            y += Integer.compare(b.cellY, y);
            xs.add(x);
            ys.add(y);
        }
        return new RoadGraph.Edge(id, a, b,
                xs.stream().mapToInt(Integer::intValue).toArray(),
                ys.stream().mapToInt(Integer::intValue).toArray());
    }

    private static ReinforcementRequest request(int x, int y) {
        return new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                x, y, x, y);
    }

    private static void assertCanonicalRearDispatch(TraversalAxis axis) {
        MapResult map = new BspCityGenerator().generate(
                240, 160, 42L, axis);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        int minimumForward = axis == TraversalAxis.WEST_TO_EAST ? 120 : 80;
        ConvoyDeploymentPolicy policy = request -> new ConvoyDeployment(
                map.defenderSpawnX, map.defenderSpawnY, minimumForward,
                true, false,
                SquadCommandClaim.mission("conquest-defender", "canonical test"));
        ConvoyMeans means = new ConvoyMeans(map.roadGraph, axis,
                null, RiskLevel.LOW, policy);

        ReinforcementDispatchResult result = means.dispatch(sim,
                request(map.defenderSpawnX, map.defenderSpawnY));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result,
                "canonical " + axis + " map needs a complete rear convoy route");
        VehicleMission mission = onlyMission(sim);
        if (axis == TraversalAxis.WEST_TO_EAST) {
            assertTrue(mission.inboundX[0] > map.grid.getWidth());
            assertTrue(mission.lzX >= minimumForward);
        } else {
            assertTrue(mission.inboundY[0] > map.grid.getHeight());
            assertTrue(mission.lzY >= minimumForward);
        }
    }

    private static TacticalNode target(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.GUARDPOST,
                x, y, x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 2);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static void assertRouteExists(BattleSimulation sim,
                                          int startX, int startY,
                                          int goalX, int goalY) {
        VehicleClearance clearance = VehicleClearance.erode(sim.getGrid(), 1);
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        assertNotNull(VehicleRoutePlanner.route(
                startX, startY, goalX, goalY, sim.getGrid(), cost, clearance),
                "clearance route must exist");
        assertNotNull(VehicleRoutePlanner.routeDrivable(
                startX, startY, goalX, goalY, sim.getGrid(),
                cost, clearance, VehicleType.HEAVY_APC),
                "straight clearance route must be drivable");
    }
}
