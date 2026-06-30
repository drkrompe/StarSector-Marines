package com.dillon.starsectormarines.battle.mech;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * <p>Stage 1 ships one goal ({@link MechEliminateEnemiesGoal}) and one
 * action ({@link EngageAtCurrentBand}). The role-anchored goals
 * ({@code OverwatchKillZone} for LR Support, {@code BackstopAssignedSquad}
 * for Armored Support) layer on top in subsequent slices.
 */
public final class GoapMechBehavior implements UnitBehavior {

    public static final GoapMechBehavior INSTANCE = new GoapMechBehavior();

    /** Goals the squad-level planner picks from each replan. Highest-priority bucket wins, relevance breaks ties. MISSION-priority role goals come first; SURVIVAL-tier {@link MechSurviveContact} wins whenever {@link com.dillon.starsectormarines.battle.decision.goap.Predicate#MORALE_BROKEN} trips (the MISSION goals carve themselves out on that predicate too, so SURVIVAL wins outright); the ENGAGEMENT-priority ambient {@link MechEliminateEnemiesGoal} is the floor. Within MISSION, overwatch is listed first so a mixed-role squad with both roles relevant tips to overwatch (Stage 1 tie-break — per-member goal assignment in Stage 2 resolves this cleanly). */
    public static final List<Goal> MECH_GOALS = List.of(
            OverwatchKillZoneGoal.INSTANCE,
            BackstopAssignedSquadGoal.INSTANCE,
            MechSurviveContact.INSTANCE,
            MechEliminateEnemiesGoal.INSTANCE
    );

    /** Actions the planner may use. The role-anchored goals ship custom-plans that bypass the planner; the list is the registry for any future goal that wants backward-chaining search. */
    public static final List<Action> MECH_ACTIONS = List.of(
            EngageAtCurrentBand.INSTANCE,
            OverwatchKillZone.INSTANCE,
            BackstopAssignedSquad.INSTANCE,
            MechBreakContact.INSTANCE
    );


    /** Hard cap on planner-search node expansions. Stage 1 custom-plans so the planner doesn't run; the cap is set anyway for when role-anchored goals start using {@link Planner#plan}. */
    public static final int PLAN_NODE_LIMIT = 256;

    private GoapMechBehavior() {}

    @Override
    public void update(Entity unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit.entityId);
        if (squad == null) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan catches up next tick; idle this frame rather than fall
            // through to some arbitrary default. Keeps the planner authoritative.
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
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= Planner.REPLAN_PERIOD
                           || memberCountChanged;

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
            List<Entity> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                Entity u = sim.liveUnitAt(i);
                if (!sim.squad().hasSquad(u.entityId) || sim.squad().squadId(u.entityId) != squad.id) continue;
                aliveMembers.add(u);
            }
            for (SquadPlan.Step step : plan.steps()) {
                List<RoleAssigner.Slot<Entity>> slots = step.action.roles(squad, sim);
                Map<String, List<Entity>> assignment = RoleAssigner.assign(aliveMembers, slots);
                step.assignments.putAll(assignment);
            }
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = 0f;
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
    }
}
