package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
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
import com.dillon.starsectormarines.battle.nav.Paths;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.ReflexChain;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * <p>Between "this unit has a squad" and "execute the step" sits
 * {@link InfantryReflexes#CHAIN}, the ordered list of interrupts that may
 * pre-empt the plan for one marine on one tick. It is declared there rather
 * than branched here so its priority order can be read and pinned.
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
            WithdrawAssignedGoal.INSTANCE,
            ServiceAssignedObjectiveGoal.INSTANCE,
            AdvanceAssignedTrackGoal.INSTANCE,
            AttackMoveGoal.INSTANCE,
            DefendAssignedSiteGoal.INSTANCE,
            DefendAssignedAreaGoal.INSTANCE,
            DefendAssignedTrackGoal.INSTANCE,
            ClearAssignedZoneGoal.INSTANCE,
            SweepAssignedSectorGoal.INSTANCE,
            EscortAssignedCiviliansGoal.INSTANCE,
            GarrisonAmbush.INSTANCE,
            FrontageDefense.INSTANCE,
            GuardPost.INSTANCE,
            GarrisonCompound.INSTANCE,
            RoutinePatrol.INSTANCE,
            ReinforceContact.INSTANCE,
            SurviveContact.INSTANCE,
            DisengageFromContactGoal.INSTANCE,
            RecoverFromAmbush.INSTANCE,
            BreachToEngage.INSTANCE,
            HoldEngagementLineGoal.INSTANCE,
            EliminateEnemiesGoal.INSTANCE,
            AmbientEngagementGoal.INSTANCE,
            AwaitOrdersGoal.INSTANCE
    );

    /**
     * Fallback library while a player tactical context is suspended by
     * cohesion. No unrelated mission goal may fill that gap: doing so lets a
     * planter, last stand, or other authored mission role railroad the squad
     * while the player's order is still active.
     */
    private static final List<Goal> NON_MISSION_INFANTRY_GOALS = INFANTRY_GOALS
            .stream()
            .filter(goal -> goal.priority() != Goal.Priority.MISSION)
            .toList();

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

    /** Captured only by the opt-in Conquest profiler, never on the normal tick path. */
    public record ReplanBreakdown(long worldStateNanos, long selectionNanos,
                                  long relevanceNanos, int relevanceCalls,
                                  String slowestRelevanceGoal, long slowestRelevanceNanos,
                                  long customPlanNanos, long searchNanos,
                                  long roleAssignmentNanos, String slowestRoleAction,
                                  long slowestRoleNanos, int roleCandidates,
                                  int planSteps, int planAttempts,
                                  int declinedGoals, String selectedGoal) {
        public static final ReplanBreakdown EMPTY = new ReplanBreakdown(
                0L, 0L, 0L, 0, "", 0L, 0L, 0L, 0L, "", 0L, 0, 0, 0, 0, "");
    }

    public static final class ReplanTiming implements Goal.RelevanceProbe {
        private long worldStateNanos, selectionNanos, relevanceNanos;
        private long slowestRelevanceNanos, customPlanNanos, searchNanos;
        private long roleAssignmentNanos;
        private long slowestRoleNanos;
        private int relevanceCalls, planAttempts, declinedGoals, roleCandidates, planSteps;
        private String slowestRelevanceGoal = "", slowestRoleAction = "", selectedGoal = "";

        @Override
        public void record(Goal goal, long nanos) {
            relevanceNanos += nanos;
            relevanceCalls++;
            if (nanos > slowestRelevanceNanos) {
                slowestRelevanceNanos = nanos;
                slowestRelevanceGoal = goal.name();
            }
        }

        public ReplanBreakdown snapshot() {
            return new ReplanBreakdown(worldStateNanos, selectionNanos,
                    relevanceNanos, relevanceCalls, slowestRelevanceGoal,
                    slowestRelevanceNanos, customPlanNanos, searchNanos,
                    roleAssignmentNanos, slowestRoleAction, slowestRoleNanos,
                    roleCandidates, planSteps, planAttempts, declinedGoals,
                    selectedGoal);
        }
    }

    private GoapInfantryBehavior() {}

    /**
     * Runs the squad-free prefix of {@link InfantryReflexes#CHAIN} for one
     * marine — everything up to the broken-fire-team peel — and reports whether
     * he is still free to act. Returns {@code false} when a reflex consumed the
     * tick, in which case the caller skips {@link Action#execute} this frame.
     *
     * <p>{@code permitsOpportunityFire} narrows rather than silences the
     * opportunity reflexes; which of the three answer under which value is
     * {@link InfantryReflexes}' to say.
     *
     * <p>Kept as a named entry point rather than folded into {@link #update}
     * because it is how a coordinated action's suppression is stated and asked
     * about from outside — a marine with no squad at all can be handed to it,
     * which is exactly what the bounding-overwatch evidence does.
     */
    public static boolean prepareForAction(long unit, BattleControl sim,
                                           boolean permitsOpportunityFire) {
        return ReflexChain.run(InfantryReflexes.PREPARATION_CHAIN, unit,
                sim.squadOf(unit), new ReflexContext(permitsOpportunityFire),
                sim) == null;
    }

    @Override
    public void update(long unit, BattleSimulation sim) {
        Squad squad = sim.squadOf(unit);
        if (squad == null) return;
        if (protectedShelterGuard(squad, sim)) {
            sim.combat().setTargetId(unit, 0L);
            sim.clearPath(unit);
            return;
        }

        // Consult the assigned action before the reflex chain so a move-only
        // role cannot initiate an opportunity rocket and then skip the action
        // that was supposed to keep it moving. An already-started aim still
        // completes — the committed-aim reflex is a shot's lifecycle, not a
        // fresh tactical choice.
        SquadPlan prepPlan = squad.currentPlan;
        if (prepPlan == null && !Paths.isEmpty(sim.world().path(unit))) {
            // A path is execution state owned by the plan that authored it.
            // Once no plan can advance that path, retaining it produces a
            // ghost movement order in diagnostics and can leak into a later
            // plan. A merely-complete plan is intentionally excluded: another
            // member may have just completed Approach while siblings still
            // need that path under the next Engage plan. Committed aim still
            // completes in the reflex chain below.
            sim.clearPath(unit);
        }
        SquadPlan.Step prepStep = prepPlan != null && !prepPlan.isComplete()
                ? prepPlan.currentStep() : null;
        boolean permitsPreparationFire = prepStep == null
                || prepStep.slotOf(unit) == null
                || prepStep.action.permitsOpportunityFire();

        // Everything that outranks the assigned step, in the one order that is
        // this arm's law. See InfantryReflexes for what each entry answers and
        // InfantryReflexOrderTest for why the order may not be shuffled.
        if (ReflexChain.run(InfantryReflexes.CHAIN, unit, squad,
                new ReflexContext(permitsPreparationFire), sim) != null) {
            return;
        }

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan pass (run from BattleSimulation.tick) will catch up next
            // tick at the latest. Keep the planner authoritative for movement,
            // but don't discard a legal consume-once shot while waiting.
            opportunityPrimary(unit, sim);
            return;
        }

        SquadPlan.Step step = plan.currentStep();
        // Null possible under parallel dispatch: a sibling worker advanced past
        // the end between the isComplete() check and here. Skip this tick.
        if (step == null || step.slotOf(unit) == null) {
            opportunityPrimary(unit, sim);
            return;
        }

        ActionStatus status = executeTimed(step, unit, squad, sim);
        if (step.action.permitsOpportunityFire()) {
            opportunityPrimary(unit, sim);
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
                        if (step.action instanceof EnterZone) squad.clearMechScreen();
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

    /** The step's {@code execute}, attributed to its action class in the tick's inner profile. */
    public static ActionStatus executeTimed(SquadPlan.Step step, long unit, Squad squad,
                                            BattleSimulation sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile == null) return step.action.execute(unit, squad, sim);
        long t0 = System.nanoTime();
        try {
            return step.action.execute(unit, squad, sim);
        } finally {
            profile.recordAction(step.action.getClass().getSimpleName(),
                    System.nanoTime() - t0);
        }
    }

    private static void opportunityPrimary(long unit, BattleSimulation sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long t0 = profile != null ? System.nanoTime() : 0L;
        InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.OPPORTUNITY_PRIMARY,
                    System.nanoTime() - t0);
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
     *   <li>A squad-wide direct-LOS episode started, or the alert level transitioned</li>
     *   <li>Any fire team's morale hysteresis entered or left the broken state — the plan has to be rebuilt around a team that just peeled, or make room for one that just rejoined</li>
     *   <li>The alert pass observed hostile incoming fire with LOS to its origin</li>
     *   <li>{@link Planner#REPLAN_PERIOD} sim-seconds have elapsed since the last replan</li>
     * </ul>
     *
     * <p>Called serially by the squad replan pass. The value-oriented search
     * is per-squad, but some goal evaluations read sibling plans for frontage
     * reservations; parallel execution needs a separate publication contract.
     */
    public static void replanIfNeeded(Squad squad, BattleSimulation sim) {
        replanIfNeeded(squad, sim, null);
    }

    public static void replanIfNeeded(Squad squad, BattleSimulation sim,
                                      ReplanTiming timing) {
        if (squad.aliveMembers == 0) {
            // Wiped squad — drop any lingering plan so the assignedMembers
            // list doesn't pin dead units.
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.aliveMembersAtLastPlan = 0;
            squad.clearMechScreen();
            squad.clearBoundingOverwatch();
            squad.clearEngagementDisciplineHold();
            return;
        }
        if (protectedShelterGuard(squad, sim)) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = 0f;
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            squad.clearMechScreen();
            squad.clearBoundingOverwatch();
            return;
        }

        boolean memberCountChanged = squad.aliveMembers != squad.aliveMembersAtLastPlan;
        ObjectiveAssignment executableAssignment = squad.assignmentForExecution();
        boolean assignmentChanged = !Objects.equals(executableAssignment,
                squad.assignedObjectiveAtLastPlan);
        // Incoming fire is a tactical interrupt, not something infantry should
        // ignore until the normal two-second cadence. SquadAlertSystem computes
        // this from the same shot/LOS contract as UNDER_FIRE_AT_LOS immediately
        // before the replan pass. It is transient, so once the squad reaches a
        // hidden cell the ordinary plan can resume on the next replan.
        boolean incomingFireStarted = squad._underFireAtLosThisTick
                && !squad._underFireAtLosLastTick;
        // Contact is a squad-level no-direct-LOS -> some-direct-LOS episode
        // edge. Additional hostile identities join belief without repeatedly
        // replacing the plan during one continuous engagement.
        boolean contactStateChanged = squad._directContactStartedThisTick
                || squad._alertLevelChangedThisTick;
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= Planner.REPLAN_PERIOD
                           || memberCountChanged
                           || assignmentChanged
                           || incomingFireStarted
                           || contactStateChanged
                           || squad._contactDoctrineChangedThisTick
                           || squad._moraleBrokenChangedThisTick;

        if (!needsReplan) {
            squad.timeSinceReplan += BattleSimulation.TICK_DT;
            return;
        }

        squad.routingEpoch++;

        // A live-member change invalidates both the role partition and any
        // in-flight bound authored from it. Sticky mission plans may return
        // the same SquadPlan instance below, so matching target geometry is
        // not enough to preserve the old phase: its moving team can now be
        // dissolved, folded into a sibling, or entirely dead.
        if (memberCountChanged) squad.clearBoundingOverwatch();

        long stageStart = timing == null ? 0L : System.nanoTime();
        WorldState current = WorldStateBuilder.build(squad, sim);
        if (timing != null) timing.worldStateNanos += System.nanoTime() - stageStart;
        Goal.EvaluationContext evaluations = new Goal.EvaluationContext(
                current, squad, sim, timing);
        // Walk down the ladder rather than stopping at the first winner.
        // Relevance answers "is this goal worth wanting"; only the planner
        // answers "can it be acted on from here", and a goal that loses the
        // second question used to end the search holding a null plan — which
        // a member reads as an order to stand still and drop its path. A
        // declined goal is therefore set aside and the next-best is asked,
        // down to the IDLE floor. Bounded by the goal count: each pass either
        // returns a plan or removes one goal from contention.
        Goal goal;
        SquadPlan plan;
        Set<Goal> declined = Set.of();
        while (true) {
            stageStart = timing == null ? 0L : System.nanoTime();
            Goal.Choice choice = pickGoal(squad, sim, declined, evaluations);
            if (timing != null) timing.selectionNanos += System.nanoTime() - stageStart;
            if (choice == null) {
                goal = null;
                plan = null;
                break;
            }
            goal = choice.goal();
            if (timing != null) timing.planAttempts++;
            // Custom-plan escape hatch: goals that synthesize their plan
            // directly (e.g. SecureObjectiveZone walking a zone-graph BFS
            // path) bypass the backward-chaining search and return their plan
            // ready to be filled with role assignments below. Returning null
            // means "use the planner", not "no plan" — the decline is the
            // planner's null below.
            stageStart = timing == null ? 0L : System.nanoTime();
            plan = goal.customPlan(choice.evaluation(), squad, sim);
            if (timing != null) timing.customPlanNanos += System.nanoTime() - stageStart;
            if (plan == null) {
                stageStart = timing == null ? 0L : System.nanoTime();
                plan = Planner.plan(
                        current,
                        goal.desiredState(squad, sim),
                        INFANTRY_ACTIONS,
                        squad,
                        sim,
                        PLAN_NODE_LIMIT);
                if (timing != null) timing.searchNanos += System.nanoTime() - stageStart;
            }
            if (plan != null) break;
            if (declined.isEmpty()) declined = new HashSet<>();
            declined.add(goal);
            if (timing != null) timing.declinedGoals++;
        }
        if (goal == null) {
            // No relevant goal — sit idle until something changes.
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.timeSinceReplan = Planner.periodicTimerAfterReplan(squad.id);
            squad.aliveMembersAtLastPlan = squad.aliveMembers;
            squad.assignedObjectiveAtLastPlan = executableAssignment;
            squad.clearMechScreen();
            squad.clearBoundingOverwatch();
            return;
        }

        if (plan != null && !plan.isComplete()) {
            stageStart = timing == null ? 0L : System.nanoTime();
            // Gather alive squadmates once, hand them to RoleAssigner per step.
            // Stage 1 actions declare a single "any" slot taking all members
            // (Action.roles default) — same effect as the previous "add
            // everyone to every step" wiring. Stage 2 actions override roles()
            // to expose meaningful partitions (planter+portal-holders for
            // sabotage cordon, suppressor+bounder for bounding overwatch, etc.)
            // and the same call here distributes members per slot.
            // A broken fire team is peeling to cover under the dispatcher's
            // morale override, not executing this plan. Leaving its marines in
            // the candidate pool would hand a cordon post or a bounding slot
            // to someone walking the other way, so the squad plans around the
            // team that peeled and takes it back on the replan that fires when
            // its morale clears.
            //
            // A late arrival is out for the same reason and by the same rule:
            // it is crossing open ground to reach the squad, and a plan that
            // slotted it would either stall on it or drag the step back toward
            // the landing zone. It comes back into the pool on the replan after
            // its rejoin retires.
            List<Long> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (squad.fireTeamBroken(sim.squad().fireTeamIndex(member))) continue;
                if (squad.isRejoining(member)) continue;
                aliveMembers.add(member);
            }
            if (timing != null) {
                timing.roleCandidates = aliveMembers.size();
                timing.planSteps = plan.steps().size();
            }
            for (SquadPlan.Step step : plan.steps()) {
                long roleStart = timing == null ? 0L : System.nanoTime();
                Map<String, List<Long>> assignment = step.action.assignRoles(
                        squad, sim, aliveMembers);
                // Some mission goals deliberately retain the current plan
                // across replans to prevent portal oscillation. Replace its
                // role map exactly; putAll alone leaves dissolved fire-team
                // keys and duplicate survivors after casualties.
                step.assignments.clear();
                step.assignments.putAll(assignment);
                if (timing != null) {
                    long duration = System.nanoTime() - roleStart;
                    if (duration > timing.slowestRoleNanos) {
                        timing.slowestRoleNanos = duration;
                        timing.slowestRoleAction = step.action.name();
                    }
                }
            }
            if (timing != null) timing.roleAssignmentNanos += System.nanoTime() - stageStart;
        }
        if (squad.boundingActive && !continuesBoundingAdvance(plan, squad)) {
            squad.clearBoundingOverwatch();
        }
        if (continuesMechScreenAdvance(plan)) squad.mechScreenTick = -1;
        else squad.clearMechScreen();
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        if (timing != null) timing.selectedGoal = goal.name();
        squad.timeSinceReplan = Planner.periodicTimerAfterReplan(squad.id);
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
        squad.assignedObjectiveAtLastPlan = executableAssignment;
    }

    /**
     * Player tactical context is the squad's exclusive mission-tier context
     * until completion. Survival may still suspend it, and the order system
     * removes it before a hard withdrawal replans, but an unrelated authored
     * mission goal cannot compete merely because it also lives in MISSION.
     *
     * <p>Which goal a player's order must win with is {@link OrderCatalog}'s
     * to say, not this dispatcher's — the same row the order system reads to
     * decide who may be handed the order and when it is over.
     */
    private static Goal.Choice pickGoal(Squad squad, BattleSimulation sim,
                                        Set<Goal> declined,
                                        Goal.EvaluationContext evaluations) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || !squad.hasPlayerOrder(assignment.kind())) {
            return Goal.pickMostRelevantPrepared(INFANTRY_GOALS, evaluations,
                    declined);
        }

        // The player's own order still outranks the authored library, but it
        // is not exempt from having to be workable: a declined tactical goal
        // yields to the non-mission ladder rather than pinning the squad to an
        // order it cannot act on. A kind with no player row goes the same way.
        // The order system cannot produce one, but the field behind it is a
        // plain setter, so the case is answered rather than assumed away.
        PlayerOrder player = OrderCatalog.playerOrder(assignment.kind());
        if (player != null && !declined.contains(player.goal())) {
            Goal.Evaluation evaluation = evaluations.evaluate(player.goal());
            if (evaluation.relevance() > 0f) {
                return new Goal.Choice(player.goal(), evaluation);
            }
        }
        return Goal.pickMostRelevantPrepared(
                NON_MISSION_INFANTRY_GOALS, evaluations, declined);
    }

    private static boolean protectedShelterGuard(
            Squad squad, BattleSimulation sim) {
        return sim.isShelterGuard(squad.id) && sim.isCivilianShelterProtected();
    }

    private static boolean continuesBoundingAdvance(SquadPlan plan, Squad squad) {
        if (plan == null || plan.isComplete()) return false;
        SquadPlan.Step step = plan.currentStep();
        if (step == null || !(step.action instanceof EnterZone enter)) return false;
        return enter.targetZoneId() == squad.boundingTargetZoneId
                && enter.destX() == squad.boundingDestX
                && enter.destY() == squad.boundingDestY;
    }

    private static boolean continuesMechScreenAdvance(SquadPlan plan) {
        if (plan == null || plan.isComplete()) return false;
        SquadPlan.Step step = plan.currentStep();
        return step != null && step.action instanceof EnterZone;
    }
}
