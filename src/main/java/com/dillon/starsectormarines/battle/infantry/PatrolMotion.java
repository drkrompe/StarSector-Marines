package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

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
        int[] next(Unit member, Squad squad, BattleView sim);

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
    public static ActionStatus advance(Unit member, Squad squad, BattleControl sim,
                                       WaypointSource source, boolean fireWhilePatrolling) {
        if (squad.patrolDwellTimer > 0f) {
            if (member == squad.leader) squad.patrolDwellTimer -= BattleSimulation.TICK_DT;
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
        onMove(member, sim, squad.patrolWaypointX, squad.patrolWaypointY, fireWhilePatrolling);
        return ActionStatus.RUNNING;
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

    private static void onHold(Unit member, BattleControl sim, boolean fire) {
        if (fire) fireIfAble(member, sim);
        hold(member, sim);
    }

    private static void onMove(Unit member, BattleControl sim, int tx, int ty, boolean fire) {
        if (fire) fireIfAble(member, sim);
        moveToward(member, sim, tx, ty);
    }

    /** Path one tick toward {@code (tx,ty)}; on arrival (or no path) park in place. */
    public static void moveToward(Unit member, BattleControl sim, int tx, int ty) {
        if (member.getMoveProgress() == 0f && member.pathIdx >= member.pathCellCount()) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), tx, ty, sim.getOccupancyMap()));
        }
        if (member.pathIdx < member.pathCellCount()) {
            sim.advanceMovement(member);
        } else {
            member.setMoveProgress(0f);
            member.setRenderPos(member.getCellX(), member.getCellY());
        }
    }

    /** Stop and clear any path — park the member on its current cell. */
    public static void hold(Unit member, BattleControl sim) {
        sim.clearPath(member);
        member.setMoveProgress(0f);
        member.setRenderPos(member.getCellX(), member.getCellY());
    }

    /** Opportunistic moving-stance shot at a visible in-range enemy; no movement. */
    public static void fireIfAble(Unit member, BattleControl sim) {
        if (member.getCooldownTimer() > 0f) {
            member.setCooldownTimer(member.getCooldownTimer() - BattleSimulation.TICK_DT);
        }
        Unit target = sim.targetOf(member);
        if (target == null || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        if (dist <= member.getAttackRange() && visible && member.getCooldownTimer() <= 0f) {
            sim.fireShot(member, target, FireStance.MOVING);
            member.setCooldownTimer(member.attackCooldown);
            member.beginBurst(target);
        }
    }
}
