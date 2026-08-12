package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * <b>Squad posture: hold ground under overwatch.</b> Story A — a garrison
 * squad with LOS and range on an enemy while its kill-zone gate is still
 * closed. The squad holds its exact position and takes legal shots of
 * opportunity; it never waits passively while an enemy fires at it. When
 * {@link Predicate#ENEMY_IN_KILL_ZONE} flips true the planner may re-pick
 * {@link EngagePosture}, which is free to maneuver rather than remaining
 * pinned to the defensive post.
 *
 * <p>Same {@code ENEMY_DAMAGED=true} effect as {@link EngagePosture} so the
 * planner sees both as candidates for {@link com.dillon.starsectormarines.battle.infantry.EliminateEnemiesGoal}.
 * Higher cost ({@link #COST} vs Engage's 1.0) makes Engage the preferred pick
 * whenever its precondition set is satisfied — Overwatch is the fallback when
 * Engage's {@link Predicate#ENEMY_IN_KILL_ZONE} precondition is false. The
 * distinction is positional discipline, not fire discipline: both postures
 * shoot, but Overwatch does not leave its ground.
 *
 * <p>Per-member: holds the current cell (clears any leftover path, pins
 * {@code moveProgress/renderX/renderY}). The shared infantry dispatcher then
 * fills an otherwise-empty fire intent against the closest legal target; with
 * movement pinned, that shot uses the stanced-fire profile.
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
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        // Drop any in-flight path — the squad is on overwatch, not moving.
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        sim.world().setMoveProgress(member, 0f);
        sim.world().setRenderPos(member, sim.world().cellX(member), sim.world().cellY(member));
        // Target selection remains centralized in the dispatcher's opportunity
        // fire pass. This action owns the positional intent: stay planted.
        return ActionStatus.RUNNING;
    }
}
