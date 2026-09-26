package com.dillon.starsectormarines.battle.profile;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureTestSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickInnerProfileTest {

    @Test
    void swarmPathfindingRetainsAggregateAndCallerAttribution() {
        TickInnerProfile profile = new TickInnerProfile();

        profile.enterBehavior(TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE);
        profile.record(TickInnerProfile.Bucket.PATHFIND, 120L);
        profile.exitBehavior();

        profile.enterBehavior(TickInnerProfile.Bucket.BEHAVIOR_COMBATANT);
        profile.record(TickInnerProfile.Bucket.PATHFIND, 40L);
        profile.exitBehavior();

        assertEquals(160L, profile.nanosOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(120L, profile.nanosOf(TickInnerProfile.Bucket.SWARM_PATHFIND));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SWARM_PATHFIND));
    }

    @Test
    void resetClearsCallerScopeAlongWithCounters() {
        TickInnerProfile profile = new TickInnerProfile();
        profile.enterBehavior(TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE);

        profile.reset();
        profile.record(TickInnerProfile.Bucket.PATHFIND, 25L);

        assertEquals(25L, profile.nanosOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(0L, profile.nanosOf(TickInnerProfile.Bucket.SWARM_PATHFIND));
    }

    @Test
    void pathRequestMetricsMeasureGoalFanInAndReset() {
        TickInnerProfile profile = new TickInnerProfile();
        profile.recordPathfindRequest(1, 2, 9, 10, true);
        profile.recordPathfindRequest(3, 4, 9, 10, true);
        profile.recordPathfindRequest(1, 2, 9, 10, false);
        profile.recordPathfindRequest(5, 6, 11, 12, false);

        assertEquals(4, profile.pathfindRequestCount());
        assertEquals(2, profile.occupancyPathfindRequestCount());
        assertEquals(3, profile.uniquePathfindRequestCount());
        assertEquals(2, profile.uniquePathfindGoalCount());
        assertEquals(3, profile.maximumPathfindGoalFanIn());

        profile.reset();

        assertEquals(0, profile.pathfindRequestCount());
        assertEquals(0, profile.uniquePathfindGoalCount());
        assertEquals(0, profile.maximumPathfindGoalFanIn());
    }

    @Test
    void workerMergeUnionsPathRequestMetrics() {
        TickInnerProfile aggregate = new TickInnerProfile();
        TickInnerProfile worker = new TickInnerProfile();
        aggregate.recordPathfindRequest(1, 1, 8, 8, true);
        worker.recordPathfindRequest(2, 2, 8, 8, true);

        aggregate.addFrom(worker);

        assertEquals(2, aggregate.pathfindRequestCount());
        assertEquals(1, aggregate.uniquePathfindGoalCount());
        assertEquals(2, aggregate.maximumPathfindGoalFanIn());
    }

    @Test
    void slowSearchesMergeByIndividualDurationAndFreezeAcrossReset() {
        TickInnerProfile aggregate = new TickInnerProfile();
        TickInnerProfile worker = new TickInnerProfile();
        for (int i = 0; i < 10; i++) {
            aggregate.enterAction(100L + i, i, "DEFEND_TRACK");
            aggregate.routeReason("fallback");
            aggregate.recordPathSearch(i + 1L, i, 1, 20, 2,
                    false, i + 1, i + 2);
        }
        worker.enterAction(900L, 15, "DEFEND_SITE");
        worker.routeReason("rally");
        worker.recordPathSearch(20L, 50, 3, 80, 4, true, 0, 150);
        worker.exitAction();
        worker.recordPathSearch(5L, 51, 3, 81, 4, false, 12, 8);

        aggregate.addFrom(worker);
        TickInnerProfile.Snapshot frozen = aggregate.snapshot();
        List<TickInnerProfile.PathSearch> top = frozen.slowPathSearches;

        assertEquals(8, top.size());
        assertEquals(20L, top.get(0).nanos());
        assertEquals(50, top.get(0).startX());
        assertTrue(top.get(0).usesOccupancy());
        assertEquals(0, top.get(0).pathCells());
        assertContext(top.get(0), 900L, 15, "DEFEND_SITE", "rally");
        assertEquals(10L, top.get(1).nanos());
        assertContext(top.get(1), 109L, 9, "DEFEND_TRACK", "fallback");
        assertEquals(5L, top.get(7).nanos());
        assertContext(top.get(6), 104L, 4, "DEFEND_TRACK", "fallback");
        assertContext(top.get(7), 0L, -1, "", "");
        assertEquals(223L, frozen.pathfindExpandedNodes);

        aggregate.reset();
        worker.reset();
        assertTrue(aggregate.slowPathSearches().isEmpty());
        assertEquals(0L, aggregate.pathfindExpandedNodes());
        assertEquals(20L, frozen.slowPathSearches.get(0).nanos());
        assertContext(frozen.slowPathSearches.get(0), 900L, 15, "DEFEND_SITE", "rally");
        assertEquals(223L, frozen.pathfindExpandedNodes);
    }

    @Test
    void workerMergeTransfersSearchSamplesAndResetsWorkerStorage() {
        TickInnerProfile.releaseCurrentThread();
        TickInnerProfile destination = new TickInnerProfile();
        TickInnerProfile worker = TickInnerProfile.current();
        try {
            worker.enterAction(500L, 8, "DEFEND_SITE");
            worker.routeReason("entrance");
            worker.recordPathSearch(600L, 2, 3, 10, 11,
                    true, 9, 40);
            TickInnerProfile.mergeAllInto(destination);

            assertEquals(40L, destination.pathfindExpandedNodes());
            assertEquals(600L, destination.slowPathSearches().get(0).nanos());
            assertContext(destination.slowPathSearches().get(0),
                    500L, 8, "DEFEND_SITE", "entrance");
            assertEquals(0L, worker.pathfindExpandedNodes());
            assertTrue(worker.slowPathSearches().isEmpty());
            worker.recordPathSearch(1L, 0, 0, 1, 1, false, 2, 1);
            assertContext(worker.slowPathSearches().get(0), 0L, -1, "", "");
        } finally {
            TickInnerProfile.releaseCurrentThread();
        }
    }

    @Test
    void actionScopeClearsReasonBetweenActionsAndOnExitOrReset() {
        TickInnerProfile profile = new TickInnerProfile();
        profile.enterAction(1L, 2, "DEFEND_SITE");
        profile.routeReason("rally");
        profile.enterAction(3L, 4, "DEFEND_TRACK");
        profile.recordPathSearch(4L, 0, 0, 1, 1, false, 2, 1);
        profile.exitAction();
        profile.recordPathSearch(3L, 0, 0, 1, 1, false, 2, 1);
        List<TickInnerProfile.PathSearch> frozen = profile.slowPathSearches();
        assertContext(frozen.get(0), 3L, 4, "DEFEND_TRACK", "");
        assertContext(frozen.get(1), 0L, -1, "", "");

        profile.enterAction(5L, 6, "DEFEND_SITE");
        profile.routeReason("stale");
        profile.reset();
        profile.recordPathSearch(2L, 0, 0, 1, 1, false, 2, 1);
        assertContext(profile.slowPathSearches().get(0), 0L, -1, "", "");
        assertContext(frozen.get(0), 3L, 4, "DEFEND_TRACK", "");

        profile.enterAction(7L, 8, null);
        profile.routeReason(null);
        profile.recordPathSearch(5L, 0, 0, 1, 1, false, 2, 1);
        assertContext(profile.slowPathSearches().get(0), 7L, 8, "", "");
    }

    @Test
    void actionTotalsAccumulateAcrossTickResetsWithoutSharingMutableCounters() {
        TickInnerProfile profile = new TickInnerProfile();
        Map<String, long[]> totals = new HashMap<>();
        profile.recordAction("DEFEND_SITE", 10L);
        profile.accumulateActionTotals(totals);
        profile.reset();
        profile.recordAction("DEFEND_SITE", 20L);
        profile.recordAction("DEFEND_TRACK", 30L);
        profile.accumulateActionTotals(totals);
        profile.reset();
        assertEquals(30L, totals.get("DEFEND_SITE")[0]);
        assertEquals(2L, totals.get("DEFEND_SITE")[1]);
        assertEquals(30L, totals.get("DEFEND_TRACK")[0]);
        assertEquals(1L, totals.get("DEFEND_TRACK")[1]);
    }

    private static void assertContext(TickInnerProfile.PathSearch search,
                                      long memberId, int squadId,
                                      String action, String routeReason) {
        assertEquals(memberId, search.memberId());
        assertEquals(squadId, search.squadId());
        assertEquals(action, search.action());
        assertEquals(routeReason, search.routeReason());
    }

    @Test
    void tickBoundaryClearsAndReleaseDeregistersWorkerScratch() {
        TickInnerProfile.releaseCurrentThread();
        int before = TickInnerProfile.trackedWorkerCount();
        TickInnerProfile worker = TickInnerProfile.current();
        worker.record(TickInnerProfile.Bucket.PATHFIND, 25L);

        TickInnerProfile.resetAllWorkers();

        assertEquals(0L, worker.nanosOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(0, worker.countOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(before + 1, TickInnerProfile.trackedWorkerCount());

        TickInnerProfile.releaseCurrentThread();

        assertEquals(before, TickInnerProfile.trackedWorkerCount());
    }

    @Test
    void closingFixtureSimulationDeregistersWorkerProfiles() throws Exception {
        TickInnerProfile.releaseCurrentThread();
        int before = TickInnerProfile.trackedWorkerCount();
        BattleSimulation sim = BattleFixtureTestSupport.loadDefaultFixture().build();
        while (sim.liveUnitCount() < 64) {
            int suffix = sim.liveUnitCount();
            sim.spawn(new EntitySpec("worker-profile-" + suffix,
                    Faction.MARINE, UnitType.MARINE_BLUE, 1, 1));
        }
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(TickInnerProfile.trackedWorkerCount() > before,
                "real battle ticks should create worker-local profiles");

        sim.close();

        assertEquals(before, TickInnerProfile.trackedWorkerCount());
    }
}
