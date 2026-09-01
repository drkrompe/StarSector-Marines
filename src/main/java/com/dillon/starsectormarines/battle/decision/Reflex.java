package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * One interrupt in a unit's reflex chain — the ordered list of things that may
 * pre-empt the squad plan's current step for a single unit on a single tick.
 *
 * <p>A reflex sits between "this unit has a squad" and "execute the step it was
 * assigned". It is individual-tier: it answers something the unit itself is in
 * the middle of, or has just walked into, that the squad's plan cannot know
 * about — a committed aim animation, a friendly satchel about to go off
 * underfoot, a grenade already in the air, a fire team whose morale has broken.
 * It is <em>not</em> a place to invent work. A reflex that has nothing to answer
 * returns {@code false} and the next one is asked.
 *
 * <p><b>Order is the whole contract.</b> An earlier reflex pre-empts every later
 * one and the step, so the list a dispatcher declares is a statement about
 * priority: grenade evasion outranks a shot of opportunity, a broken fire team
 * outranks the step. That was previously an if-chain, which states the same law
 * in a form nobody can read, cite, or test. Each arm declares its chain once and
 * pins the order with a test; reordering it is a behaviour change to be
 * measured, not a tidy-up.
 *
 * <p>Stateless across ticks like {@link UnitBehavior}: per-unit state lives in
 * world components keyed by the unit's {@code long} entity id, so one instance
 * services every unit on the field.
 */
public interface Reflex {

    /** Stable identifier for the ordering test and the per-unit diagnostic. */
    String name();

    /**
     * Runs this reflex for one unit.
     *
     * @return true when the reflex consumed this tick for the unit: no later
     *         reflex and no plan step runs.
     */
    boolean interrupt(long unit, Squad squad, ReflexContext context, BattleControl sim);
}
