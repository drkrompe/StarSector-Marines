/**
 * Framework core — the entity registry + data substrate.
 *
 * <p>Category: framework core (the shared entity store; no single feature
 *           owner).
 * <br>Charter:  the dense SoA roster + id-mint ({@code UnitRosterService}) and
 *           the faction roster ({@code FactionUnitRoster}), the spatial indices
 *           ({@code UnitSpatialIndex}, {@code UnitDestinationSpatialIndex}), the
 *           construction spec ({@code EntitySpec}), the off-roster body surface
 *           ({@code BodyCarrier}, {@code BodyService}), the shared body
 *           geometry decision ({@code BodyRadius}), and the shared enums
 *           ({@code Faction}, {@code UnitRole}, {@code UnitType}).
 * <br>Boundary: data substrate only — behaviors live in the actor domains
 *           ({@code infantry/}, {@code mech/}, ...), not here. The one
 *           decision it does make is where an arrival actually stands: a spec
 *           names a cell, adoption is the only place that sees every arrival,
 *           and an arrival that carries no {@code MOVEMENT} takes that cell
 *           while the incumbent steps aside ({@code StandingRoom}). Overlap
 *           between two movers is left to {@code SeparationSystem}, which owns
 *           it; what is fixed here is the case nothing later can undo, because
 *           a body that cannot move has been placed once and for good. For
 *           proximity, use a spatial index's {@code gather()}; never scan
 *           the live registry. An entity is a bare {@code long} id: the roster
 *           holds a dense {@code long[]}, and every per-unit datum lives in the
 *           {@code EntityWorld}'s id-keyed component columns (reached via the
 *           {@code World} facade / per-component Services). There is no separate
 *           entity handle: {@code entity = id} holds at every layer.
 *           Field-lifecycle docs on the {@code UnitRosterService} columns are
 *           mandated, not optional. Not every body is a roster row: a convoy
 *           chassis and an aircraft are world-resident bodies that reach the
 *           scans through {@code BodyService}. Consumers ask that registry
 *           rather than asking each carrier in turn — a per-kind branch is
 *           exactly what left three sites unable to see an aircraft. A carrier
 *           answers presence and altitude; whether a given shooter may engage a
 *           body is {@code combat/EngagementService}'s relation, not a
 *           predicate the body carries. How big a body is may be <em>read</em>
 *           by id or columnar for cost reasons, but it is <em>decided</em> once
 *           in {@code BodyRadius}: a second derivation of a body's own geometry
 *           does not belong anywhere, however cheap the access has to be.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} and {@code ecs-nouns.md}.
 */
package com.dillon.starsectormarines.battle.unit;
