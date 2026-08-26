/**
 * Actor domain — the mech combatant.
 *
 * <p>Category: actor domain (entity + weapons + behavior/GOAP + lifecycle).
 * <br>Charter:  the mech GOAP composer ({@code GoapMechBehavior}) + its
 *           goals ({@code Mech*Goal}, {@code BackstopAssignedSquad*},
 *           {@code OverwatchKillZone*}), {@code MechCombatantBehavior} +
 *           {@code MechBreakContact}, and mech weapon config
 *           ({@code WeaponDef}, {@code MechRole}). The mech's optional
 *           loadout capability is a component, {@code MechLoadoutComponent},
 *           in the {@code components/} subpackage (per the ECS-migration
 *           component convention).
 * <br>Boundary: mech-specific GOAP + weapon <em>config</em> here; the
 *           shared {@code HeavyWeapons} firing mechanism lives in
 *           {@code combat/}. Mech and infantry likely share a combatant
 *           core eventually — but per the lean-engine rule, promote shared
 *           pieces to a common base only when a third consumer appears,
 *           not preemptively (see {@code ai-nouns.md}, Direction).
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.mech;
