package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in full-size Conquest map evidence, excluded from the ordinary unit suite. */
@Tag("convoy-route-evidence")
class ConvoyRouteProofEvidenceTest {

    @Test
    void capturesProgressiveProofShapeOnLargeConquestMaps() throws Exception {
        JSONObject report = new JSONObject();
        report.put("schemaVersion", 1);
        report.put("mapWidth", 560);
        report.put("mapHeight", 336);
        report.put("seed", 42);
        for (TraversalAxis axis : new TraversalAxis[]{
                TraversalAxis.SOUTH_TO_NORTH, TraversalAxis.WEST_TO_EAST}) {
            report.put(axis.name(), run(axis));
        }
        Path outputDir = Path.of(System.getProperty("battle.convoyRoute.outputDir",
                "build/reports/performance/convoy-route"));
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("summary.json");
        Files.writeString(output, report.toString(2), StandardCharsets.UTF_8);
        System.out.println("Convoy route evidence: " + output.toAbsolutePath());
    }

    private static JSONObject run(TraversalAxis axis) throws Exception {
        MapResult map = new BspCityGenerator().generate(560, 336, 42L, axis);
        int minimumForward = axis == TraversalAxis.WEST_TO_EAST ? 280 : 168;
        DeliveryDeploymentPolicy policy = request -> new DeliveryDeployment(
                map.defenderSpawnX, map.defenderSpawnY, minimumForward,
                true, false, SquadCommandClaim.mission(
                "conquest-defender", "convoy route evidence"));
        ConvoyMeans means = new ConvoyMeans(map.roadGraph, axis,
                null, RiskLevel.LOW, policy);
        ReinforcementRequest request = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                map.defenderSpawnX, map.defenderSpawnY,
                map.defenderSpawnX, map.defenderSpawnY);
        TickInnerProfile profile = new TickInnerProfile();
        try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
            TickInnerProfile.setCurrent(profile);
            long started = System.nanoTime();
            ReinforcementDispatchResult result = means.dispatch(sim, request);
            long firstDispatchNanos = System.nanoTime() - started;
            long snapshotNanos = profile.nanosOf(
                    TickInnerProfile.Bucket.CONVOY_PROGRESSIVE_SNAPSHOT);
            assertEquals(ReinforcementDispatchResult.RETRYABLE, result);
            int ticks = 0;
            int maxExpanded = 0;
            long maxStepNanos = 0L;
            long totalStepNanos = 0L;
            long totalExpanded = 0L;
            long searches = 0L;
            JSONObject worstStep = new JSONObject();
            while (result == ReinforcementDispatchResult.RETRYABLE && ticks < 256) {
                profile.reset();
                started = System.nanoTime();
                boolean finished = means.advance(BattleSimulation.TICK_DT, sim);
                long stepNanos = System.nanoTime() - started;
                totalStepNanos += stepNanos;
                if (stepNanos > maxStepNanos) {
                    maxStepNanos = stepNanos;
                    worstStep = new JSONObject()
                            .put("tick", ticks + 1)
                            .put("expandedNodes", profile.convoyExpandedNodes())
                            .put("searchesStarted", profile.convoySearchesStarted())
                            .put("clearanceCells", profile.convoyClearanceEvaluations())
                            .put("terrainCostCells", profile.convoyCostEvaluations());
                }
                maxExpanded = (int) Math.max(maxExpanded, profile.convoyExpandedNodes());
                totalExpanded += profile.convoyExpandedNodes();
                searches += profile.convoySearchesStarted();
                ticks++;
                // The service reoffers requests once per second and drains a
                // completed proof immediately; dispatching on every test tick
                // would hide proof-abandonment mistakes on a long search.
                if (finished || ticks % 30 == 0) {
                    result = means.dispatch(sim, request);
                }
            }
            assertEquals(ReinforcementDispatchResult.COMMITTED, result,
                    "large Conquest map should admit its generated rear convoy");
            long[] ids = sim.getConvoyVehicleIds();
            assertEquals(1, ids.length);
            VehicleMission mission = sim.convoyMission(ids[0]);
            assertNotNull(mission.routeFields);
            assertTrue(maxExpanded <= RouteProofJob.EXPANSIONS_PER_TICK);
            int cells = map.grid.getWidth() * map.grid.getHeight();
            return new JSONObject()
                    .put("result", result.name())
                    .put("cells", cells)
                    .put("proofTicks", ticks)
                    .put("firstDispatchMs", millis(firstDispatchNanos))
                    .put("snapshotMs", millis(snapshotNanos))
                    .put("totalStepMs", millis(totalStepNanos))
                    .put("maxStepMs", millis(maxStepNanos))
                    .put("worstStep", worstStep)
                    .put("maxExpandedNodesPerStep", maxExpanded)
                    .put("totalExpandedNodes", totalExpanded)
                    .put("searchesStarted", searches)
                    .put("clearanceCells", mission.routeFields.clearanceEvaluations())
                    .put("terrainCostCells", mission.routeFields.costEvaluations());
        } finally {
            TickInnerProfile.setCurrent(null);
        }
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0; }
}
