package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.DroneSwarmAction;

import java.util.List;

/**
 * The one goal a drone squad runs: keep the home hub alive by screening it,
 * engaging anything that approaches, and pursuing intruders out to the leash.
 * MISSION priority — same bucket as garrison {@code GuardPost} and patrol
 * {@code RoutinePatrol} — so future SURVIVAL-tier goals (e.g. a "drone is at
 * low HP" retreat-to-hub action) could preempt naturally without rewiring
 * dispatch.
 *
 * <p>Custom-plan: a single-step plan of {@link DroneSwarmAction}. The action
 * runs perpetually (always returns RUNNING) and handles all three drone
 * postures (engage / pursue / patrol) internally; replan re-evaluates goal
 * selection on the standard 2-second cadence.
 *
 * <p>Relevance: 1 when the hub is still alive, 0 otherwise. A destroyed hub
 * has no further squad work to do — the cascade-kill in
 * {@code BattleSimulation.demolishDeadDroneHubs} zeros the drones' HP, the
 * next replan sees {@code aliveMembers == 0} and clears the plan.
 */
public final class DefendHubGoal implements Goal {

    public static final DefendHubGoal INSTANCE = new DefendHubGoal();

    private DefendHubGoal() {}

    @Override public String name() { return "DefendHub"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        if (squad.droneHub == null) return 0f;
        if (!squad.droneHub.isAlive()) return 0f;
        return 1f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(DroneSwarmAction.INSTANCE)));
    }
}
