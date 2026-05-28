/**
 * Framework core — tick performance instrumentation.
 *
 * <p>Category: framework core (diagnostics; no single feature owner).
 * <br>Charter:  per-tick and inner-phase timing ({@code TickProfile},
 *           {@code TickInnerProfile}) for the sim hot loop.
 * <br>Boundary: diagnostics only — nothing here is load-bearing for
 *           gameplay correctness; don't hang gameplay state off it.
 *           (The line-of-sight cache {@code LosCache} moved to
 *           {@code nav/} — it was never a profiler, only shared this
 *           package's per-tick static-slot lifecycle.)
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.profile;
