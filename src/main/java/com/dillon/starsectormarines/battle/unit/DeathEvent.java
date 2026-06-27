package com.dillon.starsectormarines.battle.unit;

/**
 * A unit's death, as it travels through the {@link DeathDispatcher} mailbox.
 * Published once per unit by the death cascade in
 * {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve} on
 * the {@code wasAlive → !isAlive} transition, then fanned out to the
 * subscribed handlers (turret/hub demolition, drone crash, render, future
 * medic) that decide how to represent the death.
 *
 * <p><b>Self-contained snapshot, growing.</b> The event carries the dead
 * {@link Entity} for handlers that need its concrete subtype ({@code instanceof}
 * for the drone crash / turret + hub demolition / mech wreck), plus a snapshot
 * of the moment-of-death state those handlers read. The snapshot is captured at
 * publish time, while the unit is still live and registered — the unit is
 * released from the dense {@link UnitRosterService} immediately <em>after</em> the
 * publish, after which its Group-C accessors are fail-loud (the {@code local*}
 * shadow for those columns is gone). So handlers must read the event's snapshot,
 * <b>not</b> the unit's accessors, for any post-release value.
 *
 * <p>{@link #cellX} / {@link #cellY} are the logical death cell — where the
 * demolition systems flip rubble and the wreck systems drop a smoking wreck.
 * As the corpse grows into a fuller body entity this record grows with it (hp,
 * render pos) only when a handler actually needs the field. Keep it minimal.
 */
public record DeathEvent(Entity unit, int cellX, int cellY) {}
