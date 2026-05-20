package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Preemption gate that role behaviors consult at the top of their update
 * pass. Encodes the design rule that lower tiers can override higher-tier
 * intent based on emergent local reality (individual ↔ squad ↔ command).
 *
 * <p><b>Current preempts:</b>
 * <ul>
 *   <li>{@link Result#DELEGATE_TO_GOAP} — squad's morale is broken. The
 *       squad-level GOAP planner has picked {@code SurviveContact} and
 *       {@code currentPlan} is the {@code BreakContact} step; role
 *       behaviors that don't normally run the plan (Garrison, Patrol,
 *       etc.) need to delegate so retreat actually happens.</li>
 * </ul>
 *
 * <p><b>Future preempts</b> likely to land here: cohesion broken (member
 * too far from squad centroid, needs to regroup before honoring squad
 * intent), kit-retrieval needed (out of ammo / first-aid, individual
 * priority overrides squad), suppressed (member pinned and can't move
 * even if squad wants them to). Each new predicate adds a {@code Result}
 * value or a fan-out to a sub-action; the role behaviors stay
 * thin and don't need to know which predicate fired.
 *
 * <p>See {@code memory/tier_override_design.md} for the architectural
 * intent and {@code memory/role_behavior_goap_dispatch.md} for the
 * dispatch trap this helper exists to prevent.
 */
public final class TierOverride {

    /**
     * What the role behavior should do for this tick. {@link #NONE} means
     * carry out the role's normal logic; any other value means stop and
     * yield to the higher-priority handler indicated.
     */
    public enum Result {
        /** No preempt — run the role behavior's normal pass. */
        NONE,
        /**
         * Defer to {@link CombatantBehavior}, which routes to
         * {@code GoapInfantryBehavior.update} and executes whatever step
         * the squad's {@code currentPlan} sits on. Used today for the
         * morale-broken → BreakContact route on Garrison/Patrol units.
         */
        DELEGATE_TO_GOAP
    }

    private TierOverride() {}

    /**
     * Returns the preempt verdict for {@code u}. Callers act on the
     * non-{@link Result#NONE} branches and short-circuit their own
     * dispatch when one fires; for {@link Result#NONE} they fall through
     * to the role's normal logic. Cheap — just a fixed set of predicate
     * reads on the squad — so calling it at the top of every role-tick
     * is free.
     */
    public static Result check(Unit u, Squad squad, BattleSimulation sim) {
        if (squad == null) return Result.NONE;
        if (squad.moraleBroken) return Result.DELEGATE_TO_GOAP;
        return Result.NONE;
    }
}
