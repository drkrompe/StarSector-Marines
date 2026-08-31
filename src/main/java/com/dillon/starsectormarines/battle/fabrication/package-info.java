/**
 * Category: battle feature domain.
 *
 * <p>Charter: what a vehicle bay is <em>for</em> — a machine on the stocks in
 * every berth, technicians working it up, and a finished chassis driving out
 * under its own power. The bay is the only structure on a battle map that makes
 * something rather than merely supplying it, and what it makes is a unit.
 *
 * <p>Boundary: this package owns the stocks and the work done on them. It does
 * not own the berths ({@code world.gen.Gantry}, authored by the fitting that cut
 * the bay), the technicians ({@code battle.ambient}, which decides who is at
 * work and where), or the ordinary life of a finished machine
 * ({@code battle.mech}). It creates ordinary battle actors and hands them over;
 * nothing here is a parallel unit model.
 *
 * <p>Deliberately not reinforcement. A reinforcement means answers a request
 * from somewhere off the map and is chosen against other means by how soon it
 * would arrive; a bay answers nothing and arrives nowhere. It produces because
 * it is held and worked, on its own cadence, in the place it stands — the same
 * relationship an armoury has to its tickets, with a unit at the end of it
 * instead of a number. See {@code reinforcement-nouns.md} for the distinction.
 *
 * <p>Pointer: {@code mapgen-nouns.md} owns the bay as a room and
 * {@code mechs-nouns.md} owns what comes out of it.
 */
package com.dillon.starsectormarines.battle.fabrication;
