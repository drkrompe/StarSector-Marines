/**
 * Framework core — the entity registry + data substrate.
 *
 * <p>Category: framework core (the shared entity store; no single feature
 *           owner).
 * <br>Charter:  {@code Unit} (the entity), {@code UnitRegistry} + SoA
 *           storage, roster services ({@code UnitRosterService},
 *           {@code FactionUnitRoster}), the spatial indices
 *           ({@code UnitSpatialIndex}, {@code UnitDestinationSpatialIndex}),
 *           and the shared enums ({@code Faction}, {@code UnitRole},
 *           {@code UnitType}).
 * <br>Boundary: data substrate only — behaviors live in the actor domains
 *           ({@code infantry/}, {@code mech/}, ...), not here. For
 *           proximity, use a spatial index's {@code gather()}; never walk
 *           {@code getUnits()}. Mid-migration: {@code Unit} is a fat POJO
 *           being gutted into SoA arrays and is not yet an ECS id — which
 *           is why this package is still {@code unit/}, not {@code entity/}
 *           (rename deferred until the type has <em>become</em> an id).
 *           Field-lifecycle docs on {@code Unit}/{@code UnitRegistry} are
 *           mandated, not optional.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} and
 * {@code roadmap/ecs-migration/overview.md}.
 */
package com.dillon.starsectormarines.battle.unit;
