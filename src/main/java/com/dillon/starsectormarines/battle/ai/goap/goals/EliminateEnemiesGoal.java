package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;

/**
 * Stage 1's only goal — always relevant, satisfied by inflicting damage on
 * an enemy. Combined with the three parity actions, this is sufficient to
 * keep an infantry squad engaged through every Stage 1 scenario.
 *
 * <p>The planner backward-chains: to satisfy {@code ENEMY_DAMAGED}, run
 * {@code EngageVisible}; needs {@code HAS_LOS_TO_TARGET ∧ IN_RANGE_OF_TARGET};
 * if those don't hold in the snapshot, prepend {@code MoveToFiringPosition};
 * if the squad is scattered, prepend {@code MaintainCohesion} as well.
 *
 * <p>Relevance is high when targets exist and low (but non-zero) otherwise.
 * Non-zero keeps the goal in contention so the planner can produce a no-op
 * (empty) plan when the world is already in the goal state — distinct from
 * "no goal at all," which is the Stage 2+ case when other goals exist.
 */
public final class EliminateEnemiesGoal implements Goal {

    public static final EliminateEnemiesGoal INSTANCE = new EliminateEnemiesGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private EliminateEnemiesGoal() {}

    @Override public String name() { return "EliminateEnemies"; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        return state.get(Predicate.HAS_TARGET) ? 1.0f : 0.1f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }
}
