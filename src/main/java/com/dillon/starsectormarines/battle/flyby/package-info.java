/**
 * Standalone feature domain — the fighter flyby atmosphere layer.
 *
 * <p>Category: feature domain (presentation + light sim hybrid).
 * <br>Charter:  scripted vanilla-fighter flyovers — its own GL renderer
 *           ({@code FlybyOverlay}), the roster/profile data model
 *           ({@code FlybyRoster}, {@code PlayerFleetWings},
 *           {@code FighterWing}, {@code FighterProfile},
 *           {@code WeaponClass}), heading-based weave + strafing runs,
 *           and audio.
 * <br>Boundary: standalone — NOT under {@code ui/}, because it carries
 *           data/roster state and a sim coupling
 *           ({@code BattleSimulation#applyExternalDamage}), not just
 *           presentation. Forward-looking: when fighters are rebuilt as
 *           real flying entities on {@code air/AirBody} (spawn on/off map,
 *           land at bases, be shot down), this folds into {@code air/} —
 *           deferred until it shares that code, not before (see
 *           {@code roadmap/backlog.md}).
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.flyby;
