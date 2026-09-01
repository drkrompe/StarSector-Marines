package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

import java.util.Random;

/**
 * A place a mission asks for, before the map is big enough to say where it is.
 *
 * <p>A {@link Precinct} is seeded at a cell, which is the wrong unit for
 * authoring: a scenario wants "the garrison in the north-east", and what cell
 * that means depends on the map. A brief states the place and the
 * {@link MapPlacement}, and becomes a precinct once there is a map to lay it
 * into.
 *
 * <p>The split matters for more than convenience. A brief can be written once
 * and used at every map scale, and two missions asking for the same layout get
 * different maps, because the placement is a region to land in rather than a
 * position.
 */
public record PrecinctBrief(String name, MapPlacement placement,
                            GrownTrunkPlan.Profile growth, FortressProgram program,
                            Precinct.Boundary boundary) {

    public PrecinctBrief {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a place is named");
        if (placement == null) throw new IllegalArgumentException(name + " has no placement");
        if (growth == null) throw new IllegalArgumentException(name + " has no growth profile");
        if (boundary == null) throw new IllegalArgumentException(name + " has no boundary");
    }

    /** A place whose parcels are zoned and filled the ordinary way. */
    public static PrecinctBrief settlement(String name, MapPlacement placement,
                                           GrownTrunkPlan.Profile growth) {
        return new PrecinctBrief(name, placement, growth, null, Precinct.Boundary.OPEN);
    }

    /** A place that owes authored buildings and is walled — what a fortress is. */
    public static PrecinctBrief garrison(String name, MapPlacement placement,
                                         GrownTrunkPlan.Profile growth,
                                         FortressProgram program) {
        return new PrecinctBrief(name, placement, growth, program,
                Precinct.Boundary.WALLED);
    }

    /** The same place asked for somewhere else. */
    public PrecinctBrief at(MapPlacement where) {
        return new PrecinctBrief(name, where, growth, program, boundary);
    }

    /** Resolves to a precinct seeded inside this brief's placement. */
    public Precinct resolve(int width, int height, int margin, Random rng) {
        int[] seed = placement.resolve(width, height, margin, rng);
        return new Precinct(name, seed[0], seed[1], growth, program, boundary);
    }
}
