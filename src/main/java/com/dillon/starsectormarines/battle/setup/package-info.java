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
 *           known future work (out of scope of the reorg) — see
 *           {@code overview.md}.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.setup;
