package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;

import java.util.List;

/**
 * What a squad wants to be true. The planner's per-replan flow:
 * <ol>
 *   <li>Snapshot the current world state.</li>
 *   <li>Score every registered goal's {@link #relevance}; pick the highest
 *       within the highest-occupied {@link Priority} bucket.</li>
 *   <li>Backward-chain from {@link #desiredState} until the current state
 *       satisfies the regressed preconditions.</li>
 *   <li>Fall back to the next-most-relevant goal if no plan is reachable
 *       within the search limit.</li>
 * </ol>
 *
 * <p>Stateless singletons, same thread-safety contract as {@link Action}:
 * {@link #relevance}, {@link #priority}, and {@link #desiredState} run
 * during the parallel replan window and must be read-only against
 * {@link BattleSimulation}.
 */
public interface Goal {

    /**
     * Goal-priority buckets. Lower ordinal = higher priority. {@link
     * #pickMostRelevant} picks within the highest-priority bucket that has
     * any goal with positive relevance; relevance only breaks ties inside
     * a bucket. This lets a MISSION goal (e.g. {@code HoldPosition} on a
     * {@code MUST_HOLD} node) override an ENGAGEMENT goal (e.g.
     * {@code EliminateEnemies}) even when the engagement scores a higher
     * raw relevance — see stories B, F, H in the tactical story bank.
     */
    enum Priority {
        /** Mission objectives — outrank everything (Stories F, H). */
        MISSION,
        /** HP-threshold gated goals like SurviveContact (Story B). */
        SURVIVAL,
        /** Default for combat goals like EliminateEnemies. */
        ENGAGEMENT,
        /** Reserved for future "no contact, no objective" routines. */
        IDLE
    }

    /** Human-readable identity for logs + debug overlays. */
    String name();

    /**
     * Priority score in this squad's current context. Higher wins
     * <em>within the goal's {@link #priority()} bucket</em>. A "no-context"
     * baseline like {@code 0.1} keeps a goal in the running so the planner
     * falls back to it when no higher-priority goal has a reachable plan.
     * Zero or negative values disable the goal for this squad-tick.
     */
    float relevance(WorldState state, Squad squad, BattleSimulation sim);

    /**
     * Which {@link Priority} bucket this goal lives in. Defaults to
     * {@link Priority#ENGAGEMENT} — appropriate for combat goals that
     * shouldn't override mission or survival objectives.
     */
    default Priority priority() {
        return Priority.ENGAGEMENT;
    }

    /**
     * The {@link WorldState} the planner backward-chains from. Conventionally
     * a tiny state setting only the goal-marker predicates (e.g.
     * {@code ENEMY_DAMAGED = true}). Predicates not specified are unconstrained.
     */
    WorldState desiredState(Squad squad, BattleSimulation sim);

    /**
     * Escape hatch for goals that synthesize their plan directly rather than
     * letting the backward-chaining planner compose one. The replan pass calls
     * this <em>before</em> {@link Planner#plan}; a non-null return is used
     * as-is and {@link #desiredState} is ignored. Returning null falls back to
     * the standard A* search.
     *
     * <p>Used for goals whose plan structure is determined by an external
     * computation (zone-graph BFS for the room-clear sweep, fixed step
     * sequences for scripted set-pieces) — i.e., cases where the
     * precondition/effect chain doesn't carry enough information to drive
     * search productively. The returned plan's steps still get role-assigner
     * filled by the replan caller, so per-member slot scoring works the same
     * way it does for planner-produced plans.
     *
     * <p>Called during the parallel replan window — must be read-only against
     * {@code sim} like the rest of the goal API.
     */
    default SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return null;
    }

    /**
     * Picks the highest-relevance goal in the highest-occupied
     * {@link Priority} bucket. Goals with {@code relevance <= 0} are
     * excluded — they cannot anchor a bucket or be returned. Returns
     * {@code null} when no goal has positive relevance: caller treats that
     * as "squad has nothing to do" (idle).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each goal with {@code relevance > 0}, track the best (max
     *       relevance) entry per {@link Priority} bucket.</li>
     *   <li>Return the bucket-winner from the highest-priority bucket
     *       (lowest ordinal) that has any entry.</li>
     * </ol>
     *
     * <p>Ties within a bucket resolve to the <em>last</em> goal seen
     * (strictly-greater comparison means earlier equal-relevance entries
     * are kept; in practice deterministic given the input list order).
     */
    static Goal pickMostRelevant(List<Goal> goals, WorldState state, Squad squad, BattleSimulation sim) {
        Priority[] buckets = Priority.values();
        Goal[] bucketBest = new Goal[buckets.length];
        float[] bucketBestRelevance = new float[buckets.length];
        for (Goal g : goals) {
            float r = g.relevance(state, squad, sim);
            if (r <= 0f) continue;
            int idx = g.priority().ordinal();
            if (bucketBest[idx] == null || r > bucketBestRelevance[idx]) {
                bucketBest[idx] = g;
                bucketBestRelevance[idx] = r;
            }
        }
        for (int i = 0; i < bucketBest.length; i++) {
            if (bucketBest[i] != null) return bucketBest[i];
        }
        return null;
    }
}
