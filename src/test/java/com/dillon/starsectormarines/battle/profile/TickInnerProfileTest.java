package com.dillon.starsectormarines.battle.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void tickBoundaryClearsAutoCreatedWorkerScratch() {
        TickInnerProfile worker = TickInnerProfile.current();
        worker.record(TickInnerProfile.Bucket.PATHFIND, 25L);
        TickInnerProfile.setCurrent(null);

        TickInnerProfile.resetAllWorkers();

        assertEquals(0L, worker.nanosOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(0, worker.countOf(TickInnerProfile.Bucket.PATHFIND));
    }
}
