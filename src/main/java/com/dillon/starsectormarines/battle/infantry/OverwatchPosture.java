package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * <b>Squad posture: silent overwatch.</b> Story A — a garrison squad with LOS
 * and range on an enemy but with its kill-zone gate still closed (the enemy
 * hasn't been in close LOS long enough). The squad holds position, holds fire,
 * and waits for {@link Predicate#ENEMY_IN_KILL_ZONE} to flip true so the
 * planner re-picks {@link EngagePosture} on the next replan.
 *
 * <p>Same {@code ENEMY_DAMAGED=true} effect as {@link EngagePosture} so the
 * planner sees both as candidates for {@link com.dillon.starsectormarines.battle.infantry.EliminateEnemiesGoal}.
 * Higher cost ({@link #COST} vs Engage's 1.0) makes Engage the preferred pick
 * whenever its precondition set is satisfied — Overwatch is the fallback when
 * Engage's {@link Predicate#ENEMY_IN_KILL_ZONE} precondition is false.
 *
 * <p>Per-member: holds the current cell (clears any leftover path, pins
 * {@code moveProgress/renderX/renderY}), does not fire. Same on-post discipline
 * as {@link HoldPortalCordon}'s holder branch without the opportunistic-fire
 * call. The "no fire" is the ambush's whole point — the first shot lands when
 * the planner switches to Engage.
 */
public final class OverwatchPosture implements Action {

    public static final OverwatchPosture INSTANCE = new OverwatchPosture();

    /** Higher than {@link EngagePosture}'s 1.0 so the planner picks Engage when its preconditions hold; Overwatch only wins when the kill-zone gate is closed. */
    private static final float COST = 5.0f;

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true)
            .with(Predicate.ENEMY_IN_KILL_ZONE, false);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private OverwatchPosture() {}

    @Override public String name() { return "Overwatch"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return COST; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        // Drop any in-flight path — the squad is on overwatch, not moving.
        if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
        sim.world().setMoveProgress(member.entityId, 0f);
        member.setRenderPos(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
        // Deliberately do NOT fire and do NOT touch cooldownTimer — the
        // ambush's first shot is owned by EngagePosture on the next replan
        // after the kill-zone gate flips.
        return ActionStatus.RUNNING;
    }
}
