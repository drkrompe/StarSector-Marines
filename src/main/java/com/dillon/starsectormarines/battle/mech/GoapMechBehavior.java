package com.dillon.starsectormarines.battle.mech;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.infantry.SweepAssignedSectorGoal;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.command.DefendAssignedTrackGoal;
import com.dillon.starsectormarines.battle.command.DefendAssignedSiteGoal;
import com.dillon.starsectormarines.battle.command.DefendAssignedAreaGoal;
import com.dillon.starsectormarines.battle.command.AdvanceAssignedTrackGoal;
import com.dillon.starsectormarines.battle.command.AttackMoveGoal;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ServiceAssignedObjectiveGoal;
import com.dillon.starsectormarines.battle.command.WithdrawAssignedGoal;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-unit GOAP dispatch for mech-class units. Sibling of
 * {@link GoapInfantryBehavior} with its own goal + action lists. Replaces
 * the legacy per-unit {@code MechCombatantBehavior} loop — the parity body
 * lives in {@link EngageAtCurrentBand} now.
 *
 * <p>Same shape as the infantry path: {@link #replanIfNeeded} runs once
 * per mech squad per tick during the alert-update phase; the per-unit
 * {@link #update} call consumes the squad's plan during the serial
 * unit-update pass.
 *
 * <p>Every doctrine-aware goal installs the same squad step,
 * {@link ExecuteMechDoctrine}. That action dispatches each member through its
 * own effective role, so mixed lances are not collapsed into the doctrine of
 * whichever member caused the squad-level goal to win.
 */
public final class GoapMechBehavior implements UnitBehavior {

    public static final GoapMechBehavior INSTANCE = new GoapMechBehavior();

    /** Goals the squad-level planner picks from each replan. Highest-priority bucket wins, relevance breaks ties. Doctrine mission goals yield to withdrawal, rescue, and broken-morale survival; the ambient engagement goal remains the floor. Every doctrine goal delegates through {@link ExecuteMechDoctrine} for mixed-lance execution. */
    public static final List<Goal> MECH_GOALS = List.of(
            PatrolRescueFormationGoal.INSTANCE,
            WithdrawAssignedGoal.INSTANCE,
            AttackMoveGoal.INSTANCE,
            MechAssignedObjectiveGoal.INSTANCE,
            BalancedContactGoal.INSTANCE,
            ServiceAssignedObjectiveGoal.INSTANCE,
            SweepAssignedSectorGoal.INSTANCE,
            AdvanceAssignedTrackGoal.INSTANCE,
            DefendAssignedSiteGoal.INSTANCE,
            DefendAssignedAreaGoal.INSTANCE,
            DefendAssignedTrackGoal.INSTANCE,
            AssaultAssignedObjectiveGoal.INSTANCE,
            OverwatchKillZoneGoal.INSTANCE,
            BackstopAssignedSquadGoal.INSTANCE,
            MechSurviveContact.INSTANCE,
            MechEliminateEnemiesGoal.INSTANCE
    );

    /** Actions the planner may use. The role-anchored goals ship custom-plans that bypass the planner; the list is the registry for any future goal that wants backward-chaining search. */
    public static final List<Action> MECH_ACTIONS = List.of(
            PatrolRescueFormation.INSTANCE,
            ExecuteMechDoctrine.INSTANCE,
            BreachAndAssault.INSTANCE,
            EngageAtCurrentBand.INSTANCE,
            OverwatchKillZone.INSTANCE,
            BackstopAssignedSquad.INSTANCE,
            MechBreakContact.INSTANCE
    );


    /** Hard cap on planner-search node expansions for goals without a custom plan. */
    public static final int PLAN_NODE_LIMIT = 256;

    private GoapMechBehavior() {}

    @Override
    public void update(long unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit);
        if (squad == null) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan catches up next tick; idle this frame rather than fall
            // through to some arbitrary default. No executing plan owns an
            // old path, so drop it rather than exposing a ghost order.
            if (plan == null && !Paths.isEmpty(sim.world().path(unit))) {
                sim.clearPath(unit);
            }
            return;
        }

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
            case RUNNING -> { /* keep ticking the same step next frame */ }
        }
    }

    /**
     * Called by {@code BattleSimulation} once per mech squad per tick during
     * the alert-update pass. Decides whether to (re)build the squad's plan
     * and does so when any trigger fires (no current plan, plan complete,
     * member count changed, {@link Planner#REPLAN_PERIOD} elapsed).
     *
     * <p>Mirrors {@link GoapInfantryBehavior#replanIfNeeded} structurally
     * but operates on {@link #MECH_GOALS} / {@link #MECH_ACTIONS}. Both
     * paths share the same {@link WorldStateBuilder} — the predicates
     * evaluate against squad members regardless of unit type.
     */
    public static void replanIfNeeded(Squad squad, BattleSimulation sim) {
        if (squad.aliveMembers == 0) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.aliveMembersAtLastPlan = 0;
            return;
        }

        boolean memberCountChanged = squad.aliveMembers != squad.aliveMembersAtLastPlan;
        ObjectiveAssignment executableAssignment = squad.assignmentForExecution();
        boolean assignmentChanged = !Objects.equals(executableAssignment,
                squad.assignedObjectiveAtLastPlan);
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= Planner.REPLAN_PERIOD
                           || memberCountChanged
                           || assignmentChanged;

        if (!needsReplan) {
            squad.timeSinceReplan += BattleSimulation.TICK_DT;
            return;
        }

        WorldState current = WorldStateBuilder.build(squad, sim);
        Goal goal = Goal.pickMostRelevant(MECH_GOALS, current, squad, sim);
        if (goal == null) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = 0f;
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            squad.assignedObjectiveAtLastPlan = executableAssignment;
            return;
        }

        SquadPlan plan = goal.customPlan(squad, sim);
        if (plan == null) {
            plan = Planner.plan(
                    current,
                    goal.desiredState(squad, sim),
                    MECH_ACTIONS,
                    squad,
                    sim,
                    PLAN_NODE_LIMIT);
        }

        if (plan != null && !plan.isComplete()) {
            List<Long> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                aliveMembers.add(sim.squadMemberAt(squad.id, i));
            }
            for (SquadPlan.Step step : plan.steps()) {
                List<RoleAssigner.Slot<Long>> slots = step.action.roles(squad, sim);
                Map<String, List<Long>> assignment = RoleAssigner.assign(aliveMembers, slots);
                step.assignments.putAll(assignment);
            }
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = 0f;
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
        squad.assignedObjectiveAtLastPlan = executableAssignment;
    }
}
