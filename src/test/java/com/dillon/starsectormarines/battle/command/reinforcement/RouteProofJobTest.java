package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.ProgressiveVehicleField;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The proof cursor and its lifetime budget, without a battle or map generator. */
class RouteProofJobTest {
    @Test
    void lastAttemptRejectingATurnCannotSpinWaitingForAnUnfundedRetry() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 0, 12, 12);
        carve(grid, 10, 10, 25, 12);
        RouteProofJob job = job(grid, 11, 0, 24, 11);
        reserveAttempts(job, RouteProofJob.SEARCH_BUDGET - 1);

        // This width-valid elbow requires another attempt after turn refinement.
        assertEquals(RouteProofJob.State.RUNNING, job.step(1, 1));
        assertTrue(job.budget().isExhausted());
        assertTrue(job.expandedNodesThisStep() > 0);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertEquals(RouteProofJob.State.NO_ROUTE, job.step(1)));
        assertTrue(job.expandedNodesThisStep() > 0);
        assertNull(job.plan());
    }

    @Test
    void alreadyPaidLastAttemptKeepsItsFrontierAndCanProveTheJourney() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 0, 0, 29, 29);
        RouteProofJob job = job(grid, 15, 0, 15, 15);
        reserveAttempts(job, RouteProofJob.SEARCH_BUDGET - 2);

        // The first attempt proves inbound; outbound owns the last lifetime slot.
        for (int i = 0; i < 2000 && !job.budget().isExhausted(); i++) {
            assertEquals(RouteProofJob.State.RUNNING, job.step(1, 1));
            assertTrue(job.expandedNodesThisStep() <= 1);
        }
        assertTrue(job.budget().isExhausted());
        assertEquals(RouteProofJob.State.RUNNING, job.state());
        for (int i = 0; i < 2000 && job.state() == RouteProofJob.State.RUNNING; i++) {
            job.step(1, 1);
            assertTrue(job.expandedNodesThisStep() <= 1);
        }

        assertEquals(RouteProofJob.State.PROVED, job.state());
        assertNotNull(job.plan());
        assertEquals(RouteProofJob.SEARCH_BUDGET, job.budget().spent());
    }

    private static RouteProofJob job(NavigationGrid grid, int entryX, int entryY,
                                     int dropX, int dropY) {
        RoadGraph.Node entry = new RoadGraph.Node(0, entryX, entryY, true);
        RoadGraph.Node drop = new RoadGraph.Node(1, dropX, dropY, false);
        RoadGraph.Node branch = new RoadGraph.Node(2, dropX - 1, dropY, false);
        RoadGraph graph = new RoadGraph(List.of(entry, drop, branch), List.of(
                edge(0, entry, drop), edge(1, drop, branch)));
        CellTopology topology = new CellTopology(grid.getWidth(), grid.getHeight());
        return RouteProofJob.start(graph, TraversalAxis.SOUTH_TO_NORTH,
                new DeliveryDeployment(dropX, dropY, -1, true, false, null),
                List.of(entry), grid.topologyRevision(),
                ProgressiveVehicleField.capture(grid, topology, 1),
                new LandingZoneScorer(grid, topology), List.of());
    }

    private static RoadGraph.Edge edge(int id, RoadGraph.Node a, RoadGraph.Node b) {
        return new RoadGraph.Edge(id, a, b,
                new int[]{a.cellX, b.cellX}, new int[]{a.cellY, b.cellY});
    }

    private static void reserveAttempts(RouteProofJob job, int attempts) {
        for (int i = 0; i < attempts; i++) assertTrue(job.budget().claim());
    }

    private static void carve(NavigationGrid grid, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) grid.setWalkableFloor(x, y);
        }
    }
}
