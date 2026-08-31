/**
 * Feature domain (cross-actor) — the fire&rarr;hit&rarr;damage&rarr;fx pipeline.
 *
 * <p>Category: feature domain (a cross-actor system; every armed unit
 *           routes through it).
 * <br>Charter:  shot emission + raycast ({@code ShotService},
 *           {@code BallisticResolver}, {@code ShotEvent}, {@code Projectile}),
 *           damage resolution ({@code DamageService},
 *           {@code DamageResolver} for a roster unit and
 *           {@code BodyDamageResolver} for any carried body,
 *           {@code HitResponseSystem}),
 *           the durability law and the screens that resolve inside it
 *           ({@code DurabilityModel}, {@code MitigationService},
 *           {@code MitigationSystem}),
 *           detonations, the shared chassis-weapon firing mechanism
 *           ({@code HeavyWeapons}), the engagement relation
 *           ({@code EngagementService} — can this shooter reach that body
 *           right now, including the per-weapon altitude capability),
 *           the fire-intent execution system
 *           ({@code FiringSystem} — consumes the {@code COMBAT} fire-intent
 *           queue the behaviors write), range/stance rules, and the single
 *           visual-effects sink ({@code fx/EffectsService}).
 * <br>Boundary: actor-specific weapon <em>config</em> lives in the actor
 *           domains ({@code Marine*} in {@code infantry/}, {@code Mech*}
 *           in {@code mech/}); {@code combat/} owns the shared mechanism
 *           only. Nothing here branches on what is carrying a target:
 *           ballistics, splash and the damage route all go through
 *           {@code BodyService}, and what dying means is the carrier's.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.combat;
