package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;

/**
 * One operator in the GOAP action library. Implementations are
 * <b>stateless singletons</b> so the planner can hold a single shared
 * instance per action type and run search in parallel across squads
 * without contention. All per-step state lives on the {@link SquadPlan}
 * step and the assigned {@link Unit}'s fields, never on the action.
 *
 * <p>The {@link #preconditions()} / {@link #effects()} pair is what the
 * planner reasons about; {@link #cost(WorldState, Squad, BattleSimulation)}
 * tunes which equally-valid plans the search prefers; {@link #execute}
 * runs one tick of the action against the sim during the serial
 * unit-update pass.
 *
 * <p><b>Thread-safety contract:</b> {@link #preconditions()},
 * {@link #effects()}, and {@link #cost} run during the parallel replan
 * window — they must be read-only against {@link BattleSimulation} state.
 * {@link #execute} runs only in the serial unit-update pass and is free
 * to mutate the sim.
 */
public interface Action {

    /** Human-readable identity, used in logs + debug overlays. Should match the simple class name in most cases. */
    String name();

    /**
     * State the world must be in for this action to be applicable. Conventionally
     * a small {@link WorldState} mentioning only the predicates this action
     * cares about; unrelated predicates are left unspecified.
     *
     * <p>Returned value is conventionally cached (a static final field on the
     * implementation) since the planner calls this many times per replan.
     */
    WorldState preconditions();

    /**
     * State the action produces when it completes. The planner regresses
     * search nodes by overwriting these predicates in the desired state with
     * this action's preconditions. Like {@link #preconditions()}, conventionally
     * cached.
     */
    WorldState effects();

    /**
     * Cost the planner accumulates when adding this action to a plan prefix.
     * Dynamic so an action can be cheaper when conditions favor it (e.g. a
     * firing-position move that finds high-cover cells nearby). Stage 1
     * implementations return a constant.
     *
     * <p>Called during the parallel replan window — must be read-only against
     * {@code sim}.
     */
    float cost(WorldState state, Squad squad, BattleSimulation sim);

    /**
     * How many squad members this action consumes from the squad pool when
     * the role assigner fills the plan. {@code 1} for solo actions ("one
     * squadmate fires"); {@code >1} for coordinated actions ("anchor + flanker
     * pair") that arrive in Stage 2+.
     */
    int requiredMembers();

    /**
     * Runs one tick of this action for one assigned {@code member}. Called
     * in the serial unit-update pass; free to mutate the sim. Returns:
     * <ul>
     *   <li>{@link ActionStatus#RUNNING} — keep ticking this action next frame.</li>
     *   <li>{@link ActionStatus#SUCCESS} — advance the plan to the next step.</li>
     *   <li>{@link ActionStatus#FAILURE} — invalidate the plan, trigger replan.</li>
     * </ul>
     */
    ActionStatus execute(Unit member, Squad squad, BattleSimulation sim);
}
