/**
 * <b>Category:</b> cross-engine bridge (Starsector {@code CombatEngineAPI}-facing).
 *
 * <p><b>Charter:</b> bridges the headless ground battle sim ({@code battle/}) and
 * vanilla's real-time {@code CombatEngineAPI}. Owns the battle-creation plugin,
 * proxy/avatar entities, and per-frame combat plugins that let sim state and
 * vanilla combat interact. See {@code roadmap/vanilla-combat-bridge/}.
 *
 * <p><b>Boundary (change guidance):</b> the dependency arrow points <em>one way</em>
 * — {@code combathybrid} → {@code battle} (reads sim state, slaves proxies, drains
 * damage back through the sim's external-damage path). {@code battle/} must never
 * import {@code combathybrid}: that would re-couple the headless sim to the combat
 * engine and undo the decoupling the whole project rests on. Code here is the only
 * place in the mod allowed to import {@code CombatEngineAPI} / {@code ShipAPI} /
 * {@code EveryFrameCombatPlugin} alongside sim types.
 *
 * <p><b>Current contents — S0 battle-bootstrap probe</b> (dev-gated, throwaway):
 * proves two load-bearing capabilities before any real feature investment —
 * (1) launching a vanilla combat instance from the campaign with a chosen subset
 * of the player fleet, and (2) the mod owning when that battle is considered
 * complete (suppress vanilla's auto-end; we call {@code endCombat} on our terms).
 * See {@code roadmap/vanilla-combat-bridge/stories/s0-battle-bootstrap.md}.
 *
 * <p><b>Pointer:</b> {@code roadmap/vanilla-combat-bridge/overview.md} for the
 * concept, verified API facts, and the S1 (wall-clamp) / S2 (proxy-target) probes
 * that build on S0.
 */
package com.dillon.starsectormarines.combathybrid;
