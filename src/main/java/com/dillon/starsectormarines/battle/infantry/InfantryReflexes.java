package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.Reflex;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.decision.goap.action.BreakContact;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/**
 * The marine's reflex chain — everything that may pre-empt the squad plan's
 * current step for one marine on one tick, declared once and in priority order.
 *
 * <p>This list was an if-chain inside {@code GoapInfantryBehavior.update}. The
 * behaviour is unchanged; what is new is that the order is now something a
 * reader can see, a test can pin, and the next individual-tier behaviour can be
 * inserted into at the rank its priority earns rather than by adding another
 * branch above the loop.
 *
 * <p><b>The order is the law.</b> Each entry pre-empts every entry after it and
 * the step itself: a committed aim finishes before anything else is considered,
 * grenade evasion outranks a shot of opportunity, and a marine whose fire team
 * has broken peels to cover instead of executing a step he is no longer in the
 * pool for. {@code InfantryReflexOrderTest} states that list literally; changing
 * it is a behaviour change to be measured, not a tidy-up.
 *
 * @see com.dillon.starsectormarines.battle.mech.MechReflexes the same shape for a lance
 */
public final class InfantryReflexes {

    private InfantryReflexes() {}

    /**
     * A committed special-equipment aim runs to its end. This is a shot's
     * lifecycle rather than a fresh tactical choice, so it outranks everything —
     * including the plan flipping off the posture that started it, which would
     * otherwise leave the marine stuck mid-animation.
     */
    public static final Reflex COMMITTED_AIM = new Reflex() {
        @Override public String name() { return "COMMITTED_AIM"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return InfantryUnitPrep.tickAimAndShortCircuit(unit, sim);
        }
    };

    /**
     * Weapon cooldowns advance, so they drain during move and cohere just as
     * they do during fire.
     *
     * <p>Housekeeping rather than an interrupt — it always returns {@code false}
     * and never consumes the tick. It is an entry rather than a preamble because
     * this is the position it occupies today: after a committed aim, before any
     * evasion. A marine locked in aim does not tick cooldowns, and one who
     * evades this tick does. Moving it would be a behaviour change, so it sits
     * in the list where the behaviour actually puts it.
     */
    public static final Reflex COOLDOWNS = new Reflex() {
        @Override public String name() { return "COOLDOWNS"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            InfantryUnitPrep.tickCooldowns(unit, sim.world());
            return false;
        }
    };

    /** Clear the blast of a squadmate's satchel charge before doing anything else useful. */
    public static final Reflex FRIENDLY_CHARGE = new Reflex() {
        @Override public String name() { return "FRIENDLY_CHARGE"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return SatchelTactics.evadeFriendlyCharge(unit, sim);
        }
    };

    /**
     * Get out of the radius of a grenade already in the air. Above the
     * opportunity shots on purpose: a marine who takes the shot instead is a
     * marine who dies holding it.
     */
    public static final Reflex KNOWN_GRENADE = new Reflex() {
        @Override public String name() { return "KNOWN_GRENADE"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return FragGrenadeTactics.evadeKnownGrenade(unit, sim);
        }
    };

    /**
     * The general special-equipment shot of opportunity — satchel, frag,
     * deployable, close contact — available only while the assigned step
     * tolerates it. Each of these spends a squad resource or freezes the carrier
     * mid-bound, which is why a move-only coordinated role withholds them.
     */
    public static final Reflex OPPORTUNITY_SPECIAL = new Reflex() {
        @Override public String name() { return "OPPORTUNITY_SPECIAL"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return context.opportunityFirePermitted()
                    && InfantryUnitPrep.tryOpportunitySpecial(unit, sim);
        }
    };

    /**
     * The narrowing rather than the silencing of the entry above: an advancing
     * squad still answers an emplacement with the one weapon that hurts it. A
     * turret in rocket range is the reason the advance is in trouble, not a
     * distraction from it.
     */
    public static final Reflex HARDENED_OPPORTUNITY = new Reflex() {
        @Override public String name() { return "HARDENED_OPPORTUNITY"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return !context.opportunityFirePermitted()
                    && InfantryUnitPrep.tryHardenedOpportunity(unit, sim);
        }
    };

    /**
     * The other half of that narrowing: a marine who has just walked into
     * somebody may still raise a screen on the bearing they arrived on, even
     * while the step is withholding the general special path.
     */
    public static final Reflex ONSET_SCREEN = new Reflex() {
        @Override public String name() { return "ONSET_SCREEN"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return !context.opportunityFirePermitted()
                    && InfantryUnitPrep.tryOnsetScreen(unit, sim);
        }
    };

    /**
     * Fire-team morale override — the tier between the individual and the squad
     * plan. Cohesion breaks at fire-team granularity, so a marine whose team has
     * broken pulls back to cover on his team's own account while his composed
     * siblings keep executing the squad's plan. The squad is not the thing that
     * breaks.
     *
     * <p>No plan bookkeeping here on purpose: {@link BreakContact} runs
     * perpetually and the peeled team is excluded from role assignment, so it
     * cannot advance or fail a step it was never assigned. When the team's
     * morale clears the hysteresis, the replan that fires on the flip puts these
     * marines back in the slot pool and this reflex stops catching them.
     */
    public static final Reflex BROKEN_FIRE_TEAM = new Reflex() {
        @Override public String name() { return "BROKEN_FIRE_TEAM"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            if (!squad.fireTeamBroken(sim.squad().fireTeamIndex(unit))) return false;
            BreakContact.INSTANCE.execute(unit, squad, sim);
            return true;
        }
    };

    /** The marine's reflex chain, highest priority first. */
    public static final List<Reflex> CHAIN = List.of(
            COMMITTED_AIM,
            COOLDOWNS,
            FRIENDLY_CHARGE,
            KNOWN_GRENADE,
            OPPORTUNITY_SPECIAL,
            HARDENED_OPPORTUNITY,
            ONSET_SCREEN,
            BROKEN_FIRE_TEAM);

    /**
     * The chain up to but excluding {@link #BROKEN_FIRE_TEAM} — the prefix that
     * needs no squad state and is therefore askable of a lone marine.
     * {@code GoapInfantryBehavior.prepareForAction} is its one caller.
     */
    static final List<Reflex> PREPARATION_CHAIN = CHAIN.subList(0, CHAIN.size() - 1);
}
