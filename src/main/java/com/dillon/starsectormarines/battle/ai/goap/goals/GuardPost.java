package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.HoldPost;

import java.util.List;

/**
 * Default goal for GARRISON-routed defender squads. Always relevant when the
 * squad carries the {@link Squad#holdsFireUntilKillZone} flag (set at squad
 * mint by {@code BattleSetup} for nodes whose default role is GARRISON).
 *
 * <p>{@link Priority#MISSION} — outranks {@link EliminateEnemiesGoal} so the
 * squad doesn't abandon its post to chase a visible enemy. Loses to
 * {@link GarrisonAmbush} (same bucket, registered earlier so it wins the
 * tie) for chokepoint-shaped zones, and to {@link SurviveContact} (higher
 * SURVIVAL priority) when morale breaks.
 *
 * <p>Custom-plan: a single-step plan of {@link HoldPost}. The action runs
 * perpetually (always returns RUNNING); the squad-level periodic replan is
 * what swaps goals when conditions change (target gained → still GuardPost,
 * same action but engagement branch; morale broken → SurviveContact;
 * chokepoint geometry exposed → GarrisonAmbush).
 */
public final class GuardPost implements Goal {

    public static final GuardPost INSTANCE = new GuardPost();

    private GuardPost() {}

    @Override public String name() { return "GuardPost"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        return squad.holdsFireUntilKillZone ? 1.0f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(HoldPost.INSTANCE)));
    }
}
