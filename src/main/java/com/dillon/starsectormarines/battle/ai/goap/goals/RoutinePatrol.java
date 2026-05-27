package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.PatrolRoute;

import java.util.List;

/**
 * Default goal for PATROL-routed defender squads. Wins for non-garrison
 * defenders whose alert level hasn't escalated to ENGAGED — they walk the
 * patrol route while UNAWARE and converge on last-known-enemy cells while
 * SUSPICIOUS.
 *
 * <p>{@link Priority#MISSION} so it outranks {@link EliminateEnemiesGoal}
 * for non-engaged patrols (patrols shouldn't pursue targets they've never
 * actually seen). Once a squadmate gains LOS the squad bumps to ENGAGED,
 * this goal's relevance drops to zero, and {@code EliminateEnemiesGoal} at
 * ENGAGEMENT takes over.
 *
 * <p>Custom-plan: a single-step plan of {@link PatrolRoute}. The action
 * runs perpetually (always returns RUNNING); replan re-evaluates goal
 * selection on the 2-second cadence.
 *
 * <p>Faction gate + holdsFire inversion is what isolates this to patrols:
 * marines are ATTACKER faction (excluded), garrisons set
 * {@link Squad#holdsFireUntilKillZone} (excluded via {@link GuardPost}'s
 * higher tie-break priority within MISSION). Defenders without the
 * garrison flag are patrols — both tactical-node-anchored and legacy
 * cluster-spawned variants.
 */
public final class RoutinePatrol implements Goal {

    public static final RoutinePatrol INSTANCE = new RoutinePatrol();

    private RoutinePatrol() {}

    @Override public String name() { return "RoutinePatrol"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        if (squad.faction != Faction.DEFENDER) return 0f;
        if (squad.holdsFireUntilKillZone) return 0f;
        if (squad.alertLevel == SquadAlertLevel.ENGAGED) return 0f;
        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return 0f;
        }
        return 1f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(PatrolRoute.INSTANCE)));
    }
}
