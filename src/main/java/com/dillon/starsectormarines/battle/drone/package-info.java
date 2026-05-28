/**
 * Actor domain — the drone swarm.
 *
 * <p>Category: actor domain (entity + behavior/GOAP + lifecycle).
 * <br>Charter:  the drone GOAP composer ({@code GoapDroneBehavior}) +
 *           {@code DefendHubGoal} + {@code DroneSwarmAction}, the hub unit
 *           and its behavior ({@code DroneHubUnit},
 *           {@code DroneHubBehavior}), spawning ({@code DroneSpawner}),
 *           and the crash/demolition systems ({@code DroneCrashSystem},
 *           {@code HubDemolitionSystem}).
 * <br>Boundary: drone GOAP + lifecycle here; the shared planner/engine is
 *           {@code decision/goap}. A new drone goal/action wires into
 *           {@code GoapDroneBehavior}.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.drone;
