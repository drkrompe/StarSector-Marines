/**
 * Feature domain (player-facing) — Command Powers, the player's agency layer
 * inside the battle sim.
 *
 * <p>Category: feature domain (player-invoked battlefield abilities).
 * <br>Charter:  the in-battle activation economy and power lifecycle —
 *           the {@code CommandPower} abstraction + concrete powers
 *           ({@code ReconPing}), the player command-point pool and per-power
 *           cooldowns ({@code CommandPowerService}), and the stateless
 *           {@code CommandPowerSystem} that drains queued activations, regens
 *           command points, and ages cooldowns / transient effects.
 * <br>Boundary: this package owns only the <em>activation economy + lifecycle</em>.
 *           The pre-battle fleet&rarr;available-powers resolver and loadout
 *           slotting live in the campaign tier (roadmap S2); the {@code TARGETING}
 *           UI state and the projection of active effects into other subsystems
 *           (e.g. pushing {@code ActivePing}s as ephemeral vision sources) live
 *           in the view layer ({@code ops.BattleScreen}) and the consuming
 *           service ({@code vision.VisionService}) respectively. The sim only
 *           ever sees {@code COMMITTED -> COOLDOWN}.
 *
 * <p>See {@code roadmap/command-powers/} for the design track; S1
 * ({@code stories/s1-power-framework-skeleton.md}) is the slice this package
 * implements.
 */
package com.dillon.starsectormarines.battle.power;
