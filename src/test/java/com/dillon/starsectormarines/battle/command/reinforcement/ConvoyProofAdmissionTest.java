package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Admission and request lifecycle only: one real gate, no possible drop junction. */
class ConvoyProofAdmissionTest {
    @Test
    void burstQueuesWithoutSnapshotsAndBoundsPreparedResultsUntilConsumed() {
        Fixture fixture = new Fixture();
        List<ReinforcementRequest> requests = new ArrayList<>();
        for (int i = 0; i < 104; i++) {
            ReinforcementRequest request = request();
            requests.add(request);
            assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(request));
        }
        assertEquals(0, fixture.means.routeFieldCaptures());
        assertEquals(104, fixture.means.queuedProofCount());
        assertEquals(0, fixture.means.preparedProofCount());

        // Finished-but-unconsumed proofs still own a slot. Zero elapsed time
        // isolates admission from the independent abandonment timeout.
        for (int tick = 1; tick <= 8; tick++) {
            int before = fixture.means.routeFieldCaptures();
            fixture.means.advance(0f, fixture.sim);
            assertTrue(fixture.means.routeFieldCaptures() - before <= 1);
            assertEquals(Math.min(tick, 4), fixture.means.preparedProofCount());
        }
        assertEquals(4, fixture.means.routeFieldCaptures());
        assertEquals(100, fixture.means.queuedProofCount());

        // All identical-valued but identity-distinct requests eventually get
        // their own answer when the caller consumes completed results.
        for (int tick = 0; tick < 110 && !requests.isEmpty(); tick++) {
            int before = fixture.means.routeFieldCaptures();
            fixture.means.advance(0f, fixture.sim);
            assertTrue(fixture.means.routeFieldCaptures() - before <= 1);
            assertTrue(fixture.means.preparedProofCount() <= 4);
            for (Iterator<ReinforcementRequest> it = requests.iterator(); it.hasNext();) {
                ReinforcementDispatchResult result = fixture.dispatch(it.next());
                if (result == ReinforcementDispatchResult.REJECTED) it.remove();
                else assertEquals(ReinforcementDispatchResult.RETRYABLE, result);
            }
        }
        assertTrue(requests.isEmpty(), "a bounded queue must keep making progress");
        assertEquals(104, fixture.means.routeFieldCaptures());
        assertEquals(0, fixture.means.preparedProofCount());
        assertEquals(0, fixture.means.queuedProofCount());
    }

    @Test
    void repeatedDispatchDoesNotDuplicateOrReorderAndAdmissionDoesNotSearchImmediately() {
        Fixture fixture = new Fixture();
        ReinforcementRequest first = request(), second = request(), third = request();
        fixture.dispatch(first);
        fixture.dispatch(second);
        fixture.dispatch(third);
        for (int i = 0; i < 10; i++) {
            fixture.dispatch(second);
            fixture.dispatch(first);
        }
        assertEquals(3, fixture.means.queuedProofCount());
        assertFalse(fixture.means.advance(0f, fixture.sim), "new snapshot is not searched yet");
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(first));
        assertEquals(1, fixture.means.routeFieldCaptures());
        assertTrue(fixture.means.advance(0f, fixture.sim));
        assertEquals(ReinforcementDispatchResult.REJECTED, fixture.dispatch(first));
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(second));
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(third));
        assertEquals(2, fixture.means.routeFieldCaptures());
        fixture.means.advance(0f, fixture.sim);
        assertEquals(ReinforcementDispatchResult.REJECTED, fixture.dispatch(second));
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(third));
    }

    @Test
    void abandonmentDiscardsQueuedAndPreparedRequestsBeforeAnyFurtherSnapshot() {
        Fixture fixture = new Fixture();
        fixture.dispatch(request());
        fixture.dispatch(request());
        float abandoned = ReinforcementService.REINFORCEMENT_TICK_PERIOD * 2f + 0.01f;
        assertFalse(fixture.means.advance(abandoned, fixture.sim));
        assertEquals(0, fixture.means.routeFieldCaptures());
        assertEquals(0, fixture.means.queuedProofCount());
        assertEquals(0, fixture.means.preparedProofCount());

        ReinforcementRequest first = request();
        fixture.dispatch(first);
        fixture.dispatch(request());
        fixture.means.advance(0f, fixture.sim);
        assertEquals(1, fixture.means.preparedProofCount());
        assertEquals(1, fixture.means.queuedProofCount());
        assertFalse(fixture.means.advance(abandoned, fixture.sim));
        assertEquals(1, fixture.means.routeFieldCaptures());
        assertEquals(0, fixture.means.queuedProofCount());
        assertEquals(0, fixture.means.preparedProofCount());
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(first));
        fixture.means.advance(0f, fixture.sim);
        assertEquals(2, fixture.means.routeFieldCaptures(), "an abandoned handle starts fresh");
    }

    @Test
    void topologyInvalidationRebuildsWithinAdmissionBudgetWithoutLosingSeniority() {
        Fixture fixture = new Fixture();
        ReinforcementRequest first = request(), second = request();
        fixture.dispatch(first);
        fixture.dispatch(second);
        fixture.means.advance(0f, fixture.sim);
        assertEquals(1, fixture.means.routeFieldCaptures());
        fixture.grid.setWalkable(0, 0, false);
        fixture.dispatch(second);
        fixture.means.advance(0f, fixture.sim);
        assertEquals(2, fixture.means.routeFieldCaptures());
        assertEquals(1, fixture.means.preparedProofCount());
        assertEquals(1, fixture.means.queuedProofCount());
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(first));
        fixture.means.advance(0f, fixture.sim);
        assertEquals(ReinforcementDispatchResult.REJECTED, fixture.dispatch(first),
                "the oldest request must be rebuilt before its younger sibling");
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(second));
        assertEquals(3, fixture.means.routeFieldCaptures());
    }

    @Test
    void entryClosedWhileQueuedRejectsAtAdmissionWithoutCapturingTheMap() {
        Fixture fixture = new Fixture();
        ReinforcementRequest request = request();
        assertEquals(ReinforcementDispatchResult.RETRYABLE, fixture.dispatch(request));
        fixture.grid.setWalkable(10, 17, false);
        assertTrue(fixture.means.advance(0f, fixture.sim));
        assertEquals(0, fixture.means.routeFieldCaptures());
        assertEquals(0, fixture.means.queuedProofCount());
        assertEquals(0, fixture.means.preparedProofCount());
        assertEquals(ReinforcementDispatchResult.REJECTED, fixture.dispatch(request));
    }

    private static ReinforcementRequest request() {
        return new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.OBJECTIVE_LOST,
                ReinforcementRequest.Strength.SMALL, 10, 10);
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(20, 20);
        final CellTopology topology = new CellTopology(20, 20);
        final ConvoyMeans means = new ConvoyMeans(new RoadGraph(
                List.of(new RoadGraph.Node(0, 10, 19, true)), List.of()),
                TraversalAxis.SOUTH_TO_NORTH);
        final BattleControl sim;

        Fixture() {
            for (int y = 0; y < 20; y++) {
                for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
            }
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getTopology" -> topology;
                        case "getNavigationGridRevision" -> grid.topologyRevision();
                        case "getConvoyVehicleIds" -> new long[0];
                        default -> throw new AssertionError("Unexpected battle query: " + method.getName());
                    });
        }

        ReinforcementDispatchResult dispatch(ReinforcementRequest request) {
            return means.dispatch(sim, request);
        }
    }
}
