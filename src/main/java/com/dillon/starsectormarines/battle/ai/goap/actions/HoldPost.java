package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Squad posture: garrison hold at assigned post.</b> Custom-plan action
 * emitted by {@link com.dillon.starsectormarines.battle.ai.goap.goals.GuardPost}
 * for defender squads pegged to a tactical node. Single per-member behavior
 * that branches on the squad's alert level and the member's current target:
 *
 * <ul>
 *   <li><b>Has target, in range + LOS</b> — fire from current cell. The
 *       cover stack at the post is the position they're paid to occupy;
 *       they don't sidestep between shots.</li>
 *   <li><b>Has target, out of range or no LOS</b> — reposition to a firing
 *       cell within {@link #HOLD_RADIUS} of the member's home cell. If no
 *       such cell exists, switch to any engageable enemy that <em>does</em>
 *       have one. If neither produces a move, hold.</li>
 *   <li><b>No target, SUSPICIOUS with last-seen cell</b> — lean toward the
 *       noise, clamped to within {@link #HOLD_RADIUS} of home. Keeps the
 *       garrison from walking off the post during investigation.</li>
 *   <li><b>No target, UNAWARE (or no last-seen cell)</b> — return to home
 *       cell and hold.</li>
 * </ul>
 *
 * <p>The squad-level {@link com.dillon.starsectormarines.battle.ai.goap.Goal}
 * layer preempts when reality demands it — morale-broken →
 * {@code SurviveContact}, chokepoint geometry → {@code GarrisonAmbush}.
 *
 * <p>Mech members never reach this action — squads are pure mech or pure
 * infantry, and the mech dispatch uses {@code MECH_GOALS} which doesn't
 * include {@code GuardPost}.
 */
public final class HoldPost implements Action {

    public static final HoldPost INSTANCE = new HoldPost();

    /** Cell radius around a member's home cell inside which it will reposition. Tight — about one block of slack so the unit can flank to the corner of their post or step to a better cover stack, but won't walk into the open street to chase. */
    public static final float HOLD_RADIUS = 6f;

    private HoldPost() {}

    @Override public String name() { return "HoldPost"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        int homeX = member.homeCellX >= 0 ? member.homeCellX : member.cellX;
        int homeY = member.homeCellY >= 0 ? member.homeCellY : member.cellY;

        // Squad retreating to a new post — every member walks home regardless
        // of alert level. updateSquadFallback drops the flag once everyone
        // arrives, at which point normal engagement resumes.
        if (squad.fallbackInProgress) {
            return returnToHome(member, sim, homeX, homeY);
        }

        Unit target = sim.targetOf(member);
        if (target == null
                || !TacticalScoring.shouldKeepPursuing(member, target, sim)) {
            target = TacticalScoring.findBestTarget(member, sim);
            member.setTarget(target);
        }

        if (target != null) {
            return executeWithTarget(member, target, sim, homeX, homeY);
        }

        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return investigateClamped(member, sim, squad, homeX, homeY);
        }

        return returnToHome(member, sim, homeX, homeY);
    }

    private static ActionStatus executeWithTarget(Unit member, Unit target, BattleSimulation sim, int homeX, int homeY) {
        if (member.cooldownTimer > 0f) member.cooldownTimer -= BattleSimulation.TICK_DT;

        float dist = TacticalScoring.cellDistance(member.cellX, member.cellY,
                target.cellX, target.cellY);
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                target.cellX, target.cellY);

        if (inRange && visible) {
            if (member.cooldownTimer <= 0f) {
                sim.fireShot(member, target);
                member.cooldownTimer = member.attackCooldown;
                member.beginBurst(target);
            }
            hold(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] firingPos = TacticalScoring.findFiringPositionWithin(
                member, target, sim, homeX, homeY, HOLD_RADIUS);
        if (firingPos == null) {
            // Current target unreachable from any cell within the hold ring.
            // Switch to any engageable enemy that fits and re-pick.
            Unit alt = TacticalScoring.findEngageableEnemyWithin(
                    member, sim, homeX, homeY, HOLD_RADIUS);
            if (alt != null) {
                member.setTarget(alt);
                target = alt;
                firingPos = TacticalScoring.findFiringPositionWithin(
                        member, target, sim, homeX, homeY, HOLD_RADIUS);
            }
        }
        if (firingPos == null || (firingPos[0] == member.cellX && firingPos[1] == member.cellY)) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, sim, firingPos[0], firingPos[1]);
        return ActionStatus.RUNNING;
    }

    private static ActionStatus investigateClamped(Unit member, BattleSimulation sim,
                                                    Squad squad, int homeX, int homeY) {
        int tx = squad.lastSeenEnemyX;
        int ty = squad.lastSeenEnemyY;
        float distHomeToLast = TacticalScoring.cellDistance(homeX, homeY, tx, ty);
        if (distHomeToLast > HOLD_RADIUS) {
            float scale = HOLD_RADIUS / distHomeToLast;
            tx = homeX + Math.round((tx - homeX) * scale);
            ty = homeY + Math.round((ty - homeY) * scale);
        }
        moveToward(member, sim, tx, ty);
        return ActionStatus.RUNNING;
    }

    private static ActionStatus returnToHome(Unit member, BattleSimulation sim, int homeX, int homeY) {
        if (member.cellX == homeX && member.cellY == homeY) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, sim, homeX, homeY);
        return ActionStatus.RUNNING;
    }

    private static void moveToward(Unit member, BattleSimulation sim, int tx, int ty) {
        if (member.moveProgress == 0f && member.pathIdx >= member.pathCellCount()) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, tx, ty, sim.getOccupancyMap()));
        }
        if (member.pathIdx < member.pathCellCount()) {
            sim.advanceMovement(member);
        } else {
            member.moveProgress = 0f;
            member.renderX = member.cellX;
            member.renderY = member.cellY;
        }
    }

    private static void hold(Unit member, BattleSimulation sim) {
        sim.clearPath(member);
        member.moveProgress = 0f;
        member.renderX = member.cellX;
        member.renderY = member.cellY;
    }
}
