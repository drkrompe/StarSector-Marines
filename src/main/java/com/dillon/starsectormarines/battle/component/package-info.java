/**
 * ECS composition layer — optional capabilities as components, not nullable
 * fields.
 *
 * <p>Category: engine infrastructure (storage + composition).
 * <br>Charter:  the generic {@link com.dillon.starsectormarines.battle.component.ComponentStore}
 *           — a presence-based, entity-id-keyed store — plus the concrete
 *           component types that compose onto entities ({@code Crashing},
 *           and more as capabilities migrate off {@code Entity}). A component is
 *           a small bag of state an entity <em>has</em> (or doesn't); a system
 *           processes the entities that have a given component by iterating its
 *           store, never by scanning all units and branching on
 *           {@code role}/{@code type}.
 * <br>Boundary: the store + component records live here; the systems that
 *           process them stay in their domain package (the drone crash system
 *           in {@code battle.drone}, etc.) and the shared {@code UnitRegistry}
 *           dense table stays in {@code battle.unit}. This package is the
 *           seam for the component-model phase of the ECS migration — see
 *           {@code roadmap/ecs-migration/component-model.md}. Presence is
 *           hand-rolled per store; no generic {@code Aspect}/{@code World}
 *           query engine yet (Phase C, gated on heterogeneity).
 */
package com.dillon.starsectormarines.battle.component;
