/**
 * Framework core — GOAP decision engine + tactical dispatch.
 *
 * <p>Category: framework core (mechanism; no single feature owner).
 * <br>Charter:  the GOAP engine ({@code goap/}: planner, goal, action,
 *           world-state, scoring), per-unit role&rarr;behavior dispatch
 *           ({@code UnitUpdateSystem}, {@code UnitBehavior}), the reflex-chain
 *           shape each arm declares its per-tick interrupts in
 *           ({@code Reflex}, {@code ReflexContext}, {@code ReflexChain}), the
 *           spatial attacker index, and tactical scoring + the tactical graph
 *           ({@code TacticalScoring}, {@code TacticalMap/Node/Linker}).
 * <br>Boundary: actor behaviors live in their domain packages
 *           ({@code infantry/}, {@code mech/}, {@code drone/},
 *           {@code turret/}), NOT here — the reflex <em>runner</em> is
 *           mechanism and lives here; each arm's reflex <em>list</em> is a
 *           priority statement about that actor and lives with it. Known exception: the dispatch
 *           wiring ({@code UnitUpdateSystem}, {@code TacticalScoring},
 *           {@code goap.world.WorldStateBuilder}) still names concrete
 *           feature behaviors — a deferred framework&rarr;feature edge.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy
 * and the dispatch-inversion follow-up.
 */
package com.dillon.starsectormarines.battle.decision;
