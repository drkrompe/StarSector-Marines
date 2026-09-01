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
 */
public record Precinct(String name, int seedX, int seedY,
                       GrownTrunkPlan.Profile growth,
                       FortressProgram program,
                       Boundary boundary) {

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
    }

    /** A place whose parcels are zoned and filled the ordinary way. */
    public static Precinct settlement(String name, int x, int y, GrownTrunkPlan.Profile growth) {
        return new Precinct(name, x, y, growth, null, Boundary.OPEN);
    }

    /** A place that owes authored buildings and is walled — what a fortress is. */
    public static Precinct garrison(String name, int x, int y,
                                    GrownTrunkPlan.Profile growth, FortressProgram program) {
        return new Precinct(name, x, y, growth, program, Boundary.WALLED);
    }

    /** Whether this precinct packs authored footprints rather than zoning its parcels. */
    public boolean isProgrammed() {
        return program != null;
    }
}
