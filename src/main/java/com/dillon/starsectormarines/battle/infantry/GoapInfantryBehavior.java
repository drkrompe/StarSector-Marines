package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-unit GOAP dispatch for infantry. Pairs with the squad-level replan
 * pass {@link #replanIfNeeded(Squad, BattleSimulation)} which builds the
 * {@link SquadPlan}; this dispatcher's {@link #update(long, BattleSimulation)}
 * is the per-tick consumer that executes the current step's action for one
 * assigned member.
 *
 * <p>Two registries live here:
 * <ul>
 *   <li>{@link #INFANTRY_GOALS} — the goal list the replan picks from. Stage 1
 *       has one ({@link EliminateEnemiesGoal}); Stage 2 will grow this with
 *       {@code SurviveContact}, {@code SecurePosition}, mission goals.</li>
 *   <li>{@link #INFANTRY_ACTIONS} — the action library the planner may use.
 *       Stage 1 has three postures; Stage 2 will add suppress / flank / cover /
 *       advance-under-cover.</li>
 * </ul>
 *
 * <p>Solo units (no squad) idle here — the planner is a squad-level
 * construct, and a unit without a squad has no plan to consult. In
 * practice every alive combatant is squad-assigned (marines via shuttle
 * deboard, defenders via {@code BattleSetup}); non-squad units are a
 * transient edge case.
 */
public final class GoapInfantryBehavior implements UnitBehavior {

    public static final GoapInfantryBehavior INSTANCE = new GoapInfantryBehavior();

    /** Goals the squad-level planner picks from each replan. Highest-priority bucket wins, relevance breaks ties within a bucket (see {@link Goal#pickMostRelevant}). */
    public static final List<Goal> INFANTRY_GOALS = List.of(
            HoldPosition.INSTANCE,
            CordonForPlant.INSTANCE,
            SecureObjectiveZone.INSTANCE,
            SecureCompoundGoal.INSTANCE,
            ClearAssignedZoneGoal.INSTANCE,
            EscortAssignedCiviliansGoal.INSTANCE,
            GarrisonAmbush.INSTANCE,
            GuardPost.INSTANCE,
            GarrisonCompound.INSTANCE,
            RoutinePatrol.INSTANCE,
            ReinforceContact.INSTANCE,
            SurviveContact.INSTANCE,
            RecoverFromAmbush.INSTANCE,
            BreachToEngage.INSTANCE,
            EliminateEnemiesGoal.INSTANCE
    );

    /** Actions the planner may use. */
    public static final List<Action> INFANTRY_ACTIONS = List.of(
            EngagePosture.INSTANCE,
            ApproachPosture.INSTANCE,
            RegroupPosture.INSTANCE,
            OverwatchPosture.INSTANCE,
            BreakLOS.INSTANCE
    );


    /** Hard cap on planner-search node expansions. 256 is comfortably above what Stage 1's tiny action library needs; Stage 2 may bump as the action surface grows. */
    public static final int PLAN_NODE_LIMIT = 256;

    private GoapInfantryBehavior() {}

    /**
     * Lifecycle prep called once before {@link Action#execute} each tick:
     * advance the rocket-aim animation if mid-aim (short-circuits the action
     * for this tick), tick cooldowns, then opportunistically commit a rocket
     * if the current action permits opportunity fire and a turret-of-opportunity
     * sits in range with LOS. Returns {@code false} when the unit is locked in
     * aim (existing or freshly initiated) — caller should skip
     * {@code action.execute} this frame.
     */
    public static boolean prepareForAction(long unit, BattleControl sim,
                                           boolean permitsOpportunityFire) {
        if (InfantryUnitPrep.tickAimAndShortCircuit(unit, sim)) return false;
        InfantryUnitPrep.tickCooldowns(unit, sim.world());
        if (permitsOpportunityFire && InfantryUnitPrep.tryOpportunityRocket(unit, sim)) return false;
        return true;
    }

    @Override
    public void update(long unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit);
        if (squad == null) return;

        // Consult the assigned action before the preparation hook so a
        // move-only role cannot initiate an opportunity rocket and then skip
        // the action that was supposed to keep it moving. An already-started
        // aim still completes — tickAimAndShortCircuit is a committed-shot
        // lifecycle, not a fresh tactical choice.
        SquadPlan prepPlan = squad.currentPlan;
        SquadPlan.Step prepStep = prepPlan != null && !prepPlan.isComplete()
                ? prepPlan.currentStep() : null;
        boolean permitsPreparationFire = prepStep == null
                || prepStep.slotOf(unit) == null
                || prepStep.action.permitsOpportunityFire();
        if (!prepareForAction(unit, sim, permitsPreparationFire)) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan pass (run from BattleSimulation.tick) will catch up next
            // tick at the latest. Keep the planner authoritative for movement,
            // but don't discard a legal consume-once shot while waiting.
            InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
            return;
        }

        SquadPlan.Step step = plan.currentStep();
        // Null possible under parallel dispatch: a sibling worker advanced past
        // the end between the isComplete() check and here. Skip this tick.
        if (step == null || step.slotOf(unit) == null) {
            InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
            return;
        }

        ActionStatus status = step.action.execute(unit, squad, sim);
        if (step.action.permitsOpportunityFire()) {
            InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
        }
        switch (status) {
            // SUCCESS / FAILURE mutate squad-shared plan state. Two members
            // both observing the same step's SUCCESS would double-advance
            // (skipping the next step) without the lock; the inside-lock
            // recheck of (plan == squad.currentPlan && plan.currentStep() ==
            // step) ensures only the first observer commits the advance.
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
     * Called by {@code BattleSimulation} once per squad per tick during the
     * alert-update pass. Decides whether to (re)build the squad's plan and
     * does so when any trigger fires:
     * <ul>
     *   <li>No current plan</li>
     *   <li>Current plan ran to completion</li>
     *   <li>Squad lost or gained a live member since the last plan (death-driven freshness)</li>
     *   <li>The alert pass observed hostile incoming fire with LOS to its origin</li>
     *   <li>{@link Planner#REPLAN_PERIOD} sim-seconds have elapsed since the last replan</li>
     * </ul>
     *
     * <p><b>Parallelism candidate.</b> Planning is purely functional and
     * per-squad — this method is safe to invoke across squads concurrently
     * once the sim is willing to parallelize the alert-update pass. Stage 1
     * calls it serially; the data-oriented WorldState + stateless actions
     * (see {@code roadmap/ai/README.md} parallelism section) are sized for
     * the parallel future.
     */
    public static void replanIfNeeded(Squad squad, BattleSimulation sim) {
        if (squad.aliveMembers == 0) {
            // Wiped squad — drop any lingering plan so the assignedMembers
            // list doesn't pin dead units.
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.aliveMembersAtLastPlan = 0;
            return;
        }

        boolean memberCountChanged = squad.aliveMembers != squad.aliveMembersAtLastPlan;
        // Incoming fire is a tactical interrupt, not something infantry should
        // ignore until the normal two-second cadence. SquadAlertSystem computes
        // this from the same shot/LOS contract as UNDER_FIRE_AT_LOS immediately
        // before the replan pass. It is transient, so once the squad reaches a
        // hidden cell the ordinary plan can resume on the next replan.
        boolean incomingFireStarted = squad._underFireAtLosThisTick
                && !squad._underFireAtLosLastTick;
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= Planner.REPLAN_PERIOD
                           || memberCountChanged
                           || incomingFireStarted;

        if (!needsReplan) {
            squad.timeSinceReplan += BattleSimulation.TICK_DT;
            return;
        }

        WorldState current = WorldStateBuilder.build(squad, sim);
        Goal goal = Goal.pickMostRelevant(INFANTRY_GOALS, current, squad, sim);
        if (goal == null) {
            // No relevant goal — sit idle until something changes.
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = 0f;
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            return;
        }

        // Custom-plan escape hatch: goals that synthesize their plan directly
        // (e.g. SecureObjectiveZone walking a zone-graph BFS path) bypass the
        // backward-chaining search and return their plan ready to be filled
        // with role assignments below. Returns null when the goal wants to
        // fall back to the planner — keeps the API a single switch.
        SquadPlan plan = goal.customPlan(squad, sim);
        if (plan == null) {
            plan = Planner.plan(
                    current,
                    goal.desiredState(squad, sim),
                    INFANTRY_ACTIONS,
                    squad,
                    sim,
                    PLAN_NODE_LIMIT);
        }

        if (plan != null && !plan.isComplete()) {
            // Gather alive squadmates once, hand them to RoleAssigner per step.
            // Stage 1 actions declare a single "any" slot taking all members
            // (Action.roles default) — same effect as the previous "add
            // everyone to every step" wiring. Stage 2 actions override roles()
            // to expose meaningful partitions (planter+portal-holders for
            // sabotage cordon, suppressor+bounder for bounding overwatch, etc.)
            // and the same call here distributes members per slot.
            List<Long> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long u = sim.liveUnitAt(i);
                if (!sim.squad().hasSquad(u) || sim.squad().squadId(u) != squad.id) continue;
                aliveMembers.add(u);
            }
            for (SquadPlan.Step step : plan.steps()) {
                List<RoleAssigner.Slot<Long>> slots = step.action.roles(squad, sim);
                Map<String, List<Long>> assignment = RoleAssigner.assign(aliveMembers, slots);
                step.assignments.putAll(assignment);
            }
        }
        if (squad.boundingActive && !continuesBoundingAdvance(plan, squad)) {
            squad.clearBoundingOverwatch();
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = 0f;
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
    }

    private static boolean continuesBoundingAdvance(SquadPlan plan, Squad squad) {
        if (plan == null || plan.isComplete()) return false;
        SquadPlan.Step step = plan.currentStep();
        if (step == null || !(step.action instanceof EnterZone enter)) return false;
        return enter.targetZoneId() == squad.boundingTargetZoneId
                && enter.destX() == squad.boundingDestX
                && enter.destY() == squad.boundingDestY;
    }
}
