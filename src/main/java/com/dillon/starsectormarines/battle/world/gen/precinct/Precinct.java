package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

/**
 * One place on a map: where it starts, how it grows, what it owes, and whether
 * it is walled.
 *
 * <p>A map used to be a recipe of map-wide stages, so every kind of place was a
 * stage and a second one of anything was a second stage. A fortress was
 * conquest-only, biome-band-placed and rectangle-enveloped, and two fortresses
 * was not expressible at all. A precinct is the noun that removes that: a
 * fortress is a precinct owing a garrison program behind a wall, a town is one
 * owing nothing in the open, and two fortresses is two of these.
 *
 * <p>See {@code precincts.md} for the model and the measurements behind it.
 *
 * @param name    what to call it in evidence and reports; never read by the game
 * @param seedX   where its growth starts
 * @param seedY   where its growth starts
 * @param growth  how far and how densely its road skeleton spreads
 * @param program what it owes, or {@code null} for a place whose parcels are
 *                zoned and filled the ordinary way rather than packed from
 *                authored footprints. That one field is the whole difference
 *                between a fortress and a town.
 * @param boundary whether a wall is drawn around what grew
 * @param fortification how hard that wall is to take; {@code null} for an open place
 * @param character what a zoned place is on the inside — the mix its parcels
 *                are themed from and where its centre is. Required for a zoned
 *                precinct and absent for a programmed one, whose interior is
 *                packed rather than themed
 * @param landing what the attacking force comes down on here, or {@code null}
 *                for the ordinary case — a place nobody lands on. A landing
 *                place is programmed like any other, and this is what tells the
 *                stages that emit its nodes and seat its berths that the ground
 *                is the marines' rather than the defender's
 */
public record Precinct(String name, int seedX, int seedY,
                       GrownTrunkPlan.Profile growth,
                       FortressProgram program,
                       Boundary boundary,
                       Fortification fortification,
                       PrecinctCharacter character,
                       LandingKind landing) {

    /** A place whose wall, if it has one, is of ordinary strength. */
    public Precinct(String name, int seedX, int seedY, GrownTrunkPlan.Profile growth,
                    FortressProgram program, Boundary boundary) {
        this(name, seedX, seedY, growth, program, boundary,
                boundary == Boundary.WALLED ? Fortification.GARRISON : null);
    }

    /** A place whose interior, if it is zoned, is an ordinary town's. */
    public Precinct(String name, int seedX, int seedY, GrownTrunkPlan.Profile growth,
                    FortressProgram program, Boundary boundary, Fortification fortification) {
        this(name, seedX, seedY, growth, program, boundary, fortification,
                program == null ? PrecinctCharacter.TOWN : null);
    }

    /** A place nobody lands on, which is every place but one. */
    public Precinct(String name, int seedX, int seedY, GrownTrunkPlan.Profile growth,
                    FortressProgram program, Boundary boundary, Fortification fortification,
                    PrecinctCharacter character) {
        this(name, seedX, seedY, growth, program, boundary, fortification, character, null);
    }

    /** What happens at the edge of a precinct once its interior exists. */
    public enum Boundary {
        /**
         * Nothing. The precinct meets its neighbours where their growth met,
         * which is the ordinary case and is what a town does.
         */
        OPEN,
        /**
         * A wall stamped around the outline of what grew, with gates where the
         * arms cross it. Drawn last on purpose: a wall stamped first can only
         * enclose whatever the fill happened to leave — see
         * {@code compound-programs.md}.
         */
        WALLED
    }

    public Precinct {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a precinct is named");
        if (growth == null) throw new IllegalArgumentException(name + " has no growth profile");
        if (boundary == null) throw new IllegalArgumentException(name + " has no boundary");
        if (boundary == Boundary.WALLED && fortification == null) {
            throw new IllegalArgumentException(name + " is walled but says nothing about "
                    + "how hard the wall is, which is the dial a mission needs");
        }
        if (program == null && character == null) {
            throw new IllegalArgumentException(name + " is zoned but says nothing about "
                    + "what kind of place it is, so its parcels have nothing to be themed from");
        }
        if (program != null && character != null) {
            throw new IllegalArgumentException(name + " is programmed, so its interior is "
                    + "packed from its program and a character would say nothing");
        }
        if (landing != null && program == null) {
            throw new IllegalArgumentException(name + " is landed on but has no program, "
                    + "so nothing claims the apron the berths stand on");
        }
    }

    /** A place whose parcels are zoned and filled the ordinary way, as a town. */
    public static Precinct settlement(String name, int x, int y, GrownTrunkPlan.Profile growth) {
        return settlement(name, x, y, growth, PrecinctCharacter.TOWN);
    }

    /** A zoned place of a stated kind. */
    public static Precinct settlement(String name, int x, int y, GrownTrunkPlan.Profile growth,
                                      PrecinctCharacter character) {
        return new Precinct(name, x, y, growth, null, Boundary.OPEN, null, character);
    }

    /** A place that owes authored buildings and is walled — what a fortress is. */
    public static Precinct garrison(String name, int x, int y,
                                    GrownTrunkPlan.Profile growth, FortressProgram program) {
        return garrison(name, x, y, growth, program, Fortification.GARRISON);
    }

    /** The same, fortified to whatever the mission thinks its attacker can handle. */
    public static Precinct garrison(String name, int x, int y,
                                    GrownTrunkPlan.Profile growth, FortressProgram program,
                                    Fortification fortification) {
        return new Precinct(name, x, y, growth, program, Boundary.WALLED, fortification, null);
    }

    /**
     * The ground the attacking force comes ashore on.
     *
     * <p>Open rather than walled, and programmed rather than zoned: its apron
     * is what its program owes, so it claims its ground with the other
     * programmed places — before the settlement floods — and a town grows round
     * the beachhead instead of over it.
     */
    public static Precinct landing(String name, int x, int y,
                                   GrownTrunkPlan.Profile growth, LandingKind kind,
                                   FortressProgram program) {
        return new Precinct(name, x, y, growth, program, Boundary.OPEN, null, null, kind);
    }

    /** This place, said to be a different kind of place inside. Zoned precincts only. */
    public Precinct withCharacter(PrecinctCharacter character) {
        return new Precinct(name, seedX, seedY, growth, program, boundary, fortification,
                character, landing);
    }

    /** Whether the attacking force comes ashore here. */
    public boolean isLanding() {
        return landing != null;
    }

    /** Whether this precinct packs authored footprints rather than zoning its parcels. */
    public boolean isProgrammed() {
        return program != null;
    }
}
