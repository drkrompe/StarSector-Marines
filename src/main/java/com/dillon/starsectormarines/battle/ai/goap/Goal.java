package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;

import java.util.List;

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

    /**
     * Picks the highest-relevance goal from a registry. Goals returning
     * negative relevance are disabled for this squad-tick and excluded.
     * Returns {@code null} when every goal is disabled — caller treats that
     * as "squad has nothing to do" (idle).
     *
     * <p>Ties resolve to the first goal in the list (deterministic). If we
     * grow many goals with overlapping relevance, switch to a randomized
     * tiebreaker or a strict priority ordering.
     */
    static Goal pickMostRelevant(List<Goal> goals, WorldState state, Squad squad, BattleSimulation sim) {
        Goal best = null;
        float bestRelevance = Float.NEGATIVE_INFINITY;
        for (Goal g : goals) {
            float r = g.relevance(state, squad, sim);
            if (r < 0f) continue;
            if (r > bestRelevance) {
                bestRelevance = r;
                best = g;
            }
        }
        return best;
    }
}
