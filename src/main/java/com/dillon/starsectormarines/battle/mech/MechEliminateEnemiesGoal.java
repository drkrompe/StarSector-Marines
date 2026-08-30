package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;

import java.util.List;

/**
 * Ambient mech goal — always relevant, satisfied by inflicting damage.
 * Sibling of {@link EliminateEnemiesGoal} for mech squads; the relevance
 * scoring is identical (high with a target, 0.1 floor without). What
 * differs is the plan: this goal custom-plans the single-step
 * {@link ExecuteMechDoctrine} dispatcher rather than going through the
 * backward-chaining planner. Every member of a mixed lance therefore uses its
 * own effective doctrine even when this ambient goal owns the squad plan.
 *
 * <p>Role-anchored goals (LR Support's {@code OverwatchKillZone}, Tank's
 * {@code BackstopAssignedSquad}) use {@link Goal.Priority#MISSION} so they
 * outrank this ambient
 * {@link Goal.Priority#ENGAGEMENT} default whenever their preconditions
 * hold.
 */
public final class MechEliminateEnemiesGoal implements Goal {

    public static final MechEliminateEnemiesGoal INSTANCE = new MechEliminateEnemiesGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private MechEliminateEnemiesGoal() {}

    @Override public String name() { return "MechEliminateEnemies"; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        return state.get(Predicate.HAS_TARGET) ? 1.0f : 0.1f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
    }
}
