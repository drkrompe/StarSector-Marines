package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.actions.ApproachPosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.RegroupPosture;
import com.dillon.starsectormarines.battle.ai.goap.goals.EliminateEnemiesGoal;
import com.dillon.starsectormarines.battle.ai.goap.world.WorldStateBuilder;

import java.util.ArrayList;
import java.util.List;

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
 * <p>Solo units (no squad) fall back to {@link InfantryCombatantBehavior} —
 * the planner is a squad-level construct, and one marine isn't enough to
 * justify a plan.
 */
public final class GoapInfantryBehavior implements UnitBehavior {

    public static final GoapInfantryBehavior INSTANCE = new GoapInfantryBehavior();

    /** Goals the squad-level planner picks from each replan. Highest-relevance wins (see {@link Goal#pickMostRelevant}). */
    public static final List<Goal> INFANTRY_GOALS = List.of(
            EliminateEnemiesGoal.INSTANCE
    );

    /** Actions the planner may use. */
    public static final List<Action> INFANTRY_ACTIONS = List.of(
            EngagePosture.INSTANCE,
            ApproachPosture.INSTANCE,
            RegroupPosture.INSTANCE
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
        if (squad == null) {
            // Solo unit — no squad-level plan, fall back to the legacy path.
            InfantryCombatantBehavior.INSTANCE.update(unit, sim);
            return;
        }

        if (!prepareForAction(unit, sim)) return;

        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) {
            // Replan pass (run from BattleSimulation.tick) will catch up next
            // tick at the latest. Idle this frame rather than fall through to
            // some arbitrary default — keeps the planner authoritative.
            return;
        }

        SquadPlan.Step step = plan.currentStep();
        if (!step.assignedMembers.contains(unit)) return;

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

        SquadPlan plan = Planner.plan(
                current,
                goal.desiredState(squad, sim),
                INFANTRY_ACTIONS,
                squad,
                sim,
                PLAN_NODE_LIMIT);

        if (plan != null && !plan.isComplete()) {
            // Stage 1 assignment: every alive squadmate is bound to every step.
            // Postures already do the right thing per-member based on personal
            // LOS / range / cohesion state. Stage 2 will use RoleAssigner to
            // fill slot-specific assignments for multi-role actions
            // (anchor + flanker, suppressor + mover, etc.).
            List<Unit> aliveMembers = new ArrayList<>(squad.aliveMembers);
            for (Unit u : sim.getUnits()) {
                if (!u.isAlive() || u.squadId != squad.id) continue;
                aliveMembers.add(u);
            }
            for (SquadPlan.Step step : plan.steps()) {
                step.assignedMembers.addAll(aliveMembers);
            }
        }
        squad.currentPlan = plan;
        squad.currentGoal = goal;
        squad.timeSinceReplan = 0f;
        squad.aliveMembersAtLastPlan = squad.aliveMembers;
    }
}
