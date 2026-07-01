/**
 * Feature domain (cross-actor) — the fire&rarr;hit&rarr;damage&rarr;fx pipeline.
 *
 * <p>Category: feature domain (a cross-actor system; every armed unit
 *           routes through it).
 * <br>Charter:  shot emission + raycast ({@code ShotService},
 *           {@code ShotRaycast}, {@code ShotEvent}, {@code Projectile}),
 *           damage resolution ({@code DamageService},
 *           {@code DamageResolver}, {@code HitResponseSystem}),
 *           detonations, the shared chassis-weapon firing mechanism
 *           ({@code HeavyWeapons}), the fire-intent execution system
 *           ({@code FiringSystem} — consumes the {@code COMBAT} fire
 *           intent behaviors queue), range/stance rules, and the single
 *           visual-effects sink ({@code fx/EffectsService}).
 * <br>Boundary: actor-specific weapon <em>config</em> lives in the actor
 *           domains ({@code Marine*} in {@code infantry/}, {@code Mech*}
 *           in {@code mech/}); {@code combat/} owns the shared mechanism
 *           only.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.combat;
