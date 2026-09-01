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
public record PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom) {

    public PrecinctPlan {
        precincts = List.copyOf(precincts);
    }

    /** A plan with no opinion about where the attack comes from. */
    public PrecinctPlan(List<Precinct> precincts) {
        this(precincts, null);
    }

    /**
     * The place a mission is about, or {@code null} when nothing on the map is
     * programmed.
     *
     * <p>The first programmed precinct. A map with two garrisons has to pick
     * one to be the climax, and the order a mission listed them in is the
     * honest answer — it is the only statement of intent there is.
     */
    public Precinct objective() {
        for (Precinct precinct : precincts) {
            if (precinct.isProgrammed()) return precinct;
        }
        return null;
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

    /**
     * How much of the map is settled.
     *
     * <p>The same world can be a lonely installation or a city with an
     * installation in it, and which one is a decision about the battle rather
     * than about the planet. Density already exists per place — this says how
     * many places there are and how far they reach, which is the part that
     * decides whether a map reads as country or as conurbation.
     */
    public enum Sprawl {
        /**
         * An installation and the country around it. No town: the garrison is
         * the somewhere the battle happens, and everything else is approach.
         */
        REMOTE,
        /** A town, an installation, and a few outlying places. Country between them. */
        BALANCED,
        /**
         * A city that runs to the map edge. Nature survives only where a park
         * was left, because the main settlement's frontage covers everything it
         * can reach — which is what a profile at full density already means.
         */
        DENSE
    }

    /** A mission's own answer, which overrides anything derivable. */
    public static PrecinctPlan authored(List<Precinct> precincts) {
        if (precincts.isEmpty()) {
            throw new IllegalArgumentException("a map with no places in it is not a map");
        }
        return new PrecinctPlan(precincts);
    }

    /**
     * A mission's layout: what places, roughly where, and which way the attack
     * comes from.
     *
     * <p>This is the authoring surface. A conquest scenario says its garrison is
     * in the north-east and its marines arrive from the south-west, and the same
     * brief lays out at any map size.
     *
     * <p>Placements are honoured over separation, which is the one trade worth
     * stating. Two places asked for the same corner will end up close together,
     * because a mission that asks for that means it — a derived plan is where
     * spacing is the generator's business.
     *
     * @param attackerFrom where the attacking force arrives, or {@code null} to
     *                     let the map decide
     */
    public static PrecinctPlan laidOut(List<PrecinctBrief> briefs, MapPlacement attackerFrom,
                                       int width, int height, Random rng) {
        if (briefs.isEmpty()) {
            throw new IllegalArgumentException("a map with no places in it is not a map");
        }
        List<Precinct> out = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();
        for (PrecinctBrief brief : briefs) {
            out.add(spaced(brief, taken, width, height, rng));
        }
        return new PrecinctPlan(out, attackerFrom);
    }

    /**
     * Resolves one brief, retrying inside its own placement so two places do not
     * land on each other.
     *
     * <p>Bounded, and it gives up rather than widening the search: leaving a
     * place slightly too close to its neighbour is a worse map, and moving it
     * out of the region the mission asked for is a different map.
     */
    private static Precinct spaced(PrecinctBrief brief, List<int[]> taken,
                                   int width, int height, Random rng) {
        Precinct best = null;
        int bestGap = -1;
        for (int attempt = 0; attempt < 64; attempt++) {
            Precinct candidate = brief.resolve(width, height, EDGE_MARGIN, rng);
            int gap = Integer.MAX_VALUE;
            for (int[] other : taken) {
                int dx = other[0] - candidate.seedX();
                int dy = other[1] - candidate.seedY();
                gap = Math.min(gap, dx * dx + dy * dy);
            }
            if (gap > bestGap) {
                bestGap = gap;
                best = candidate;
            }
            if (gap >= MIN_SEED_SEPARATION * MIN_SEED_SEPARATION) break;
        }
        taken.add(new int[]{best.seedX(), best.seedY()});
        return best;
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
        return derive(profile, Sprawl.BALANCED, width, height, rng);
    }

    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      int width, int height, Random rng) {
        List<Precinct> out = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();

        float density = switch (sprawl) {
            // Full density is not "a lot of streets" — it is the point at which
            // a profile's frontage stops being a depth and covers everything it
            // can reach, so the settlement claims out to whatever its
            // neighbours and the map edge allow.
            case DENSE -> 1f;
            case REMOTE -> 0f;
            case BALANCED -> SettlementZoning.densityFor(profile.marketSize());
        };
        boolean garrison = profile.defenseLevel() > 0;

        // A remote map is an installation in country: adding a town to it is
        // the one thing that would stop it being one. The garrison is then the
        // somewhere the battle happens, so the settlement is only kept when
        // there is no garrison to be that.
        if (sprawl != Sprawl.REMOTE || !garrison) {
            GrownTrunkPlan.Profile main = GrownTrunkPlan.Profile.of(density, profile.link());
            int[] seed = placeSeed(taken, width, height, rng);
            out.add(Precinct.settlement("settlement", seed[0], seed[1], main));
        }

        if (garrison) {
            int[] garrisonSeed = placeSeed(taken, width, height, rng);
            // A garrison grows sparsely: it is an installation rather than a
            // town, and its ground comes from its program rather than from how
            // far its streets reach.
            out.add(Precinct.garrison("garrison", garrisonSeed[0], garrisonSeed[1],
                    GrownTrunkPlan.Profile.hamlet(), garrisonFor(profile)));
        }

        for (int i = 0; i < outlyingPlaces(profile.marketSize(), sprawl); i++) {
            int[] hamletSeed = placeSeed(taken, width, height, rng);
            if (hamletSeed == null) break;
            out.add(Precinct.settlement("outlying-" + (i + 1), hamletSeed[0], hamletSeed[1],
                    outlyingGrowth(sprawl)));
        }
        return new PrecinctPlan(out, null);
    }

    /**
     * How many places sit around the main one.
     *
     * <p>Zero below a market that could support them, then one per three sizes.
     * Coarse on purpose — the point is that the count moves with the world, not
     * that this curve is right.
     */
    private static int outlyingPlaces(int marketSize, Sprawl sprawl) {
        return switch (sprawl) {
            case REMOTE -> 0;
            case BALANCED -> Math.max(0, (marketSize - 2) / 3);
            // Enough seeds that the map is places rather than one place with a
            // very long reach: a single settlement at full density claims
            // outward evenly and comes out as a disc, where a city is districts
            // meeting each other.
            case DENSE -> Math.max(3, marketSize);
        };
    }

    /** What an outlying place is. In a city they are districts, not hamlets. */
    private static GrownTrunkPlan.Profile outlyingGrowth(Sprawl sprawl) {
        return sprawl == Sprawl.DENSE
                ? GrownTrunkPlan.Profile.city()
                : GrownTrunkPlan.Profile.hamlet();
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
