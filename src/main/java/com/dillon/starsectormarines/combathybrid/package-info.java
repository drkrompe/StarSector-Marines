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
 * <p><b>Sub-packages</b> (dependency arrow strictly {@code probe → host → bridge}):
 * <ul>
 *   <li>{@code bridge} — durable, host-agnostic sim⇄vanilla coupling: the render sink
 *       ({@code GroundSceneBackdrop}), the proxy/damage/death mirror ({@code SimProxyMirror}),
 *       and the immutable battle snapshot ({@code GroundBattleConfig}).</li>
 *   <li>{@code host} — durable vanilla-combat session lifecycle: {@code CombatBridgeSession}
 *       orchestrates spectator-canvas + completion + camera/fleet-stash policy and installs
 *       the bridge adapters.</li>
 *   <li>{@code probe} — {@code @DebugOnly} dev trigger (hotkeys, tag-armed creation-plugin
 *       pick, roster building); deletable once the mission-flow trigger lands.</li>
 * </ul>
 *
 * <p><b>Pointer:</b> {@code roadmap/vanilla-combat-bridge/production-architecture.md} for the
 * code structure and {@code overview.md}/{@code architecture.md} for the concept + verified
 * API facts.
 */
package com.dillon.starsectormarines.combathybrid;
