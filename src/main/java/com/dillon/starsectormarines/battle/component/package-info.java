/**
 * ECS composition layer — optional capabilities as components, not nullable
 * fields.
 *
 * <p>Category: engine infrastructure (storage + composition).
 * <br>Charter:  the generic {@link com.dillon.starsectormarines.battle.component.ComponentStore}
 *           — a presence-based, entity-id-keyed store. A component is a small
 *           bag of state an entity <em>has</em> (or doesn't); a system processes
 *           the entities that have a given component by iterating its store,
 *           never by scanning all units and branching on {@code role}/{@code type}.
 * <br>Boundary: only the generic store lives here. The concrete component
 *           <em>records</em> live in a {@code components} subpackage of their
 *           related domain (convention: named {@code XxxComponent}) — e.g.
 *           {@code battle.air.components.CrashingComponent},
 *           {@code battle.unit.components.RenderPositionComponent} /
 *           {@code DeadBodyComponent}, {@code battle.mech.components.MechLoadoutComponent}
 *           — and the systems that process them stay in their domain package
 *           (the drone crash system in {@code battle.drone}, etc.). The shared
 *           {@code UnitRegistry} dense table stays in {@code battle.unit}. This
 *           package is the seam for the component-model phase of the ECS
 *           migration — see {@code roadmap/ecs-migration/component-model.md}.
 *           Presence is hand-rolled per store; no generic {@code Aspect}/{@code World}
 *           query engine yet (Phase C, gated on heterogeneity).
 */
package com.dillon.starsectormarines.battle.component;
