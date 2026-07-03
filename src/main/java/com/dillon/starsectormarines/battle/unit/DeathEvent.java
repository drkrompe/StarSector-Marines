package com.dillon.starsectormarines.battle.unit;

/**
 * A unit's death, as it travels through the {@link DeathDispatcher} mailbox.
 * Published once per unit by the death cascade in
 * {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve} on
 * the {@code wasAlive → !isAlive} transition, then fanned out to the
 * subscribed handlers (turret/hub demolition, drone crash, mech wreck,
 * dead-body/render) that decide how to represent the death.
 *
 * <p><b>Self-contained snapshot, growing.</b> The event carries the dead unit's
 * {@code long} {@link #unitId} plus a snapshot of the moment-of-death state its
 * handlers read. Handlers classify the dead unit <em>by id</em>
 * ({@code identity().type(id).isTurret()} / {@code isDroneHub()} / {@code isDrone()},
 * or a {@code mechLoadout(id) != null} probe) rather than off a held object
 * subtype. The snapshot is captured at publish time, while the unit is still live
 * and registered — the unit is released from the dense {@link UnitRosterService}
 * immediately <em>after</em> the publish, after which its Group-C accessors are
 * fail-loud. So handlers must read the event's snapshot, <b>not</b> the unit's
 * live accessors, for any post-release value; identity + render position + the
 * kept corpse columns survive the release (they ride the corpse transmute) and
 * stay readable by id.
 *
 * <p>{@link #cellX} / {@link #cellY} are the logical death cell — where the
 * demolition systems flip rubble and the wreck systems drop a smoking wreck.
 * {@link #deathPoseIdx} is the random prone-pose frame the dead-body handler
 * ({@code DeadBodySystem}) authors into the corpse's {@code SPRITE.index} for
 * battlefield variety; {@code -1} = no ground corpse (a cascade-killed drone that
 * crashes-and-fades instead). As the corpse grows into a fuller body entity this
 * record grows with it (hp, render pos) only when a handler actually needs the
 * field. Keep it minimal.
 */
public record DeathEvent(long unitId, int cellX, int cellY, int deathPoseIdx) {}
