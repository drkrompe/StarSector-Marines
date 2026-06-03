/**
 * Archetype-table ECS storage — the engine-layer entity/component substrate.
 *
 * <p>Category: engine infrastructure (storage + composition). Game-agnostic: it
 * knows entities, component types, columns, archetypes, queries, and array
 * lifecycle — and nothing about {@code UnitType} / position / combat.
 * <br>Charter: an entity is a bare {@code long} id; a component is a typed column
 *           of data (primitive-specialized, no boxing) or a zero-field presence
 *           <b>tag</b>; an archetype is the set of component types an entity has,
 *           identified by a {@code long} bitmask; each archetype owns one SoA
 *           {@link com.dillon.starsectormarines.engine.ecs.ArchetypeTable} of
 *           dense, swap-and-pop rows. A structural change (add/remove component)
 *           moves the entity's row between tables. Systems iterate a
 *           {@link com.dillon.starsectormarines.engine.ecs.Query}'s matched tables
 *           and walk the raw column arrays.
 * <br>Boundary: pure storage + composition mechanism. No game components live here
 *           (the game registers those during the retrofit); no rendering, no
 *           reflection (component types are registered in code), no sprite /
 *           presentation state (that is render-tier). Replaces the transitional
 *           {@link com.dillon.starsectormarines.battle.component.ComponentStore}.
 * <br>Pointer: {@code roadmap/ecs-migration/archetype-storage.md} (committed design).
 */
package com.dillon.starsectormarines.engine.ecs;
