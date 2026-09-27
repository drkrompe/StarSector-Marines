package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.mech.MechRouteIntent;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;

/**
 * Shared mechanics for the dwell-gated, squad-scoped waypoint patrol used by
 * {@link PatrolRoute} (district node route), {@link GarrisonPatrol} (compound
 * room round-robin), and {@link GuardPostPatrol} (turret-emplacement box). The
 * three differ on only two axes — how the next waypoint is chosen and when the
 * current one is spent (the {@link WaypointSource}), and whether they fire
 * opportunistically while patrolling. Everything else (leader-gated dwell,
 * double-checked waypoint write under {@code squad.lock}, arrival test,
 * path/hold motion) is identical and lives here.
 *
 * <p>Stateless — all per-squad patrol state lives on the {@link Squad}
 * ({@code patrolWaypointX/Y}, {@code patrolDwellTimer}), so concurrent
 * per-member execution stays safe: the dwell decrement is leader-gated and the
 * waypoint write is lock-guarded, matching the GOAP action concurrency contract.
 */
public final class PatrolMotion {
    public static final String BOUND_GUARD_PATROL_PROPERTY = "battle.pathfinding.boundGuardPatrol";
    public static final String BOUND_DISTRICT_PATROL_PROPERTY = "battle.pathfinding.boundDistrictPatrol";

    /** Separate controls and attribution over the same optional-travel mechanism. */
    public enum OptionalRoutePolicy {
        NONE(null, null, null, null, null),
        GUARD(BOUND_GUARD_PATROL_PROPERTY, Bucket.GUARD_PATROL_SEARCH, Bucket.GUARD_PATROL_EXPANDED,
                Bucket.GUARD_PATROL_REFUSAL, Bucket.GUARD_PATROL_BACKOFF),
        DISTRICT(BOUND_DISTRICT_PATROL_PROPERTY, Bucket.DISTRICT_PATROL_SEARCH, Bucket.DISTRICT_PATROL_EXPANDED,
                Bucket.DISTRICT_PATROL_REFUSAL, Bucket.DISTRICT_PATROL_BACKOFF);

        private final String property;
        final Bucket search, expanded, refusal, backoff;

        OptionalRoutePolicy(String property, Bucket search, Bucket expanded, Bucket refusal, Bucket backoff) {
            this.property = property;
            this.search = search;
            this.expanded = expanded;
            this.refusal = refusal;
            this.backoff = backoff;
        }

        boolean enabled() {
            return this != NONE && Boolean.parseBoolean(System.getProperty(property, "true"));
        }
    }

    /** Sim-seconds a squad rests at a waypoint before picking a new one. Long enough that the foot-traffic reads as patrol-pausing-to-look-around, not march-step. */
    public static final float DWELL_SECONDS = 4.0f;
    /** Cell-radius around the waypoint inside which the squad's centroid counts as "arrived" and the dwell starts. Bigger than 1 so the squad doesn't stutter at the exact cell while members straggle in. */
    public static final int ARRIVAL_RADIUS = 3;

    private PatrolMotion() {}

    /**
     * Picks where a patrol heads next and decides when its current waypoint is
     * spent. Implementations supply the strategy (district node route / compound
     * room round-robin / emplacement box sample); the default {@link #needsNew}
     * fires on no-waypoint or arrival, and a strategy can widen it — e.g.
     * {@link GuardPostPatrol} also re-rolls a waypoint that has drifted outside
     * its box, since {@code patrolWaypointX/Y} is shared across postures and
     * isn't reset on a posture switch.
     */
    public interface WaypointSource {
        /** Quiet wandering may decline costly routes; required room tours retain ordinary routing. */
        default OptionalRoutePolicy optionalRoutePolicy() { return OptionalRoutePolicy.NONE; }

        /** Next waypoint {@code {x,y}}, or null to keep the current one and dwell. */
        int[] next(long member, Squad squad, BattleView sim);

        /** Whether the current squad waypoint must be replaced before moving to it. */
        default boolean needsNew(Squad squad) {
            return !hasValidWaypoint(squad) || squadHasArrived(squad);
        }
    }

