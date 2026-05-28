/**
 * Actor domain — the marine combatant.
 *
 * <p>Category: actor domain (entity + weapons + behavior/GOAP + lifecycle).
 * <br>Charter:  the infantry GOAP composer ({@code GoapInfantryBehavior})
 *           and its disjoint goal/posture/action set, combatant behavior
 *           ({@code CombatantBehavior}), cohesion + prep
 *           ({@code InfantryCohesion}, {@code InfantryUnitPrep}), marine
 *           weapons ({@code Marine*}, {@code InfantryWeapons}), and kit
 *           drops/retrieval ({@code EquipmentDrop*},
 *           {@code KitRetrieverBehavior}).
 * <br>Boundary: infantry-specific GOAP lives here — a new infantry
 *           goal/posture goes here and wires into
 *           {@code GoapInfantryBehavior}. The shared planner/engine is
 *           {@code decision/goap}; the shared fire&rarr;damage pipeline is
 *           {@code combat/}. Squad-coordination goals currently compose
 *           under infantry; promote to {@code squad/} only when a second
 *           actor type composes them.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} (GOAP partition rule).
 */
package com.dillon.starsectormarines.battle.infantry;
