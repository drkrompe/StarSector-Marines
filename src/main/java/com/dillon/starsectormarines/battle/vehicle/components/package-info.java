/**
 * ECS component payloads for the ground-vehicle domain — id-keyed motion/control state.
 *
 * <p><b>Category:</b> component data (plain POJOs stored in {@code EntityWorld} OBJECT columns),
 * per the {@code XxxComponent}-in-a-{@code components/}-subpackage convention.
 * <b>Charter:</b> hold the per-vehicle state that the stateless {@code battle.vehicle} systems
 * (e.g. {@code VehicleControlSystem}) read/write by entity id.
 * <b>Boundary:</b> pure data — no behavior, no sim/service refs; the {@code ComponentType}
 * registration lives in {@code BattleComponents} and the by-id accessors in
 * {@code battle.sim.ConvoyService}.
 * <b>Pointer:</b> {@code roadmap/ecs-migration/stories/vehicle-control-ecs.md}.
 */
package com.dillon.starsectormarines.battle.vehicle.components;
