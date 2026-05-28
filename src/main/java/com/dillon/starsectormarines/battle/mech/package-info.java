/**
 * Actor domain — the mech combatant.
 *
 * <p>Category: actor domain (entity + weapons + behavior/GOAP + lifecycle).
 * <br>Charter:  the mech GOAP composer ({@code GoapMechBehavior}) + its
 *           goals ({@code Mech*Goal}, {@code BackstopAssignedSquad*},
 *           {@code OverwatchKillZone*}), {@code MechCombatantBehavior} +
 *           {@code MechBreakContact}, and mech weapon config
 *           ({@code MechWeapon}, {@code MechLoadoutState},
 *           {@code MechRole}).
 * <br>Boundary: mech-specific GOAP + weapon <em>config</em> here; the
 *           shared {@code HeavyWeapons} firing mechanism lives in
 *           {@code combat/}. Mech and infantry likely share a combatant
 *           core eventually — but per the lean-engine rule, promote shared
 *           pieces to a common base only when a third consumer appears,
 *           not preemptively (see {@code overview.md} Future direction).
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.mech;
