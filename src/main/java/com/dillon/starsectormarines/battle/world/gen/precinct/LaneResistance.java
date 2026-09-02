package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * What one lane holds, rung by rung, between the beachhead and the objective.
 *
 * <p>A lane is the map's side of a command track, and a ladder is what stands on
 * it: one programmed place per rung, the deepest abutting the objective's claim
 * and the shallowest out toward the attacker. Every rung says two things — how
 * hard the place is ({@link Fortification.Strength}) and what kind of place it
 * is ({@link Kind}) — because those are different questions. Strength is the
 * dial {@code precincts.md} already owns: what shoots back, how many ways in,
 * what the wall costs. Kind is what the place is <em>made of</em>, and it is
 * what decides how many compounds a track has to take on the way.
 *
 * <p><b>Derived from the objective's own rung, stated when a mission cares.</b>
 * The objective resolves its fortification from the world's defence rating under
 * the operation tier's cap; a lane's ladder is that answer stepped down going
 * outward, so a citadel is approached through strongpoints and a picket is
 * approached through pickets. Nothing on a lane is ever harder than the thing
 * the lane leads to, and nothing is softer than {@link
 * Fortification.Strength#PICKET} — a rung below a picket is an empty field, and
 * a lane with an empty rung in it is not a ladder.
 *
 * <p>A mission may {@linkplain #stated state} a ladder instead. A lane left at
 * pickets is a feint; a lane of strongpoints is the grind. That is the same
 * authored-wins shape {@link Fortification} and {@link PrecinctCharacter} have,
 * for the same reason: the generator's answer is a default for a mission nobody
 * has thought about.
 */
public record LaneResistance(List<Rung> rungs) {

    /** The rung abutting the objective's claim. */
    public static final int INNERMOST_BAND = 1;

    /** The rung furthest out toward the attacker. */
    public static final int OUTERMOST_BAND = 3;

    public LaneResistance {
        if (rungs == null || rungs.isEmpty()) {
            throw new IllegalArgumentException("a lane with no rungs on it is not a ladder");
        }
        rungs = List.copyOf(rungs);
        int previous = Integer.MIN_VALUE;
        for (Rung rung : rungs) {
            if (rung.band() <= previous) {
                throw new IllegalArgumentException(
                        "a lane's rungs run inward-out and name each band once: band "
                                + rung.band() + " after band " + previous);
            }
            previous = rung.band();
        }
    }

    /**
     * One place on a lane.
     *
     * <p><b>The band names the rung, and is not a promise about where it lands.</b>
     * A rung is seeded a fraction of the way along its lane from the attacker's
     * region to the objective's, and the front band it turns out to stand in is
     * a fact about the finished map: {@code FrontDepth} cuts equal rings around
     * the objective's claim out to the map's furthest cell, and the beachhead is
     * itself two rings out at Conquest's default standoff — so the outermost
     * rung stands in front of the force rather than in front band 3, which is
     * the ground behind it. The number is which rung of the ladder this is,
     * counting in from the attacker.
     *
     * @param band     which rung, {@link #INNERMOST_BAND} abutting the objective
     * @param strength how hard it is to take
     * @param kind     what it is made of
     */
    public record Rung(int band, Fortification.Strength strength, Kind kind) {

        public Rung {
            if (band < INNERMOST_BAND || band > OUTERMOST_BAND) {
                throw new IllegalArgumentException("no lane rung stands in band " + band);
            }
            if (strength == null) throw new IllegalArgumentException("a rung states its strength");
            if (kind == null) throw new IllegalArgumentException("a rung states what it is");
        }

        /** What this rung owes, before the map is asked whether it has room for it. */
        public FortressProgram program() {
            return kind.program();
        }

        /** The wall and guns this rung stands behind. */
        public Fortification fortification() {
            return strength.fortification();
        }
    }

    /** What a lane place is made of. Neither packs a keep and neither owes an airfield. */
    public enum Kind {
        /** A guard post or two, a barrack block and a store. One compound. */
        OUTPOST,
        /** A gatehouse, guard posts, two barrack blocks and an armoury. Three compounds. */
        STRONGPOINT;

        /** The program this kind orders. */
        public FortressProgram program() {
            return switch (this) {
                case OUTPOST -> FortressProgram.outpost();
                case STRONGPOINT -> FortressProgram.strongpoint();
            };
        }
    }

    /**
     * The ladder a lane gets when nobody states one, stepped down from the
     * objective's own rung.
     *
     * <p>Band 1 is a strongpoint one rung below the objective: the last thing
     * standing before the fortress should be a place a track has to reduce, and
     * it should still be recognisably lesser than the fortress itself. Bands 2
     * and 3 are outposts, two rungs below and at {@code PICKET} respectively —
     * the approach gets thinner the further out it is, because a defender
     * garrisons what it can hold and holds what is nearest.
     *
     * <p>{@link Fortification.Strength#nudged} already floors at {@code PICKET},
     * so a picket objective produces a lane of pickets rather than a lane of
     * nothing.
     */
    public static LaneResistance derive(Fortification.Strength objective) {
        if (objective == null) {
            throw new IllegalArgumentException("a derived ladder steps down from something");
        }
        return new LaneResistance(List.of(
                new Rung(1, objective.nudged(-1), Kind.STRONGPOINT),
                new Rung(2, objective.nudged(-2), Kind.OUTPOST),
                new Rung(3, Fortification.Strength.PICKET, Kind.OUTPOST)));
    }

    /**
     * A ladder a mission wrote down. Bands are named inward-out and each at most
     * once; a band left out is a rung this lane does not have.
     */
    public static LaneResistance stated(List<Rung> rungs) {
        return new LaneResistance(rungs);
    }

    /**
     * The three ordinary rungs at stated strengths, keeping the derived shape —
     * a strongpoint abutting the objective and outposts beyond it.
     */
    public static LaneResistance stated(Fortification.Strength band1,
                                        Fortification.Strength band2,
                                        Fortification.Strength band3) {
        List<Rung> out = new ArrayList<>();
        out.add(new Rung(1, band1, Kind.STRONGPOINT));
        out.add(new Rung(2, band2, Kind.OUTPOST));
        out.add(new Rung(3, band3, Kind.OUTPOST));
        return new LaneResistance(out);
    }

    /** This ladder's rung in {@code band}, or {@code null} where it has none. */
    public Rung at(int band) {
        for (Rung rung : rungs) {
            if (rung.band() == band) return rung;
        }
        return null;
    }
}
