/**
 * <b>Category:</b> cross-engine bridge — {@code @DebugOnly} dev trigger (throwaway).
 *
 * <p><b>Charter:</b> the S0 combat-bridge probe: hotkey listener
 * ({@code CombatHybridInputListener}), campaign-plugin router
 * ({@code CombatHybridCampaignPlugin}), roster-builder + launch entry point
 * ({@code S0BattleProbe}), and minimal battle definition
 * ({@code S0BattleCreationPlugin}). Together they arm a {@code startBattle} call from
 * the campaign map, tag the synthetic enemy fleet so the router recognises the probe,
 * build the {@code GroundBattleConfig}, and delegate both creation-plugin phases to
 * {@code CombatBridgeSession}. All four classes carry {@code @DebugOnly} and are gated
 * by {@code DevConfig.S0_COMBAT_PROBE}; the entire package is deletable once the
 * mission-flow trigger (the production entrypoint) lands.
 *
 * <p><b>Boundary (change guidance):</b> allowed to import {@code host} and
 * {@code bridge} types (it drives them). Nothing in {@code host} or {@code bridge}
 * imports {@code probe} — the dependency arrow is strictly probe → host → bridge.
 * No new durable logic should be added here; durable additions belong in {@code host}
 * or {@code bridge}.
 *
 * <p><b>Pointer:</b> {@code roadmap/vanilla-combat-bridge/} for the overall design;
 * {@code stories/s0-battle-bootstrap.md} for the probe's specific verified facts and
 * acceptance criteria.
 */
package com.dillon.starsectormarines.combathybrid.probe;
