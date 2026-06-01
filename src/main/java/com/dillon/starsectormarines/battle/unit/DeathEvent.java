package com.dillon.starsectormarines.battle.unit;

/**
 * A unit's death, as it travels through the {@link DeathDispatcher} mailbox.
 * Published once per unit by the death cascade in
 * {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve} on
 * the {@code wasAlive → !isAlive} transition, then fanned out to the
 * subscribed handlers (turret/hub demolition, drone crash, render, future
 * medic) that decide how to represent the death.
 *
 * <p><b>Transitional shape.</b> The event currently carries the dead
 * {@link Unit} itself — at publish time the unit is still a live object (it is
 * released from the dense {@link UnitRegistry} immediately <em>after</em> the
 * publish, and the legacy roster list still retains it), so handlers can read
 * its type, cell, and back-links directly. As the
 * {@code retire-legacy-units-list} spine progresses and the corpse becomes a
 * lightweight body entity, this record grows toward a self-contained snapshot
 * (type, faction, cell, render pos, death-pose) so handlers no longer deref a
 * {@code registry == null} unit. Keep it minimal until a handler needs more.
 */
public record DeathEvent(Unit unit) {}
