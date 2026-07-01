/**
 * Feature domain (cross-actor) — presentation-authoring systems.
 *
 * <p>Category: feature domain (a cross-actor system; every live sheet-drawn
 *           unit routes through it).
 * <br>Charter:  systems that run in the sim tick but author <em>presentation</em>
 *           component data — {@code SPRITE} today ({@code FacingSystem}),
 *           {@code ANIMATION} in a later phase — that sim logic never reads;
 *           render tiers become pure collectors of it. The stateless
 *           derivation math ({@code LiveAppearance}) lives beside the system
 *           that calls it, the {@code battle.air.AirAppearance} precedent.
 * <br>Boundary: no {@code ops.battleview} / render-tier imports, no
 *           {@code SpriteAPI} — component data stays tier-neutral (sheet
 *           selectors + frame indices, never sprite handles). Facing here is
 *           <em>write-only</em> presentation, distinct from the real
 *           {@code facingDegrees} steer-state on air/vehicle bodies.
 *
 * <p>See {@code roadmap/ecs-migration/stories/live-appearance.md}.
 */
package com.dillon.starsectormarines.battle.appearance;
