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
 * pool for, rather than adjusting his firing position on the way out. {@code InfantryReflexOrderTest} states that list literally; changing
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
     * Weapon cooldowns and reloads advance, so they drain during move and cohere
     * just as they do during fire, and a marine idle with a part-spent magazine
     * tops it up.
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
            InfantryUnitPrep.topUpMagazineWhenIdle(unit, sim);
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
     * Get out of the fire's way: out of a burning cell, or off fuel near one.
     * Below the grenade, which kills in an instant where fire takes seconds, and
     * above everything else: a marine who stays in the fire to shoot, treat or
     * rejoin burns doing it.
     *
     * @see FireStepOut for where he goes
     */
    public static final Reflex IN_FIRE = new Reflex() {
        @Override public String name() { return "IN_FIRE"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return FireStepOut.stepOut(unit, sim);
        }
    };

    /**
     * Get out of a Bombard's spore haze, unless a Rav is in melee reach. Below
     * the fire, which burns where the haze only stings, and above everything
     * else: a marine who stays in it to shoot shoots badly and is stung for it.
     *
     * @see HazeStepOut for where he goes
     */
    public static final Reflex IN_HAZE = new Reflex() {
        @Override public String name() { return "IN_HAZE"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return HazeStepOut.stepOut(unit, sim);
        }
    };

    /**
     * A marine who landed after his squad stepped off closes on it, and does
     * not start a fight of his own on the way.
     *
     * <p><b>Ranked here on purpose.</b> Everything above it is a hazard already
     * in the air — a friendly satchel, a grenade — and a marine crossing open
     * ground alone still has to get out of the way of those. Everything below
     * it initiates contact: the special-equipment shots, the hardened-target
     * rocket, the onset screen. A lone late arrival with a rocket tube and a
     * turret in view is precisely the marine this outranks. It also outranks
     * the broken-fire-team peel, which would otherwise send him to cover
     * somewhere out on his own rather than back to the squad whose morale he
     * is being counted against.
     *
     * <p>Return fire is not initiation and is not withheld: the rejoin itself
     * authors a shot at whatever is already in range with a clear shot.
     *
     * @see SquadRejoin for what the state is and when it retires
     */
    public static final Reflex REJOIN = new Reflex() {
        @Override public String name() { return "REJOIN"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return SquadRejoin.rejoin(unit, squad, sim);
        }
    };

    /**
     * A squad working a mortar tube: its crew halts and sets the tube down and the rest of the squad stays leashed to it,
     * for the section's window.
     *
     * <p><b>Ranked here</b> for the reason {@link #REJOIN} is: below the hazards already in the air and the marine still
     * closing on the squad, and above everything that would start something or walk him somewhere else, because a leash
     * that a shot of opportunity or the step could walk a marine out of is not one. A broken fire team is let go by the
     * reflex itself, so the morale law still comes first.
     *
     * @see MortarTactics#serveSection for what the section asks of each marine
     */
    public static final Reflex MORTAR_SECTION = new Reflex() {
        @Override public String name() { return "MORTAR_SECTION"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return MortarTactics.serveSection(unit, squad, sim);
        }
    };

    /**
     * A squad medic walking to a patient or treating one, keeping at the
     * patient's side as the squad moves on.
     *
     * <p><b>Ranked here</b> for the reason {@link #MORTAR_SECTION} is: below the
     * hazards already in the air and the marine still closing on the squad, and
     * above everything that would start something. A medic with hands on a
     * patient throwing a grenade is the thing this rank prevents; the medic holds
     * fire for the whole treatment. A broken fire team is let go by the reflex
     * itself, so the morale law still comes first.
     *
     * @see SquadMedicSystem#serve for what the medic and patient each do
     */
    public static final Reflex TREATMENT = new Reflex() {
        @Override public String name() { return "TREATMENT"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return SquadMedicSystem.serve(unit, squad, sim);
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

    /**
     * A marine with one of his own in his firing lane moves a cell rather than
     * shooting through them or picking somebody else to shoot at.
     *
     * <p><b>It never interrupts</b>, in the way {@link #COOLDOWNS} never
     * interrupts: it authors a path and declines, so the marine's assigned step
     * still runs, still shoots, and is what walks him along it. A step-aside
     * that consumed the tick was measured and cost compounds — see
     * {@link LaneSidestep} — because a marine who stops shooting for a second
     * and a half to move a cell is a marine spending the one thing a timed
     * advance cannot spare.
     *
     * <p>Its rank still decides something even so. Last, and below
     * {@link #BROKEN_FIRE_TEAM} on purpose: a marine whose fire team has broken
     * is leaving rather than adjusting his firing position, and that reflex
     * consumes the tick, so a peeling marine is never handed a sidestep to
     * carry out on his way. Everything above it is about a shot or a hazard
     * already in flight, and none of that should have a lateral step authored
     * underneath it.
     *
     * @see LaneSidestep for what it will and will not trade to clear the lane
     */
    public static final Reflex LANE_SIDESTEP = new Reflex() {
        @Override public String name() { return "LANE_SIDESTEP"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return LaneSidestep.stepOutOfLane(unit, sim);
        }
    };

    /**
     * The marine's reflex chain, highest priority first. Two entries —
     * {@link #COOLDOWNS} and {@link #LANE_SIDESTEP} — never consume a tick;
     * they are in the list because the list is where a reader looks to find out
     * what happens to a marine before his step runs, and because their rank
     * still says which interrupts pre-empt them.
     */
    public static final List<Reflex> CHAIN = List.of(
            COMMITTED_AIM,
            COOLDOWNS,
            FRIENDLY_CHARGE,
            KNOWN_GRENADE,
            IN_FIRE,
            IN_HAZE,
            REJOIN,
            MORTAR_SECTION,
            TREATMENT,
            OPPORTUNITY_SPECIAL,
            HARDENED_OPPORTUNITY,
            BROKEN_FIRE_TEAM,
            LANE_SIDESTEP);

    /**
     * The chain up to but excluding {@link #BROKEN_FIRE_TEAM} — the prefix that
     * is askable of a lone marine, every entry in it declining rather than
     * failing when there is no squad to read.
     * {@code GoapInfantryBehavior.prepareForAction} is its one caller.
     *
     * <p>{@link #LANE_SIDESTEP} is outside it too, and not because it wants a
     * squad. Its one caller is a coordinated action that owns where the marine
     * is standing for the length of a bound; a reflex that walks him two cells
     * to clear a lane would be a second author of the same movement, and the
     * bound is the one that knows where the marine is supposed to end up.
     * Written as an index rather than {@code size() - 1} so appending the next
     * reflex cannot silently readmit the broken-fire-team peel here.
     */
    static final List<Reflex> PREPARATION_CHAIN =
            CHAIN.subList(0, CHAIN.indexOf(BROKEN_FIRE_TEAM));
}
