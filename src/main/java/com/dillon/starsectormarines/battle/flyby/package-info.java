/**
 * Standalone feature domain — the fighter roster the air arm flies.
 *
 * <p>Category: data.
 * <br>Charter:  which fighters exist, which factions fly them, and which of
 *           them a battle has committed — {@code FighterProfile},
 *           {@code FighterWing}, {@code FlybyRoster}, {@code PlayerFleetWings},
 *           {@code DebugAirRoster}, {@code WeaponClass}. A profile is an
 *           {@code air.Airframe}: it says what the aircraft looks like, how it
 *           flies, and what it drops.
 * <br>Boundary: data only. Nothing here flies anything. Its own flight model,
 *           fire resolution, lifecycle and GL renderer were deleted on
 *           2026-08-30 as duplicates of {@code air/}, which owns all four —
 *           {@code air.AirCoverSystem} flies these wings in from off the map
 *           and {@code air.AirStrikeSystem} flies them off a berth, and both
 *           are the same sortie from a different origin.
 * <br>Residue: {@code FlybyOverlay} is six sound ids that {@code FighterProfile}
 *           names, and the package name is the last thing left calling a
 *           fighter a "flyby". Both fold once {@code FighterProfile} can be
 *           edited; see {@code fighter-air-entities.md}.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.flyby;
