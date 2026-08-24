/**
 * Framework core (adjacent) — pre-battle construction.
 *
 * <p>Category: framework core (one-shot wiring; no single feature owner).
 * <br>Charter:  {@code BattleSetup} builds the initial sim — map, rosters,
 *           and unit placement; {@code DefenderRoster} is its private
 *           roster-assembly helper.
 * <br>Boundary: one-shot setup only. Per-tick logic belongs in
 *           {@code sim/} + its Systems, never here. {@code BattleSetup}
 *           is large and mixes mission-specific wiring; decomposing it is
 *           known future work, outside the shipped reorganization.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.setup;
