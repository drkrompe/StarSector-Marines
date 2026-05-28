/**
 * Framework core — the tick orchestrator.
 *
 * <p>Category: framework core (mechanism; no single feature owner).
 * <br>Charter:  {@code BattleSimulation} owns tick order and the deferred
 *           mutation queues ({@code PendingOccupancyDelta},
 *           {@code PendingTargetMutation}) that let parallel per-unit
 *           passes stage changes without racing.
 * <br>Boundary: north star is "sim = just the tick loop." State lives in
 *           Services, per-tick logic in stateless Systems. When adding
 *           work: new persistent state &rarr; a Service (its own domain
 *           package); new per-tick pass &rarr; a System wired into
 *           {@code BattleSimulation}'s tick order. Do NOT grow feature
 *           state or behavior onto the orchestrator itself.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} and
 * {@code roadmap/ecs-migration/overview.md} for the Services/Systems model.
 */
package com.dillon.starsectormarines.battle.sim;