    /**
     * One tick of the dwell-gated waypoint walk. Counts down the leader-gated
     * dwell, then — when the source says the waypoint is spent — re-rolls it
     * under the squad lock (double-checked so a sibling worker's fresh pick
     * isn't clobbered), else moves toward it. {@code fireWhilePatrolling} adds
     * opportunistic fire at visible in-range enemies during both the hold and
     * the move (garrison / guard postures; plain district patrols pass false).
     */
    public static ActionStatus advance(long member, Squad squad, BattleControl sim,
                                       WaypointSource source, boolean fireWhilePatrolling) {
        OptionalRoutePolicy policy = source.optionalRoutePolicy();
        boolean boundedQuiet = policy.enabled()
                && !sim.world().hasMechLoadout(member);
        if (squad.patrolDwellTimer > 0f) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            if (boundedQuiet && profile != null) profile.recordCount(policy.backoff, 1);
            if (ticksDwell(member, squad, sim)) squad.patrolDwellTimer -= BattleSimulation.TICK_DT;
            onHold(member, sim, fireWhilePatrolling);
            return ActionStatus.RUNNING;
        }
        if (source.needsNew(squad)) {
            synchronized (squad.lock) {
                // Re-check under the lock — a sibling worker may have already
                // advanced the waypoint and started a new dwell.
                if (squad.patrolDwellTimer > 0f || !source.needsNew(squad)) {
                    onHold(member, sim, fireWhilePatrolling);
                    return ActionStatus.RUNNING;
                }
                int[] wp = source.next(member, squad, sim);
                if (wp != null) {
                    squad.patrolWaypointX = wp[0];
                    squad.patrolWaypointY = wp[1];
                }
                squad.patrolDwellTimer = DWELL_SECONDS;
            }
            onHold(member, sim, fireWhilePatrolling);
            return ActionStatus.RUNNING;
        }
        if (boundedQuiet) {
            advanceOptionalQuiet(member, squad, sim, fireWhilePatrolling, policy);
            return ActionStatus.RUNNING;
        }
        boolean moving = onMove(member, sim, squad.patrolWaypointX, squad.patrolWaypointY, fireWhilePatrolling);
        if (!moving) {
            // The waypoint is unreachable from here — GridPathfinder found no
            // route (not an arrival: arrival is caught by needsNew above, so
            // reaching this park means there is genuinely no path). Invalidate
            // the waypoint so needsNew re-picks next tick. Without this the
            // round-robin never advances (it only rolls over on arrival) and the
            // squad parks on the unreachable cell forever — a garrison whose
            // next room sits across a wall the zone graph floods past but the
            // pathfinder honors ([[zone_graph_ignores_edges]]) would freeze in
            // place. Lock-guarded like the pick above so a sibling worker's
            // fresh waypoint isn't clobbered.
            synchronized (squad.lock) {
                squad.patrolWaypointX = -1;
                squad.patrolWaypointY = -1;
            }
        }
        return ActionStatus.RUNNING;
    }

    /** Search outside the squad monitor; only a still-current waypoint may receive the answer. */
    private static void advanceOptionalQuiet(long member, Squad squad, BattleControl sim, boolean fire,
                                             OptionalRoutePolicy policy) {
        if (fire) fireIfAble(member, sim);
        int tx = squad.patrolWaypointX;
        int ty = squad.patrolWaypointY;
        if (tx < 0 || ty < 0 || squad.patrolDwellTimer > 0f) {
            hold(member, sim);
            return;
        }
        int[] path = sim.world().path(member);
        if (sim.movement().pathTargetsCell(member, tx, ty)
                && sim.world().pathIdx(member) < Paths.cellCount(path)) {
            sim.advanceMovement(member);
            return;
        }
        // A throttled callback is waiting, not a failed waypoint. In particular
        // it must not keep walking an old combat/mission path while waiting.
        boolean mayRepath = sim.movement().mayRepath(member);
        hold(member, sim);
        if (!mayRepath) return;
        int[] found = QuietPatrolRoute.find(sim.getGrid(), sim.world().cellX(member),
                sim.world().cellY(member), tx, ty, GridPathfinder.USE_CARDINAL_NAVIGATION, policy);
        if (Paths.isEmpty(found)) {
            QuietPatrolRoute.refuse(squad, tx, ty, policy);
            return;
        }
        synchronized (squad.lock) {
            if (squad.patrolWaypointX == tx && squad.patrolWaypointY == ty
                    && squad.patrolDwellTimer <= 0f) sim.setPath(member, found);
        }
        // Start following on the next tick. A sibling may refuse this waypoint
        // after this publication; the next callback checks dwell before moving.
    }

    /** Exactly one available member writes the dwell, including when the real leader is controlled. */
    private static boolean ticksDwell(long member, Squad squad, BattleView sim) {
        long writer = squad.autonomousLeader(sim);
        if (writer != 0L) return member == writer;
        // Preserve the leaderless recovery path for a squad whose roster membership
        // has not been published yet. A controlled-only squad has no AI writer.
        return squad.controlledMemberId() == 0L && squad.participatesInPlan(member);
    }

    public static boolean hasValidWaypoint(Squad squad) {
        return squad.patrolWaypointX >= 0 && squad.patrolWaypointY >= 0;
    }

    public static boolean squadHasArrived(Squad squad) {
        if (squad.aliveMembers == 0) return true;
        float dx = squad.centroidX - (squad.patrolWaypointX + 0.5f);
        float dy = squad.centroidY - (squad.patrolWaypointY + 0.5f);
        return Math.sqrt(dx * dx + dy * dy) <= ARRIVAL_RADIUS;
    }

    private static void onHold(long member, BattleControl sim, boolean fire) {
        if (fire) fireIfAble(member, sim);
        hold(member, sim);
    }

    private static boolean onMove(long member, BattleControl sim, int tx, int ty, boolean fire) {
        if (fire) fireIfAble(member, sim);
        return moveToward(member, sim, tx, ty);
    }

    /**
     * Path one tick toward {@code (tx,ty)}. Returns {@code true} while the member
     * has a path to follow (moving, or sitting on the target with a trivial
     * one-cell path), {@code false} when the pathfinder returns no route from the
     * current cell — i.e. the target is unreachable and the member parks in
     * place. Callers driving a re-rollable waypoint use the {@code false} return
     * to pick a different target instead of parking forever.
     */
    public static boolean moveToward(long member, BattleControl sim, int tx, int ty) {
        return moveToward(member, sim, tx, ty, false);
    }

    /**
     * As {@link #moveToward(long, BattleControl, int, int)}, but with
     * {@code boundDetour} refusing a route out of proportion to the straight
     * line it stands in for — see {@link ApproachBound}.
     *
     * <p>Pass {@code true} only for a destination chosen as an improvement,
     * which today means a firing position inside a post's own ring. A member
     * returning home or walking to a heard noise passes {@code false}: the
     * long way round is the right way when it is the only way.
     *
     * <p>The refusal clears the path rather than leaving an empty one behind.
     * {@code setPath} stamps the repath throttle only on a non-empty
     * assignment, so an empty path never throttles and the same search runs
     * again next tick — and a search toward an unreachable cell exhausts the
     * whole reachable component before failing. Parking in place is what the
     * caller wanted; paying a full-component search per tick to do it is not.
     */
    public static boolean moveToward(long member, BattleControl sim, int tx, int ty,
                                     boolean boundDetour) {
        if (sim.world().hasMechLoadout(member)) {
            return MechRouteIntent.forMember(member, PatrolMotion.class,
                    MechRouteIntent.cellKey(tx, ty), sim).moveToward(member, tx, ty, sim, boundDetour)
                    != PathRequestStatus.FAILED;
        }
        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        if (sim.movement().mayRepath(member) && pathIdx >= Paths.cellCount(path)) {
            int fromX = sim.world().cellX(member);
            int fromY = sim.world().cellY(member);
            int[] found = GridPathfinder.findPath(sim.getGrid(),
                    fromX, fromY, tx, ty, sim.getOccupancyMap());
            if (!ApproachBound.worthWalkingTo(fromX, fromY, tx, ty, found, boundDetour)) {
                // Only this branch actually rejected a firing-position route;
                // the false return below can also mean a throttled empty path.
                if (boundDetour) sim.getTacticalScoring().forgetFiringPosition(member);
                hold(member, sim);
                return false;
            }
            sim.setPath(member, found);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (pathIdx < Paths.cellCount(path)) {
            sim.advanceMovement(member);
            return true;
        }
        return false;
    }

    /** Stop and clear any path — park the member on its current cell. */
    public static void hold(long member, BattleControl sim) {
        sim.clearPath(member);
    }

    /** Authors a MOVING-stance shot of opportunity without changing pursuit. */
    public static void fireIfAble(long member, BattleControl sim) {
        fireIfAble(member, sim, FireStance.MOVING);
    }

    /**
     * Authors a fire intent at the nearest enemy the member can shoot now,
     * retaining the currently registered reflex threat through near-equal
     * alternatives. Opportunity fire is an execution-only trigger: it never
     * replaces the pursuit target that governs movement.
     */
    public static void fireIfAble(long member, BattleControl sim, FireStance stance) {
        long target = sim.getTacticalScoring().closestEnemyInAttackRange(
                member, sim.combat().reflexTargetId(member),
                TacticalScoring.OPPORTUNITY_RETARGET_DISTANCE_MARGIN);
        if (target != 0L) {
            sim.combat().setFireIntent(member, target, stance, false);
        }
    }
}
