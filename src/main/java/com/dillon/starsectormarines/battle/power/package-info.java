/**
 * Feature domain (player-facing) — Command Powers, the player's agency layer
 * inside the battle sim.
 *
 * <p>Category: feature domain (player-invoked battlefield abilities).
 * <br>Charter:  the in-battle activation economy and power lifecycle —
 *           the {@code CommandPower} abstraction + concrete powers
 *           (physical delivery, air support and fire support), the player
 *           command-point pool and per-power cooldowns
 *           ({@code CommandPowerService}), and the stateless
 *           {@code CommandPowerSystem} that drains queued activations, regens
 *           command points, and ages cooldowns / transient effects.
 * <br>Boundary: this package owns only the <em>activation economy + lifecycle</em>.
 *           The pre-battle fleet&rarr;available-powers resolver and loadout
 *           slotting live in the campaign tier; the {@code TARGETING}
 *           UI state lives in the view layer ({@code ops.BattleScreen}). The sim only
 *           ever sees {@code COMMITTED -> COOLDOWN}.
 *
 * <p>See {@code command-powers-nouns.md} for the standing feature model.
 */
package com.dillon.starsectormarines.battle.power;
