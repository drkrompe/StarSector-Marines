/**
 * Framework core — the entity registry + data substrate.
 *
 * <p>Category: framework core (the shared entity store; no single feature
 *           owner).
 * <br>Charter:  the dense SoA roster + id-mint ({@code UnitRosterService}) and
 *           the faction roster ({@code FactionUnitRoster}), the spatial indices
 *           ({@code UnitSpatialIndex}, {@code UnitDestinationSpatialIndex}), the
 *           construction spec ({@code EntitySpec}), and the shared enums
 *           ({@code Faction}, {@code UnitRole}, {@code UnitType}).
 * <br>Boundary: data substrate only — behaviors live in the actor domains
 *           ({@code infantry/}, {@code mech/}, ...), not here. For
 *           proximity, use a spatial index's {@code gather()}; never scan
 *           the live registry. An entity is a bare {@code long} id: the roster
 *           holds a dense {@code long[]}, and every per-unit datum lives in the
 *           {@code EntityWorld}'s id-keyed component columns (reached via the
 *           {@code World} facade / per-component Services). The identity-collapse
 *           epic deleted the old {@code Entity} handle — {@code entity = id} holds
 *           at every layer. (A {@code unit/} → {@code entity/} package rename is
 *           now unblocked but not yet done — deferred as a pure move.)
 *           Field-lifecycle docs on the {@code UnitRosterService} columns are
 *           mandated, not optional.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} and
 * {@code roadmap/ecs-migration/overview.md}.
 */
package com.dillon.starsectormarines.battle.unit;
