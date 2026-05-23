package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;

/**
 * <b>Squad posture: silent overwatch.</b> Story A — a garrison squad with LOS
 * and range on an enemy but with its kill-zone gate still closed (the enemy
 * hasn't been in close LOS long enough). The squad holds position, holds fire,
 * and waits for {@link Predicate#ENEMY_IN_KILL_ZONE} to flip true so the
 * planner re-picks {@link EngagePosture} on the next replan.
 *
 * <p>Same {@code ENEMY_DAMAGED=true} effect as {@link EngagePosture} so the
 * planner sees both as candidates for {@link com.dillon.starsectormarines.battle.ai.goap.goals.EliminateEnemiesGoal}.
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
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return COST; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Drop any in-flight path — the squad is on overwatch, not moving.
        if (!member.pathEmpty()) sim.clearPath(member);
        member.moveProgress = 0f;
        member.renderX = member.getCellX();
        member.renderY = member.getCellY();
        // Deliberately do NOT fire and do NOT touch cooldownTimer — the
        // ambush's first shot is owned by EngagePosture on the next replan
        // after the kill-zone gate flips.
        return ActionStatus.RUNNING;
    }
}
