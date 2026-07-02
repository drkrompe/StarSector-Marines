package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * <b>Squad posture: garrison hold at assigned post.</b> Custom-plan action
 * emitted by {@link com.dillon.starsectormarines.battle.infantry.GuardPost}
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
 * <p>The squad-level {@link com.dillon.starsectormarines.battle.decision.goap.Goal}
 * layer preempts when reality demands it — morale-broken →
 * {@code SurviveContact}, chokepoint geometry → {@code GarrisonAmbush}.
 *
 * <p>Mech members never reach this action — squads are pure mech or pure
 * infantry, and the mech dispatch uses {@code MECH_GOALS} which doesn't
 * include {@code GuardPost}.
 */
public final class HoldPost implements Action {

    public static final HoldPost INSTANCE = new HoldPost();

    /** Cell radius (≈ metres) around a member's home cell inside which it will reposition — ~0.5× infantry weapon range (~24), so a held guard can flank across a building's worth of frontage or step to a better cover stack, but won't walk out into the open street to chase. */
    public static final float HOLD_RADIUS = 12f;

    private HoldPost() {}

    @Override public String name() { return "HoldPost"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        boolean hasHome = sim.home().hasHome(member.entityId);
        int homeX = hasHome ? sim.home().homeCellX(member.entityId) : sim.world().cellX(member.entityId);
        int homeY = hasHome ? sim.home().homeCellY(member.entityId) : sim.world().cellY(member.entityId);

        // Squad retreating to a new post — every member walks home regardless
        // of alert level. updateSquadFallback drops the flag once everyone
        // arrives, at which point normal engagement resumes.
        if (squad.fallbackInProgress) {
            return returnToHome(member, sim, homeX, homeY);
        }

        Entity target = sim.targetOf(member);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
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

    private static ActionStatus executeWithTarget(Entity member, Entity target, BattleControl sim, int homeX, int homeY) {
        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(member.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));

        if (inRange && visible) {
            sim.combat().setFireIntent(member.entityId, Entity.idOf(target), FireStance.STANCED, false);
            hold(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                member, target, homeX, homeY, HOLD_RADIUS);
        if (firingPos == null) {
            // Current target unreachable from any cell within the hold ring.
            // Switch to any engageable enemy that fits and re-pick.
            Entity alt = sim.getTacticalScoring().findEngageableEnemyWithin(
                    member, homeX, homeY, HOLD_RADIUS);
            if (alt != null) {
                sim.world().setTargetId(member.entityId, Entity.idOf(alt));
                target = alt;
                firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                        member, target, homeX, homeY, HOLD_RADIUS);
            }
        }
        if (firingPos == null || (firingPos[0] == sim.world().cellX(member.entityId) && firingPos[1] == sim.world().cellY(member.entityId))) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, sim, firingPos[0], firingPos[1]);
        return ActionStatus.RUNNING;
    }

    private static ActionStatus investigateClamped(Entity member, BattleControl sim,
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

    private static ActionStatus returnToHome(Entity member, BattleControl sim, int homeX, int homeY) {
        if (sim.world().cellX(member.entityId) == homeX && sim.world().cellY(member.entityId) == homeY) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, sim, homeX, homeY);
        return ActionStatus.RUNNING;
    }

    private static void moveToward(Entity member, BattleControl sim, int tx, int ty) {
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
        } else {
            sim.world().setMoveProgress(member.entityId, 0f);
            sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
        }
    }

    private static void hold(Entity member, BattleControl sim) {
        sim.clearPath(member);
        sim.world().setMoveProgress(member.entityId, 0f);
        sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
    }
}
