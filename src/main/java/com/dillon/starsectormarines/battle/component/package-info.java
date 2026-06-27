/**
 * ECS composition layer — optional capabilities as components, not nullable
 * fields.
 *
 * <p>Category: game-side composition layer.
 * <br>Charter:  two generations side by side. The committed target is the
 *           archetype-table {@code engine.ecs.EntityWorld};
 *           {@link com.dillon.starsectormarines.battle.component.BattleComponents}
 *           here is the game's component-type registrations + shared queries for
 *           it (one instance per battle, alongside the per-battle world). The
 *           generic {@link com.dillon.starsectormarines.battle.component.ComponentStore}
 *           — a presence-based, entity-id-keyed sparse store — is the
 *           <em>transitional</em> primitive its remaining users migrate off
 *           during the retrofit ({@code roadmap/ecs-migration/archetype-storage.md}).
 * <br>Boundary: no concrete component <em>records</em> here. Legacy store-backed
 *           records live in a {@code components} subpackage of their domain
 *           (convention: {@code XxxComponent}) — e.g.
 *           {@code battle.air.components.CrashingComponent},
 *           {@code battle.unit.components.RenderPositionComponent},
 *           {@code battle.mech.components.MechLoadoutComponent} — and the
 *           systems that process them stay in their domain package. Archetype
 *           components are not classes at all: a {@code ComponentType} + typed
 *           columns, defined only in {@code BattleComponents}. The shared
 *           {@code UnitRosterService} dense roster stays in {@code battle.unit};
 *           live combat columns are world components reached by id via
 *           {@code World}.
 */
package com.dillon.starsectormarines.battle.component;
