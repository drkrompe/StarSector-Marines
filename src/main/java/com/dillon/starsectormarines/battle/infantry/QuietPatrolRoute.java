package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion.OptionalRoutePolicy;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.squad.Squad;

/** Optional quiet wandering, never a policy for home, contact investigation, or combat. */
final class QuietPatrolRoute {
    static final int MAX_EXPANDED_NODES = 1024;

    private QuietPatrolRoute() { }

    /** Complete geometric routes only. Occupancy remains a physical movement concern, not a search price. */
    static int[] find(NavigationGrid grid, int fromX, int fromY, int toX, int toY, boolean cardinal,
                      OptionalRoutePolicy policy) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile == null ? 0L : System.nanoTime();
        float straight = TacticalScoring.cellDistance(fromX, fromY, toX, toY);
        // ApproachBound counts path cells, including the origin; the search envelope counts edges.
        int maxSteps = Math.max(0, (int) Math.floor(ApproachBound.DETOUR_SLACK
                + ApproachBound.DETOUR_RATIO * straight) - 1);
        try {
            int[] path = GridPathfinder.findPathWithinStepEnvelope(grid, fromX, fromY,
                    toX, toY, cardinal, maxSteps, MAX_EXPANDED_NODES);
            return ApproachBound.worthWalkingTo(fromX, fromY, toX, toY, path, true)
                    ? path : GridPathfinder.EMPTY_PATH;
        } finally {
            if (profile != null) {
                profile.record(policy.search, System.nanoTime() - started);
                profile.recordCount(policy.expanded,
                        GridPathfinder.lastSearchExpandedNodes());
            }
        }
    }

    /**
     * Retire only the attempted waypoint, then use the squad's existing dwell as
     * backoff. That state survives action replans; budget refusal is not disconnection.
     */
    static boolean refuse(Squad squad, int attemptedX, int attemptedY, OptionalRoutePolicy policy) {
        synchronized (squad.lock) {
            if (squad.patrolWaypointX != attemptedX || squad.patrolWaypointY != attemptedY
                    || squad.patrolDwellTimer > 0f) return false;
            squad.patrolWaypointX = -1;
            squad.patrolWaypointY = -1;
            squad.patrolDwellTimer = PatrolMotion.DWELL_SECONDS;
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) profile.recordCount(policy.refusal, 1);
        return true;
    }
}
