/**
 * Actor domain — the marine combatant.
 *
 * <p>Category: actor domain (entity + weapons + behavior/GOAP + lifecycle).
 * <br>Charter:  the infantry GOAP composer ({@code GoapInfantryBehavior})
 *           and its disjoint goal/posture/action set, the marine's declared
 *           reflex chain ({@code InfantryReflexes} — the ordered interrupts
 *           that pre-empt the plan step, whose order is a pinned law),
 *           combatant behavior
 *           ({@code CombatantBehavior}), cohesion + prep
 *           ({@code InfantryCohesion}, {@code InfantryUnitPrep}), marine
 *           weapons ({@code Marine*}, {@code InfantryWeapons}), and kit
 *           drops/retrieval ({@code EquipmentDrop*},
 *           {@code KitRetrieverBehavior}).
 *
 * <p>Infantry combat identity composes three orthogonal axes: {@code
 * WeaponDef} is the behavior/art family, {@code EquipmentGrade} is the
 * issued hardware tier, and {@code SoldierProfile} combines innate aptitude
 * with earned experience. {@code InfantryCombatStats} is the single resolver;
 * adding a tier must not duplicate a weapon-family enum or directional asset.
 * <br>Boundary: infantry-specific GOAP lives here — a new infantry
 *           goal/posture goes here and wires into
 *           {@code GoapInfantryBehavior}, and a new individual-tier
 *           interrupt is a reflex added to {@code InfantryReflexes.CHAIN}
 *           at its rank rather than a branch in the dispatcher. The shared planner/engine is
 *           {@code decision/goap}; the shared fire&rarr;damage pipeline is
 *           {@code combat/}. Squad-coordination goals currently compose
 *           under infantry; promote to {@code squad/} only when a second
 *           actor type composes them.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} (GOAP partition rule).
 */
package com.dillon.starsectormarines.battle.infantry;
