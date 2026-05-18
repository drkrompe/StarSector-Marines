package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;

/**
 * What a squad wants to be true. The planner's per-replan flow:
 * <ol>
 *   <li>Snapshot the current world state.</li>
 *   <li>Score every registered goal's {@link #relevance}; pick the highest.</li>
 *   <li>Backward-chain from {@link #desiredState} until the current state
 *       satisfies the regressed preconditions.</li>
 *   <li>Fall back to the next-most-relevant goal if no plan is reachable
 *       within the search limit.</li>
 * </ol>
 *
 * <p>Stateless singletons, same thread-safety contract as {@link Action}:
 * {@link #relevance} and {@link #desiredState} run during the parallel
 * replan window and must be read-only against {@link BattleSimulation}.
 */
public interface Goal {

    /** Human-readable identity for logs + debug overlays. */
    String name();

    /**
     * Priority score in this squad's current context. Higher wins. A
     * "no-context" baseline like {@code 0.1} keeps a goal in the running so
     * the planner falls back to it when no higher-priority goal has a
     * reachable plan. Negative values disable the goal for this squad-tick.
     */
    float relevance(WorldState state, Squad squad, BattleSimulation sim);

    /**
     * The {@link WorldState} the planner backward-chains from. Conventionally
     * a tiny state setting only the goal-marker predicates (e.g.
     * {@code ENEMY_DAMAGED = true}). Predicates not specified are unconstrained.
     */
    WorldState desiredState(Squad squad, BattleSimulation sim);
}
