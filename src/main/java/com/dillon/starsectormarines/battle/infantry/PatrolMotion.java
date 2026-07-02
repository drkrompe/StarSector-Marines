package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

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
        /** Next waypoint {@code {x,y}}, or null to keep the current one and dwell. */
        int[] next(Entity member, Squad squad, BattleView sim);

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
    public static ActionStatus advance(Entity member, Squad squad, BattleControl sim,
                                       WaypointSource source, boolean fireWhilePatrolling) {
        if (squad.patrolDwellTimer > 0f) {
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

    /**
     * Whether THIS member counts down the shared dwell timer this tick. Normally
     * only the leader does, so the timer decrements once per tick rather than
     * once per member. But if the leader is dead or unset — {@code leaderId} is
     * the {@code 0L} wipe sentinel, or resolves to no live unit (a death path
     * that didn't promote) — no member would match and the dwell would never
     * expire, parking the whole squad in {@link #onHold} forever (the SQ-96
     * garrison stall). Fall back to letting every member tick it: the timer
     * drains faster while leaderless (self-corrects on the next promotion) but
     * the patrol never freezes.
     */
    private static boolean ticksDwell(Entity member, Squad squad, BattleView sim) {
        if (member.entityId == squad.leaderId) return true;
        return squad.leaderId == 0L || sim.resolveUnit(squad.leaderId) == null;
    }

    public static boolean hasValidWaypoint(Squad squad) {
        return squad.patrolWaypointX >= 0 && squad.patrolWaypointY >= 0;
    }

    public static boolean squadHasArrived(Squad squad) {
        if (squad.aliveMembers == 0) return true;
        float dx = squad.centroidX - squad.patrolWaypointX;
        float dy = squad.centroidY - squad.patrolWaypointY;
        return Math.sqrt(dx * dx + dy * dy) <= ARRIVAL_RADIUS;
    }

    private static void onHold(Entity member, BattleControl sim, boolean fire) {
        if (fire) fireIfAble(member, sim);
        hold(member, sim);
    }

    private static boolean onMove(Entity member, BattleControl sim, int tx, int ty, boolean fire) {
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
    public static boolean moveToward(Entity member, BattleControl sim, int tx, int ty) {
        int[] path = sim.world().path(member.entityId);
        int pathIdx = sim.world().pathIdx(member.entityId);
        if (sim.world().moveProgress(member.entityId) == 0f && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), tx, ty, sim.getOccupancyMap()));
            path = sim.world().path(member.entityId);
            pathIdx = sim.world().pathIdx(member.entityId);
        }
        if (pathIdx < Paths.cellCount(path)) {
            sim.advanceMovement(member);
            return true;
        }
        sim.world().setMoveProgress(member.entityId, 0f);
        sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
        return false;
    }

    /** Stop and clear any path — park the member on its current cell. */
    public static void hold(Entity member, BattleControl sim) {
        sim.clearPath(member);
        sim.world().setMoveProgress(member.entityId, 0f);
        sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
    }

    /**
     * Authors a MOVING-stance fire intent at a visible in-range enemy; no
     * movement. {@code battle.combat.FiringSystem} owns the cooldown gate and
     * executes the shot in the serial FIRING phase.
     */
    public static void fireIfAble(Entity member, BattleControl sim) {
        Entity target = sim.targetOf(member);
        if (target == null || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (dist <= sim.world().attackRange(member.entityId) && visible) {
            sim.combat().setFireIntent(member.entityId, Entity.idOf(target), FireStance.MOVING, false);
        }
    }
}
