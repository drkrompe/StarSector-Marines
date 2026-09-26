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
import com.dillon.starsectormarines.battle.command.OrderCatalog;
import com.dillon.starsectormarines.battle.command.OrderCatalog.PlayerOrder;
import com.dillon.starsectormarines.battle.command.ServiceAssignedObjectiveGoal;
import com.dillon.starsectormarines.battle.command.WithdrawAssignedGoal;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.decision.ReflexChain;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Fallback library while a player order of the lance's own is declined or
     * scores nothing. The counterpart of infantry's
     * {@code NON_MISSION_INFANTRY_GOALS} and it states the same law: no
     * unrelated mission goal may fill the gap the player's order left, or an
     * authored role railroads the lance while the order still stands.
     */
    private static final List<Goal> NON_MISSION_MECH_GOALS = MECH_GOALS
            .stream()
            .filter(goal -> goal.priority() != Goal.Priority.MISSION)
            .toList();

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
        if (squad == null || !squad.availableToPlan(unit, sim)) return;

        // Everything that outranks the doctrine step, in this arm's declared
        // order. One entry today; see MechReflexes for why it is a list.
        // Opportunity fire is permitted because no mech step withholds it —
        // the suppression the flag carries is an infantry bounding concern.
        if (ReflexChain.run(MechReflexes.CHAIN, unit, squad,
                new ReflexContext(true), sim) != null) {
            return;
        }

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

        ActionStatus status = GoapInfantryBehavior.executeTimed(step, unit, squad, sim);
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
        if (squad.aliveMembers == 0 || squad.autonomousMemberCount(sim) == 0) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
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

        squad.routingEpoch++;

        WorldState current = WorldStateBuilder.build(squad, sim);
        // The ladder descends past a goal that cannot be planned, as it does
        // for infantry: committing a null plan tells a member to stand still
        // and drop its path, which is never the right answer while another
        // goal would have produced work.
        //
        // No mech squad can reach the bottom of this loop today, and that is
        // a property of the action library rather than of the mech.
        // ExecuteMechDoctrine carries no preconditions and satisfies
        // ENEMY_DAMAGED, so the ENGAGEMENT floor always chains and the
        // first pass always returns a plan. Infantry's counterpart
        // {@code ApproachPosture} needs a target the squad may not have, which
        // is exactly why the freeze was found there. The descent is here so
        // the law holds for both dispatchers rather than for whichever one
        // happens to have an unconditioned floor action — a mech action that
        // ever grows a precondition must not reintroduce the freeze.
        Goal goal;
        SquadPlan plan;
        Set<Goal> declined = Set.of();
        while (true) {
            goal = pickGoal(current, squad, sim, declined);
            if (goal == null) {
                plan = null;
                break;
            }
            plan = goal.customPlan(squad, sim);
            if (plan == null) {
                plan = Planner.plan(
                        current,
                        goal.desiredState(squad, sim),
                        MECH_ACTIONS,
                        squad,
                        sim,
                        PLAN_NODE_LIMIT);
            }
            if (plan != null) break;
            if (declined.isEmpty()) declined = new HashSet<>();
            declined.add(goal);
        }
        if (goal == null) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = Planner.periodicTimerAfterReplan(squad.id);
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            squad.assignedObjectiveAtLastPlan = executableAssignment;
            return;
        }

        if (plan != null && !plan.isComplete()) {
            List<Long> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (squad.availableToPlan(member, sim)) aliveMembers.add(member);
            }
            for (SquadPlan.Step step : plan.steps()) {
                List<RoleAssigner.Slot<Long>> slots = step.action.roles(squad, sim);
                Map<String, List<Long>> assignment = RoleAssigner.assign(aliveMembers, slots);
                step.assignments.putAll(assignment);
            }
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = Planner.periodicTimerAfterReplan(squad.id);
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
        squad.assignedObjectiveAtLastPlan = executableAssignment;
    }

    /**
     * The goal a replan serves. This is where the two dispatchers were made to
     * agree: a player's order names its goal in {@link OrderCatalog}, and that
     * goal is looked up and wins outright — exactly as it does for infantry —
     * rather than being entered into the ordinary {@link #MECH_GOALS}
     * competition and left to win the MISSION bucket on relevance. The only
     * order a lance can be handed today does win it, so the lookup changes
     * nothing while it is the only one; it is what keeps the next one from
     * having to.
     *
     * <p>The goal still has to be workable. A declined order, or one whose
     * goal scores nothing, falls to {@link #NON_MISSION_MECH_GOALS} rather
     * than to the whole ladder, and <b>that part is a deliberate change for
     * mechs</b>: the law is the one infantry's counterpart list already
     * states, that no unrelated authored mission goal may take a squad over
     * while the player's order still stands.
     */
    private static Goal pickGoal(WorldState current, Squad squad,
                                 BattleSimulation sim, Set<Goal> declined) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || !squad.hasPlayerOrder(assignment.kind())) {
            return Goal.pickMostRelevant(MECH_GOALS, current, squad, sim, declined);
        }
        PlayerOrder player = OrderCatalog.playerOrder(assignment.kind());
        if (player != null
                && !declined.contains(player.goal())
                && player.goal().relevance(current, squad, sim) > 0f) {
            return player.goal();
        }
        return Goal.pickMostRelevant(
                NON_MISSION_MECH_GOALS, current, squad, sim, declined);
    }
}
