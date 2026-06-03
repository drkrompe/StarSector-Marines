/**
 * <b>Category:</b> cross-engine bridge — durable, host-agnostic sim⇄vanilla coupling.
 *
 * <p><b>Charter:</b> owns the three types that translate between the headless ground sim
 * ({@code battle/}) and a vanilla {@code CombatEngineAPI} instance:
 * {@code GroundBattleConfig} (immutable snapshot of everything the bridge needs — sim,
 * grid dimensions, scene layers, targetable entities, proxy variant, damage scale),
 * {@code GroundSceneBackdrop} (render sink — draws the ground scene under vanilla ships via
 * a world-unit {@code BattleCamera}), and {@code SimProxyMirror} (proxy/damage/death
 * coupling — one invisible proxy per targetable entity, event-translated in both
 * directions). One config drives one session; the bridge types hold no session lifecycle
 * of their own.
 *
 * <p><b>Boundary (change guidance):</b> the dependency arrow points one way —
 * {@code combathybrid.*} → {@code battle/} (reads sim state, slaves proxies, drains
 * damage back through the sim's external-damage path). {@code battle/} must never import
 * {@code combathybrid}: that would re-couple the headless sim to the combat engine.
 * Types here may import {@code CombatEngineAPI} / {@code ShipAPI} /
 * {@code CombatLayeredRenderingPlugin} alongside sim types; nothing outside
 * {@code combathybrid} may.
 *
 * <p><b>Pointer:</b> {@code roadmap/vanilla-combat-bridge/production-architecture.md}
 * for the architectural decisions and the S3-series render-layer stories that grow
 * {@code GroundBattleConfig#sceneLayers}.
 */
package com.dillon.starsectormarines.combathybrid.bridge;
