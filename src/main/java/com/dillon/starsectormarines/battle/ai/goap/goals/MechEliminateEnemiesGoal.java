package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.EngageAtCurrentBand;

import java.util.List;

/**
 * Ambient mech goal — always relevant, satisfied by inflicting damage.
 * Sibling of {@link EliminateEnemiesGoal} for mech squads; the relevance
 * scoring is identical (high with a target, 0.1 floor without). What
 * differs is the plan: this goal custom-plans a single-step
 * {@link EngageAtCurrentBand} action rather than going through the
 * backward-chaining planner, because Stage 1 mech behavior is a single
 * "do parity engagement" tick and the planner search produces nothing more
 * useful than that.
 *
 * <p>Role-anchored goals (LR Support's {@code OverwatchKillZone}, Armored
 * Support's {@code BackstopAssignedSquad}) land in subsequent slices at
 * {@link Goal.Priority#MISSION} so they outrank this ambient
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
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        return state.get(Predicate.HAS_TARGET) ? 1.0f : 0.1f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(EngageAtCurrentBand.INSTANCE)));
    }
}
