package com.dillon.starsectormarines.battle.ai.goap.scoring;

/**
 * Maps a candidate to a suitability score. Higher = better. The planner uses
 * this to fill plan-step slots ("which squadmate should anchor?", "which
 * squadmate should flank?") without each action reimplementing comparator
 * boilerplate.
 *
 * <p><b>Thread-safety contract:</b> implementations are stateless and pure.
 * The parallel replan window runs many scorers concurrently across squads;
 * a scorer that caches mutable state would race.
 */
@FunctionalInterface
public interface Scorer<T> {

    float score(T candidate);
}
