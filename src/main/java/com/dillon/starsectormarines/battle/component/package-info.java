/**
 * ECS composition layer — optional capabilities as components, not nullable
 * fields.
 *
 * <p>Category: game-side composition layer.
 * <br>Charter:  the game's binding to the archetype-table
 *           {@code engine.ecs.EntityWorld}.
 *           {@link com.dillon.starsectormarines.battle.component.BattleComponents}
 *           here is the component-type registrations + shared queries for it (one
 *           instance per battle, alongside the per-battle world). The transitional
 *           {@code ComponentStore} that earlier slices migrated off is now retired
 *           — the archetype table is the sole composition substrate
 *           ({@code roadmap/ecs-migration/archetype-storage.md}).
 * <br>Boundary: no concrete component <em>records</em> here. An OBJECT column's
 *           payload record lives in a {@code components} subpackage of its domain
 *           (convention: {@code XxxComponent}) — e.g.
 *           {@code battle.air.components.CrashingComponent},
 *           {@code battle.mech.components.MechLoadoutComponent} — and the systems
 *           that process them stay in their domain package. Archetype components
 *           are otherwise not classes at all: a {@code ComponentType} + typed
 *           columns, defined only in {@code BattleComponents}. The shared
 *           {@code UnitRosterService} dense roster stays in {@code battle.unit};
 *           live combat columns are world components reached by id via
 *           {@code World}.
 */
package com.dillon.starsectormarines.battle.component;
