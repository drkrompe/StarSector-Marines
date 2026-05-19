package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.actions.ApproachPosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.BreakLOS;
import com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.OverwatchPosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.RegroupPosture;
import com.dillon.starsectormarines.battle.ai.goap.goals.BreachToEngage;
import com.dillon.starsectormarines.battle.ai.goap.goals.CordonForPlant;
import com.dillon.starsectormarines.battle.ai.goap.goals.EliminateEnemiesGoal;
import com.dillon.starsectormarines.battle.ai.goap.goals.GarrisonAmbush;
import com.dillon.starsectormarines.battle.ai.goap.goals.RecoverFromAmbush;
import com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone;
import com.dillon.starsectormarines.battle.ai.goap.goals.SurviveContact;
import com.dillon.starsectormarines.battle.ai.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.ai.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-unit GOAP dispatch for infantry. Pairs with the squad-level replan
 * pass {@link #replanIfNeeded(Squad, BattleSimulation)} which builds the
 * {@link SquadPlan}; this dispatcher's {@link #update(Unit, BattleSimulation)}
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
            CordonForPlant.INSTANCE,
            SecureObjectiveZone.INSTANCE,
            GarrisonAmbush.INSTANCE,
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

    /** Sim-seconds between forced replans for squads whose plan didn't otherwise change. Balances responsiveness against planner cost; 2s is long enough to amortize, short enough that stale plans don't cling past a tactical shift. */
    public static final float REPLAN_PERIOD = 2.0f;

    /** Hard cap on planner-search node expansions. 256 is comfortably above what Stage 1's tiny action library needs; Stage 2 may bump as the action surface grows. */
    public static final int PLAN_NODE_LIMIT = 256;

    private GoapInfantryBehavior() {}

    /**
     * Lifecycle prep called once before {@link Action#execute} each tick:
     * advance the rocket-aim animation if mid-aim (short-circuits the action
     * for this tick), then tick cooldowns. Returns {@code false} when the
     * unit is locked in aim — caller should skip {@code action.execute} this
     * frame.
     */
    public static boolean prepareForAction(Unit unit, BattleSimulation sim) {
        if (InfantryUnitPrep.tickAimAndShortCircuit(unit, sim)) return false;
        InfantryUnitPrep.tickCooldowns(unit);
        return true;
    }

    @Override
    public void update(Unit unit, BattleSimulation sim) {
        Squad squad = unit.squadId == Unit.NO_SQUAD ? null : sim.getSquad(unit.squadId);
        if (squad == null) return;

        if (!prepareForAction(unit, sim)) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan pass (run from BattleSimulation.tick) will catch up next
            // tick at the latest. Idle this frame rather than fall through to
            // some arbitrary default — keeps the planner authoritative.
            return;
        }

        SquadPlan.Step step = plan.currentStep();
        if (step.slotOf(unit) == null) return;

        ActionStatus status = step.action.execute(unit, squad, sim);
        switch (status) {
            case SUCCESS -> plan.advance();
            case FAILURE -> squad.currentPlan = null;
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
     *   <li>{@link #REPLAN_PERIOD} sim-seconds have elapsed since the last replan</li>
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
        boolean needsReplan = squad.currentPlan == null
                           || squad.currentPlan.isComplete()
                           || squad.timeSinceReplan >= REPLAN_PERIOD
                           || memberCountChanged;

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
