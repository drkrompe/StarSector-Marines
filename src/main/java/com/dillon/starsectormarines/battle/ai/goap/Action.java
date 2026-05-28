package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.goap.scoring.RoleAssigner;

import java.util.List;

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
     *
     * <p>Stage 2 onward prefers {@link #roles} which carries this information
     * per slot. Retained for back-compat with the Stage 1 actions; new actions
     * should leave this returning a sentinel (sum of slot counts) or 1.
     */
    int requiredMembers();

    /**
     * Declares the role slots this action exposes for the role assigner to
     * fill. Each slot has a name (used for per-member action branching in
     * {@link #execute}), a count, and a {@link com.dillon.starsectormarines.battle.ai.goap.scoring.Scorer}
     * ranking candidate members.
     *
     * <p>Default implementation returns a single {@code "any"} slot taking
     * all of {@code squad.aliveMembers} with a no-preference scorer — matches
     * the Stage 1 "every member does the action" wiring without action
     * subclasses having to opt in. Stage 2+ actions override to expose
     * meaningful role partitions ({@code "planter" + "portal:0" + "portal:1"}
     * for a sabotage cordon, {@code "suppressor" + "bounder"} for bounding
     * overwatch, etc.).
     *
     * <p>Called during the parallel replan window — must be read-only against
     * {@code sim}. Returning a slot list with total count exceeding
     * {@code squad.aliveMembers} is fine; the assigner only fills what's
     * available.
     */
    default List<RoleAssigner.Slot<Unit>> roles(Squad squad, BattleSimulation sim) {
        return List.of(new RoleAssigner.Slot<>("any", squad.aliveMembers, c -> 0f));
    }

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

    /**
     * Cells this action operates on, for the debug-overlay highlight tool. The
     * GOAP debug panel calls this when the player toggles "show cells" on a
     * plan step. Default is empty — actions whose semantics are bound to
     * specific cells (cordon guard posts, choke-point LOS cells, breach
     * stack-up cells) override; abstract actions (Engage, Approach) return
     * empty and so don't surface a highlight button.
     *
     * <p>Returns {@code [x, y]} pairs. Read-only against {@code sim} —
     * called from the HUD render path, not the planner, but the contract
     * stays the same in case a future caller invokes it during the parallel
     * replan window.
     */
    default List<int[]> highlightCells(Squad squad, BattleSimulation sim) {
        return List.of();
    }
}
