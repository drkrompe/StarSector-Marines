/**
 * Framework core — GOAP decision engine + tactical dispatch.
 *
 * <p>Category: framework core (mechanism; no single feature owner).
 * <br>Charter:  the GOAP engine ({@code goap/}: planner, goal, action,
 *           world-state, scoring), per-unit role&rarr;behavior dispatch
 *           ({@code UnitUpdateSystem}, {@code UnitBehavior}), the spatial
 *           attacker index, and tactical scoring + the tactical graph
 *           ({@code TacticalScoring}, {@code TacticalMap/Node/Linker}).
 * <br>Boundary: actor behaviors live in their domain packages
 *           ({@code infantry/}, {@code mech/}, {@code drone/},
 *           {@code turret/}), NOT here. Known exception: the dispatch
 *           wiring ({@code UnitUpdateSystem}, {@code TacticalScoring},
 *           {@code goap.world.WorldStateBuilder}) still names concrete
 *           feature behaviors — a deferred framework&rarr;feature edge.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy
 * and the dispatch-inversion follow-up.
 */
package com.dillon.starsectormarines.battle.decision;
