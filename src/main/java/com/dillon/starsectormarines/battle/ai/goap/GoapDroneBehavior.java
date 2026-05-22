package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.actions.DroneSwarmAction;
import com.dillon.starsectormarines.battle.ai.goap.goals.DefendHubGoal;
import com.dillon.starsectormarines.battle.ai.goap.scoring.RoleAssigner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-unit GOAP dispatch for drone-hub squads. Parallel to
 * {@link GoapInfantryBehavior} and {@link GoapMechBehavior} — drones have
 * their own action library because the kinematic model (AirBody +
 * {@code body.tickToward}) and tactical primitives (encircle bearings,
 * sector patrols) don't share with the cell-grid-pathfinder-based
 * infantry/mech actions.
 *
 * <p>Stage 1: one goal ({@link DefendHubGoal}) and one action
 * ({@link DroneSwarmAction}). The single action handles all three drone
 * postures (engage / pursue / patrol) internally, with slot-aware
 * positioning so the swarm spreads rather than stacking. Future goals —
 * a low-HP retreat-to-hub for damaged drones, a Commander-driven
 * objective-push for offensive deployment — slot in here at SURVIVAL /
 * MISSION priorities respectively.
 */
public final class GoapDroneBehavior implements UnitBehavior {

    public static final GoapDroneBehavior INSTANCE = new GoapDroneBehavior();

    public static final List<Goal> DRONE_GOALS = List.of(
            DefendHubGoal.INSTANCE
    );

    public static final List<Action> DRONE_ACTIONS = List.of(
            DroneSwarmAction.INSTANCE
    );

    /** Same replan cadence as the infantry path — keeps the per-tick budget consistent across squad types and matches the period the action's slot-spread bearings stabilize over. */
    public static final float REPLAN_PERIOD = 2.0f;
    /** Tight planner budget: the drone library is single-goal, single-action and never benefits from backward-chaining. Kept non-zero so a future second action can still be wired in. */
    public static final int PLAN_NODE_LIMIT = 32;

    private GoapDroneBehavior() {}

    @Override
    public void update(Unit unit, BattleSimulation sim) {
        Squad squad = unit.squadId == Unit.NO_SQUAD ? null : sim.getSquad(unit.squadId);
        if (squad == null) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return;

        SquadPlan.Step step = plan.currentStep();
        // Null possible under parallel dispatch: a sibling worker advanced past
        // the end between the isComplete() check and here. Skip this tick.
        if (step == null || step.slotOf(unit) == null) return;

        ActionStatus status = step.action.execute(unit, squad, sim);
        switch (status) {
            // SUCCESS / FAILURE mutate squad-shared plan state — see
            // GoapInfantryBehavior.update for the locking rationale (avoid
            // double-advance and ensure visibility of plan=null clear).
            case SUCCESS -> {
                synchronized (squad.lock) {
                    if (plan == squad.currentPlan && !plan.isComplete()
                            && plan.currentStep() == step) {
                        plan.advance();
                    }
                }
            }
            case FAILURE -> {
                synchronized (squad.lock) {
                    if (plan == squad.currentPlan) {
                        squad.currentPlan = null;
                    }
                }
            }
            case RUNNING -> { /* keep ticking the same step */ }
        }
    }

    /**
     * Squad-level replan pass. Mirrors {@link GoapInfantryBehavior#replanIfNeeded}
     * with the drone goal/action libraries. Skips {@code WorldStateBuilder} —
     * the current drone goal set doesn't consult predicates (custom-plan with
     * relevance gated on hub-alive only), so the empty state is sufficient
     * and we avoid the predicate-evaluator cost.
     */
    public static void replanIfNeeded(Squad squad, BattleSimulation sim) {
        if (squad.aliveMembers == 0) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.aliveMembersAtLastPlan = 0;
            return;
        }

        boolean memberCountChanged = squad.aliveMembers != squad.aliveMembersAtLastPlan;
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= REPLAN_PERIOD
                           || memberCountChanged;

        if (!needsReplan) {
            squad.timeSinceReplan += BattleSimulation.TICK_DT;
            return;
        }

        WorldState current = WorldState.EMPTY;
        Goal goal = Goal.pickMostRelevant(DRONE_GOALS, current, squad, sim);
        if (goal == null) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = 0f;
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            return;
        }

        SquadPlan plan = goal.customPlan(squad, sim);
        if (plan == null) {
            plan = Planner.plan(
                    current,
                    goal.desiredState(squad, sim),
                    DRONE_ACTIONS,
                    squad,
                    sim,
                    PLAN_NODE_LIMIT);
        }

        if (plan != null && !plan.isComplete()) {
            List<Unit> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (Unit u : sim.getUnits()) {
                if (!u.isAlive() || u.squadId != squad.id) continue;
                aliveMembers.add(u);
            }
            for (SquadPlan.Step step : plan.steps()) {
                List<RoleAssigner.Slot<Unit>> slots = step.action.roles(squad, sim);
                Map<String, List<Unit>> assignment = RoleAssigner.assign(aliveMembers, slots);
                step.assignments.putAll(assignment);
            }
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = 0f;
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
    }
}
