package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.actions.ApproachPosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.RegroupPosture;
import com.dillon.starsectormarines.battle.ai.goap.goals.EliminateEnemiesGoal;

import java.util.List;

/**
 * Per-unit GOAP dispatch for infantry. Pairs with the squad-level replan
 * pass (run from {@code BattleSimulation}, parallel across squads) which
 * builds the {@link SquadPlan}; this dispatcher's job is the serial per-unit
 * tick: handle lifecycle prep, then execute the current plan step for the
 * assigned member.
 *
 * <p><b>Stage 1 stub — {@link #update} is unimplemented.</b> Task 8 wires
 * up the plan-step execution. What this class holds today:
 * <ul>
 *   <li>{@link #INFANTRY_GOALS} / {@link #INFANTRY_ACTIONS} — the registries
 *       the replan pass uses. Adding a goal or action in Stage 2 = adding it
 *       to one of these lists.</li>
 *   <li>{@link #prepareForAction(Unit, BattleSimulation)} — per-tick lifecycle
 *       hook that ticks cooldowns and handles the secondary-aim short-circuit
 *       before delegating to the action. Pulled out of the action bodies so
 *       a mid-aim marine isn't stuck in animation when the plan flips away
 *       from {@code EngagePosture}.</li>
 * </ul>
 */
public final class GoapInfantryBehavior implements UnitBehavior {

    public static final GoapInfantryBehavior INSTANCE = new GoapInfantryBehavior();

    /**
     * Goals the squad-level planner picks from each replan. Highest-relevance
     * wins (see {@link Goal#pickMostRelevant}). Stage 1 has only one goal —
     * Stage 2 will add {@code SurviveContact}, {@code SecurePosition}, etc.
     */
    public static final List<Goal> INFANTRY_GOALS = List.of(
            EliminateEnemiesGoal.INSTANCE
    );

    /**
     * Actions the planner may use. Stage 1 has three — Stage 2 will add
     * {@code Suppress}, {@code MoveToFlank}, {@code TakeCover}, etc.
     */
    public static final List<Action> INFANTRY_ACTIONS = List.of(
            EngagePosture.INSTANCE,
            ApproachPosture.INSTANCE,
            RegroupPosture.INSTANCE
    );

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
        // Stage 1 stub. Task 8 wires plan-step execution:
        //   1. Resolve squad + current plan; bail if solo / no plan.
        //   2. prepareForAction(unit, sim); bail if false.
        //   3. Execute current step's action for this unit if assigned.
        //   4. Advance / invalidate the plan based on ActionStatus return.
        throw new UnsupportedOperationException(
                "GoapInfantryBehavior.update is wired in task 8 — flag USE_GOAP_INFANTRY remains off until then.");
    }
}
