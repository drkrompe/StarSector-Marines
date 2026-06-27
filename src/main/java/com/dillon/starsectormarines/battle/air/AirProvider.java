package com.dillon.starsectormarines.battle.air;

/**
 * Declares who owns the air layer for a {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}
 * — the marine drop-ships and fighter passes "above" the ground battle.
 *
 * <p>This is the host-selection seam of the vanilla-combat-bridge: a standalone
 * battle runs its own air; the combat-bridge host hands the air off to the real
 * vanilla ships flying in the {@code CombatEngineAPI} layer above the ground sim.
 *
 * <ul>
 *   <li><b>{@link #INTERNAL}</b> (default) — the sim's own {@code AirSystem} ticks
 *       {@link Shuttle}s through the deboard state machine, and flyby passes draw
 *       from the installed {@code FlybyRoster}. The standalone {@code BattleScreen}
 *       host. All {@code createX} factories and tests run in this mode.</li>
 *   <li><b>{@link #EXTERNAL}</b> — the host owns the air. The internal air tick is
 *       skipped, and installing internal air ({@code spawnShuttle} / {@code attachAirTurrets}
 *       / {@code setFlybyRoster}) is a contract violation that fails loud. External
 *       air-to-ground arrives as events: strafing via
 *       {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#applyExternalDamage},
 *       and (future, S3d) marine delivery via an EXTERNAL-asserting {@code deliverSquad}
 *       entry point — the inverse of the internal deboard.</li>
 * </ul>
 *
 * <p>The flag declares an invariant at the seam; it does not branch sim behavior
 * beyond gating the one air-tick call, keeping the sim "just the tick loop."
 */
public enum AirProvider {
    /** Sim owns + ticks its own shuttles and flyby passes. */
    INTERNAL,
    /** Host (real vanilla ships) owns the air; the sim runs no internal air. */
    EXTERNAL
}
