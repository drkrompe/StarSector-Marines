package com.dillon.starsectormarines.battle.profile;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureTestSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

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
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(TickInnerProfile.trackedWorkerCount() > before,
                "real battle ticks should create worker-local profiles");

        sim.close();

        assertEquals(before, TickInnerProfile.trackedWorkerCount());
    }
}
