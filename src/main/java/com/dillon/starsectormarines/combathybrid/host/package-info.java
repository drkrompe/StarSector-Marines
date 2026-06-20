/**
 * <b>Category:</b> cross-engine bridge — durable vanilla-combat session lifecycle.
 *
 * <p><b>Charter:</b> owns the per-session orchestration layer that wires the bridge
 * adapters into a running vanilla combat instance. {@code CombatBridgeSession} is the
 * thin orchestrator: given a {@code GroundBattleConfig} it composes the spectator-canvas
 * policy plugins ({@code S0CompletionPlugin}, {@code SpectatorCanvasPlugin}), sizes the
 * map, and on engine-ready installs the backdrop + proxy mirror — collapsing the
 * creation-plugin's two phases ({@code initBattle}/{@code afterDefinitionLoad}) to two
 * delegation calls. {@code PlayerFleetStash} handles the stash/restore handshake that
 * gives the spectator battle an empty deploy roster. The coupled sim is a live Conquest
 * battle that governs its own completion (no never-end pin).
 *
 * <p><b>Boundary (change guidance):</b> imports {@code bridge} types (config + adapters)
 * and {@code battle/} sim types; must not import {@code probe} (probe is a dev-only
 * caller). Session types are the long-lived production surface; probe types are
 * throwaway scaffolding and will be deleted when the mission-flow trigger lands.
 *
 * <p><b>Pointer:</b> {@code roadmap/vanilla-combat-bridge/production-architecture.md}
 * for the session lifecycle and the spectator-canvas design decisions.
 */
package com.dillon.starsectormarines.combathybrid.host;
