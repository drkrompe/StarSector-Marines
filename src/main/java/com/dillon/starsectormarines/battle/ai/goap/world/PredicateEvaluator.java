package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;

/**
 * One predicate's "is this true right now?" oracle. Registered in
 * {@link WorldStateBuilder}'s static table, one per
 * {@link com.dillon.starsectormarines.battle.ai.goap.Predicate} entry.
 *
 * <p><b>Thread-safety contract:</b> evaluators run during the parallel
 * replan window and must be <i>read-only</i> against
 * {@link BattleSimulation} state. Stateless and side-effect-free.
 *
 * <p>Adding a predicate is a two-line change: add an enum entry, add an
 * evaluator. The planner, action implementations, and goals reference
 * predicates by enum identity and never need to know what backs them.
 */
@FunctionalInterface
public interface PredicateEvaluator {
    boolean evaluate(Squad squad, BattleSimulation sim);
}
