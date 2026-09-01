package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What places a map has, and where they start.
 *
 * <p>Two ways in, and the second wins. A campaign battle {@linkplain
 * #derive derives} its places from the target world — a size-eight market with
 * planetary defenses gets a town and a garrison, a size-two one gets a hamlet —
 * so an ordinary battle needs nobody to author anything. A mission may instead
 * {@linkplain #authored state its own}, which is how a scenario asks for two
 * garrisons and no town, or a depot with no airfield, without the campaign
 * having to be able to express it.
 *
 * <p>Derivation is deliberately coarse. It is a default that keeps procedural
 * battles varied, not a simulation of settlement patterns, and the moment a
 * mission cares about the answer it should be authoring one instead.
 */
public record PrecinctPlan(List<Precinct> precincts) {

    public PrecinctPlan {
        precincts = List.copyOf(precincts);
    }

    /**
     * Minimum cells between two seeds.
     *
     * <p>Places seeded on top of each other do not produce two places: their
     * claims grow into one another immediately and the smaller one is a pocket
     * inside the larger. This is what makes them separate things on the map
     * rather than one thing with two names.
     */
    public static final int MIN_SEED_SEPARATION = 60;

    /** How far from the map edge a seed may fall, so a place has room to grow both ways. */
    private static final int EDGE_MARGIN = 30;

    /** A mission's own answer, which overrides anything derivable. */
    public static PrecinctPlan authored(List<Precinct> precincts) {
        if (precincts.isEmpty()) {
            throw new IllegalArgumentException("a map with no places in it is not a map");
        }
        return new PrecinctPlan(precincts);
    }

    /**
     * The default set for a target world.
     *
     * <p>One settlement always, because a battle happens somewhere. A walled
     * garrison when the world is defended, because that is what defenses are.
     * Outlying hamlets as the market grows, because a big world is not one
     * bigger town — it is a town with places around it, and that is the whole
     * reason for having precincts rather than one settlement with a larger
     * budget.
     */
    public static PrecinctPlan derive(TargetProfile profile, int width, int height,
                                      Random rng) {
        List<Precinct> out = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();

        float density = SettlementZoning.densityFor(profile.marketSize());
        GrownTrunkPlan.Profile main = GrownTrunkPlan.Profile.of(density, profile.link());
        int[] seed = placeSeed(taken, width, height, rng);
        out.add(Precinct.settlement("settlement", seed[0], seed[1], main));

        if (profile.defenseLevel() > 0) {
            int[] garrisonSeed = placeSeed(taken, width, height, rng);
            // A garrison grows sparsely: it is an installation rather than a
            // town, and its ground comes from its program rather than from how
            // far its streets reach.
            out.add(Precinct.garrison("garrison", garrisonSeed[0], garrisonSeed[1],
                    GrownTrunkPlan.Profile.hamlet(), garrisonFor(profile)));
        }

        for (int i = 0; i < outlyingPlaces(profile.marketSize()); i++) {
            int[] hamletSeed = placeSeed(taken, width, height, rng);
            if (hamletSeed == null) break;
            out.add(Precinct.settlement("outlying-" + (i + 1), hamletSeed[0], hamletSeed[1],
                    GrownTrunkPlan.Profile.hamlet()));
        }
        return new PrecinctPlan(out);
    }

    /**
     * How many places sit around the main one.
     *
     * <p>Zero below a market that could support them, then one per three sizes.
     * Coarse on purpose — the point is that the count moves with the world, not
     * that this curve is right.
     */
    private static int outlyingPlaces(int marketSize) {
        return Math.max(0, (marketSize - 2) / 3);
    }

    /**
     * What a defended world's garrison owes.
     *
     * <p>Airfields scale with the defense rating because an air arm is one of
     * the things being rated, and a heavily defended world fielding the same
     * single field as a lightly defended one wastes the only number that says
     * how fortified it is.
     */
    private static FortressProgram garrisonFor(TargetProfile profile) {
        return FortressProgram.garrison()
                .withAirfields(profile.defenseLevel() >= 4 ? 2 : 1);
    }

    /**
     * A seed far enough from the ones already placed.
     *
     * <p>Rejection sampling with a bounded number of tries, and {@code null}
     * when the map has no room left — a map that cannot fit another place
     * should have fewer places, not two on top of each other.
     */
    private static int[] placeSeed(List<int[]> taken, int width, int height, Random rng) {
        int spanX = Math.max(1, width - 2 * EDGE_MARGIN);
        int spanY = Math.max(1, height - 2 * EDGE_MARGIN);
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = EDGE_MARGIN + rng.nextInt(spanX);
            int y = EDGE_MARGIN + rng.nextInt(spanY);
            boolean clear = true;
            for (int[] other : taken) {
                int dx = other[0] - x;
                int dy = other[1] - y;
                if (dx * dx + dy * dy < MIN_SEED_SEPARATION * MIN_SEED_SEPARATION) {
                    clear = false;
                    break;
                }
            }
            if (!clear) continue;
            int[] seed = {x, y};
            taken.add(seed);
            return seed;
        }
        return null;
    }

    /** The seeds these precincts grow from, in order. */
    public List<GrownTrunkPlan.Seed> seeds() {
        List<GrownTrunkPlan.Seed> out = new ArrayList<>();
        for (Precinct precinct : precincts) {
            out.add(new GrownTrunkPlan.Seed(precinct.seedX(), precinct.seedY(),
                    precinct.growth()));
        }
        return out;
    }
}
