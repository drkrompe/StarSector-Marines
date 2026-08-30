/**
 * Standalone feature domain — the fighter roster the air arm flies.
 *
 * <p>Category: data.
 * <br>Charter:  which fighters exist, which factions fly them, and which of
 *           them a battle has committed — {@code FighterProfile},
 *           {@code FighterWing}, {@code FlybyRoster}, {@code PlayerFleetWings},
 *           {@code DebugAirRoster}. A profile is an {@code air.Airframe}: it
 *           says what the aircraft looks like, which hull sizes and flies it,
 *           what it drops, and how much of it there is to shoot.
 * <br>Boundary: data only. Nothing here flies anything, fires anything, or
 *           draws anything. {@code air.AirCoverSystem} flies these wings in
 *           from off the map and {@code air.AirStrikeSystem} flies them off a
 *           berth; both are the same sortie from a different origin, and
 *           {@code air.AirOrdnance} is what a profile delivers with.
 * <br>Residue: the package name is the last thing in the codebase calling a
 *           fighter a "flyby". The roster wants to live under {@code air/}
 *           named for what it is; that is a mechanical rename across roughly
 *           thirty files, tracked by {@code fighter-air-entities.md}.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.flyby;
