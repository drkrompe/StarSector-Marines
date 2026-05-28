package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;

/**
 * Story A re-trigger goal — when {@link Predicate#UNDER_FIRE_AT_LOS} flips
 * true (a squadmate is taking incoming fire from a shooter with LOS back to
 * the shot's origin), peel off into cover via
 * {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreakLOS}.
 *
 * <p>Lives in {@link Priority#SURVIVAL}, alongside {@link SurviveContact}.
 * Relevance is {@code 0.5f} so that a morale-broken squad still picks
 * SurviveContact (relevance {@code 1.0f}) — "totally retreat" outranks
 * "duck and re-engage." When morale is intact, RecoverFromAmbush wins its
 * bucket and outranks the ENGAGEMENT-tier {@link EliminateEnemiesGoal}, so a
 * squad caught in a fire lane breaks LOS first and re-engages from cover on
 * the next replan once the predicate clears.
 *
 * <p>Uses the standard backward-chaining planner (no {@code customPlan}) —
 * desiredState is just {@code UNDER_FIRE_AT_LOS=false}, which only
 * {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreakLOS}
 * produces, so the planner composes a single-step plan deterministically.
 *
 * <p><b>Stage 1 limitation.</b> The plan is squad-wide: every assigned
 * member runs {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreakLOS},
 * including ones not personally in the fire lane. The action degrades
 * gracefully — {@code findFallbackPosition} on an already-hidden cell picks
 * the member's own cell as the best (distance-0) candidate, so unexposed
 * members return SUCCESS without moving. Per-member triggering ("only the
 * exposed members duck while the rest keep firing") waits for Slice 4's
 * per-member action assignment.
 */
public final class RecoverFromAmbush implements Goal {

    public static final RecoverFromAmbush INSTANCE = new RecoverFromAmbush();

    private static final float RELEVANCE_WHEN_ACTIVE = 0.5f;

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.UNDER_FIRE_AT_LOS, false);

    private RecoverFromAmbush() {}

    @Override public String name() { return "RecoverFromAmbush"; }

    @Override
    public Priority priority() {
        return Priority.SURVIVAL;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        return state.get(Predicate.UNDER_FIRE_AT_LOS) ? RELEVANCE_WHEN_ACTIVE : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }
}
