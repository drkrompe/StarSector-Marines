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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvoyMeansTest {

    @Test
    void localEntranceProbeMatchesTheRouteProofsMaskedGate() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        grid.setWalkable(4, 17, false);
        VehicleClearance mask = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(
                        VehicleType.HEAVY_APC.visualWidthCells));
        List<RoadGraph.Node> gates = List.of(
                new RoadGraph.Node(0, 4, 19, true),
                new RoadGraph.Node(1, 10, 19, true),
                new RoadGraph.Node(2, 0, 10, true),
                new RoadGraph.Node(3, 19, 10, true),
                new RoadGraph.Node(4, 10, 10, false));
        for (TraversalAxis axis : TraversalAxis.values()) {
            for (RoadGraph.Node gate : gates) {
                assertArrayEquals(ConvoyMeans.perimeterRouteCell(axis, mask,
                                gate, 20, 20),
                        ConvoyMeans.perimeterRouteCell(axis, grid,
                                gate, 20, 20));
            }
        }
    }

    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;
    /**
     * Ticks a proof may be stepped for before the test gives up. A proof is
     * {@code RouteProofJob.SEARCH_BUDGET / ConvoyMeans.SEARCHES_PER_TICK} steps
     * at the very worst, so anything past this is a job that is not converging.
     */
    private static final int PROOF_TICK_CAP = 32;

    /**
     * Dispatches the way {@link ReinforcementSystem} does — ask, step the means
     * a tick, ask again — until the answer settles.
     *
     * <p>A convoy proves its journey across ticks rather than inside the tick
     * that asked, so a single {@code dispatch} call now answers
     * {@link ReinforcementDispatchResult#RETRYABLE} whenever the enumeration is
     * still running. The production dispatcher refunds the ticket, re-posts the
     * request, and offers it again once the means says it has finished; a test
     * that asked once and read the first answer would be measuring the first
     * four searches rather than the delivery.
     */
    private static ReinforcementDispatchResult prove(
            ConvoyMeans means, BattleSimulation sim, ReinforcementRequest req) {
        ReinforcementDispatchResult result = means.dispatch(sim, req);
        for (int tick = 0; tick < PROOF_TICK_CAP
                && result == ReinforcementDispatchResult.RETRYABLE; tick++) {
            means.advance(BattleSimulation.TICK_DT, sim);
            result = means.dispatch(sim, req);
        }
        assertNotEquals(ReinforcementDispatchResult.RETRYABLE, result,
                "a route proof must settle inside its own search budget");
        return result;
    }

    @Test
    void southToNorthConquestEntersFromNorthAndDropsBehindMinimumFront() {
        BattleSimulation sim = openSim();
        assertRouteExists(sim, 15, HEIGHT - 3, 15, 28);
        TacticalNode target = target(15, 10);
        sim.setTacticalMap(new TacticalMap(List.of(target)));
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);

        ReinforcementDispatchResult result = prove(means, sim,
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
        assertEquals(VehicleType.HEAVY_APC.capacity, mission.marineLoadout.length);
        for (var loadout : mission.marineLoadout) {
            assertNotNull(loadout.primaryDef());
            assertNotNull(loadout.equipmentGrade);
            assertNotNull(loadout.soldierProfile);
            assertNotNull(loadout.armorFamily);
        }

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

        ReinforcementDispatchResult result = prove(means, sim,
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

        ReinforcementDispatchResult result = prove(means, sim,
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

        ReinforcementDispatchResult result = prove(means, sim,
                request(15, 10));

        assertEquals(ReinforcementDispatchResult.REJECTED, result);
        assertEquals(0, sim.getConvoyVehicleIds().length);
    }

    @Test
    void topologyChangeDiscardsAnInFlightProgressiveProof() {
        BattleSimulation sim = openSim();
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH, northGraph(), 20);
        ReinforcementRequest request = request(15, 10);

        assertEquals(ReinforcementDispatchResult.RETRYABLE,
                means.dispatch(sim, request));
        assertEquals(0, means.routeFieldCaptures(), "dispatch queues without copying the map");
        means.advance(BattleSimulation.TICK_DT, sim);
        assertEquals(1, means.routeFieldCaptures());
        sim.getGrid().setWalkable(5, 5, false);
        means.advance(BattleSimulation.TICK_DT, sim);

        assertEquals(ReinforcementDispatchResult.RETRYABLE,
                means.dispatch(sim, request));
        assertEquals(2, means.routeFieldCaptures(),
                "the proof must capture the new topology, not resume its old view");
    }

    @Test
    void snappedDropInsideBuildingIsRejected() {
        BattleSimulation sim = openSim();
        sim.getGrid().setWalkable(15, 29, false);
        sim.getTopology().setBuildingId(14, 27, 1);
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH,
                northGraph(), 20);

        ReinforcementDispatchResult result = prove(means, sim,
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
                prove(means, sim, request));
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

        ReinforcementDispatchResult result = prove(means, sim,
                request(15, HEIGHT - 1));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result);
        assertTrue(onlyMission(sim).lzY < HEIGHT - 3,
                "dispatch must use the later fully drivable junction");
    }

    @Test
    void disconnectedTopRankedDropDoesNotHideReachableLaterDrop() {
        BattleSimulation sim = openSim();
        for (int x = 0; x < WIDTH; x++) sim.getGrid().setWalkable(x, 25, false);
        RoadGraph.Node entry = new RoadGraph.Node(0, 15, HEIGHT - 1, true);
        RoadGraph.Node unreachable = new RoadGraph.Node(1, 15, 20, false);
        RoadGraph.Node unreachableLeft = new RoadGraph.Node(2, 9, 20, false);
        RoadGraph.Node unreachableRight = new RoadGraph.Node(3, 21, 20, false);
        RoadGraph.Node reachable = new RoadGraph.Node(4, 15, 30, false);
        RoadGraph.Node reachableBranch = new RoadGraph.Node(5, 21, 30, false);
        RoadGraph graph = graph(List.of(entry, unreachable, unreachableLeft,
                        unreachableRight, reachable, reachableBranch),
                edge(0, entry, unreachable), edge(1, unreachable, unreachableLeft),
                edge(2, unreachable, unreachableRight), edge(3, entry, reachable),
                edge(4, reachable, reachableBranch));
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH, graph, 20);

        assertEquals(ReinforcementDispatchResult.COMMITTED,
                prove(means, sim, request(15, 20)));
        assertTrue(onlyMission(sim).lzY > 25f,
                "the proof must skip the unreachable region and try the later drop");
    }

    @Test
    void legacyMissionWithoutPolicyKeepsDefenderSideEntryAndReinforcementOwnership() {
        BattleSimulation sim = openSim();
        ConvoyMeans means = new ConvoyMeans(
                eastGraph(), TraversalAxis.SOUTH_TO_NORTH);

        ReinforcementDispatchResult result = prove(means, sim,
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

    /**
     * The probe answers for the delivery, not for the map.
     *
     * <p>A graph gate sits on the map edge and an APC's body does not, so the
     * truck stages one cell in — and if that cell will not take the full pose
     * there is no way onto the map from this gate at all. Asking only whether
     * the graph has perimeter nodes said yes to every one of these.
     */
    @Test
    void aRearGateThatCannotTakeATruckIsRefusedByTheProbe() {
        BattleSimulation sim = supplied(openSim());
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH, northGraph(), 20);
        ReinforcementRequest req = request(15, 10);
        assertTrue(means.canFulfill(sim, req),
                "an open rear gate is a gate a truck can use");
        assertTrue(means.arrivalSeconds(sim, req) < Float.MAX_VALUE);
        assertEquals(0, means.routeFieldCaptures(),
                "entry probes must not capture full route inputs");

        sim.getGrid().setWalkable(15, HEIGHT - 3, false);

        assertFalse(means.canFulfill(sim, req),
                "and a blocked one is refused before an attempt is spent on it");
        assertEquals(Float.MAX_VALUE, means.arrivalSeconds(sim, req),
                "a means that cannot come never quotes a time");
        assertEquals(0, means.routeFieldCaptures());
    }

    /**
     * Perimeter nodes on the wrong edge are not entries. Under a strict
     * defender-rear deployment the eligible set is the rear edge alone, and a
     * graph that only reaches the map's flank has nowhere to bring a truck on
     * — however many perimeter nodes it has.
     */
    @Test
    void aGraphWithNoRearGateIsRefusedByTheProbe() {
        BattleSimulation sim = supplied(openSim());
        ConvoyMeans means = means(TraversalAxis.SOUTH_TO_NORTH, eastGraph(), 20);

        assertFalse(means.canFulfill(sim, request(15, 10)));
        assertEquals(ReinforcementDispatchResult.REJECTED,
                prove(means, sim, request(15, 10)),
                "and the commit still agrees with the probe");
    }

    /** A convoy is loaded out of the armory; without one there is nothing to ferry. */
    private static BattleSimulation supplied(BattleSimulation sim) {
        sim.getCompoundService().register(new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 50, 4));
        return sim;
    }

    private static ConvoyMeans means(TraversalAxis axis, RoadGraph graph,
                                     int minimumForward) {
        DeliveryDeploymentPolicy policy = request ->
                new DeliveryDeployment(request.rallyX, request.rallyY,
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
        DeliveryDeploymentPolicy policy = request -> new DeliveryDeployment(
                map.defenderSpawnX, map.defenderSpawnY, minimumForward,
                true, false,
                SquadCommandClaim.mission("conquest-defender", "canonical test"));
        ConvoyMeans means = new ConvoyMeans(map.roadGraph, axis,
                null, RiskLevel.LOW, policy);

        ReinforcementDispatchResult result = prove(means, sim,
                request(map.defenderSpawnX, map.defenderSpawnY));

        assertEquals(ReinforcementDispatchResult.COMMITTED, result,
                "canonical " + axis + " map needs a complete rear convoy route");
        VehicleMission mission = onlyMission(sim);
        assertNotNull(mission.routeFields);
        int mapCells = map.grid.getWidth() * map.grid.getHeight();
        assertTrue(mission.routeFields.clearanceEvaluations() < mapCells / 10,
                "proof should leave clearance unexamined outside its searched region: "
                        + mission.routeFields.clearanceEvaluations() + "/" + mapCells);
        assertTrue(mission.routeFields.costEvaluations() < mapCells / 10,
                "proof should leave terrain unpriced outside its searched region: "
                        + mission.routeFields.costEvaluations() + "/" + mapCells);
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
