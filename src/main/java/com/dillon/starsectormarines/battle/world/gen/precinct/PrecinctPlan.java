package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicZoning;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
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
public record PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom,
                           Standoff standoff) {

    public PrecinctPlan {
        precincts = List.copyOf(precincts);
        // A plan that says nothing about its approach gets the one every
        // precinct map had before a standoff existed: the beachhead on the map
        // edge the attacker's own band sits against.
        if (standoff == null) standoff = Standoff.FAR;
    }

    /** A plan with no opinion about where the attack comes from. */
    public PrecinctPlan(List<Precinct> precincts) {
        this(precincts, null, Standoff.FAR);
    }

    /** A plan that states where the attack comes from but not how far out. */
    public PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom) {
        this(precincts, attackerFrom, Standoff.FAR);
    }

    /**
     * The same plan with a stated approach length.
     *
     * <p>Layered on rather than taken at derivation, because how far a force
     * lands from the objective is a mission's statement about its own battle and
     * has nothing to do with the world the places were derived from — the same
     * derived map is a long approach or a short one depending only on who is
     * being sent.
     */
    public PrecinctPlan withStandoff(Standoff standoff) {
        return new PrecinctPlan(precincts, attackerFrom, standoff);
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
     * How much of a small map a margin or a separation may eat.
     *
     * <p>A map seats what it can seat. Both fixed values above are stated for
     * the map sizes they were measured on, and on a skirmish map they are most
     * of it: at 112x64 a 30-cell margin leaves a four-cell band to seed in, so
     * the second place is never found and a defended world came out with no
     * garrison to attack. Taking a quarter of the map instead leaves the
     * measured sizes exactly where they were and lets a small map keep the
     * shape of the model rather than losing places to arithmetic.
     */
    private static final int SMALL_MAP_SHARE = 4;

    /**
     * How much of the map one garrison's ground may be.
     *
     * <p>A first guess to be measured rather than a tuned value. It exists so a
     * program authored for a landing zone does not swallow a skirmish map; what
     * share actually plays well is an open question, and this number should
     * move when somebody answers it.
     */
    private static final float FIT = 0.35f;

    /** The edge margin this map can afford. */
    private static int marginFor(int width, int height) {
        return Math.min(EDGE_MARGIN, Math.min(width, height) / SMALL_MAP_SHARE);
    }

    /** The separation between two seeds this map can afford. */
    private static int separationFor(int width, int height) {
        return Math.min(MIN_SEED_SEPARATION, Math.max(width, height) / SMALL_MAP_SHARE);
    }

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

    /** As {@link #derive(TargetProfile, Sprawl, Fortification.Demand, int, int, Random)} with nothing said about the attacker. */
    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      int width, int height, Random rng) {
        return derive(profile, sprawl, Fortification.Demand.UNSTATED, width, height, rng);
    }

    /**
     * The default set for a target world, under what a mission says about the
     * force it is sending.
     *
     * @param demand what the operation may ask of its attacker; the garrison's
     *               fortification is the world's rating resolved against it
     */
    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      Fortification.Demand demand,
                                      int width, int height, Random rng) {
        return derive(profile, sprawl, demand, MapPlacement.ANYWHERE, null,
                width, height, rng);
    }

    /**
     * The default set for a target world, laid out where the mission says.
     *
     * <p>The same derivation as above with the two things a scenario is entitled
     * to state: roughly where the place it is about goes, and roughly where the
     * attacking force arrives. Everything else — how many places, what they are,
     * what the garrison owes — still comes off the world.
     *
     * <p><b>A stated objective is seeded first.</b> The garrison is the place the
     * battle is about, and a settlement that drew before it would push it out of
     * the region the mission named — the same "need goes before frontage" rule
     * the claim pass already applies one step later. The draw order changes only
     * when a placement is actually stated: {@link MapPlacement#ANYWHERE} means
     * nothing was, and takes the order it always had.
     *
     * @param objective    roughly where the garrison goes;
     *                     {@link MapPlacement#ANYWHERE} for "the mission does not
     *                     care", which is what a derived plan passes
     * @param attackerFrom where the attacking force arrives, or {@code null} to
     *                     let the map decide
     */
    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      Fortification.Demand demand,
                                      MapPlacement objective, MapPlacement attackerFrom,
                                      int width, int height, Random rng) {
        List<Precinct> out = new ArrayList<>();
        List<int[]> taken = new ArrayList<>();
        int margin = marginFor(width, height);
        int separation = separationFor(width, height);
        boolean stated = objective != null && !MapPlacement.ANYWHERE.equals(objective);

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

        // The stated objective takes its ground before anything competes for it.
        // Built here and added below, so the list order — settlement, garrison,
        // outlying — is the one every consumer already reads.
        Precinct objectivePlace = null;
        if (garrison && stated) {
            objectivePlace = garrison(profile, demand,
                    placedSeed(objective, taken, margin, separation, width, height, rng),
                    width, height);
        }

        // A remote map is an installation in country: adding a town to it is
        // the one thing that would stop it being one. The garrison is then the
        // somewhere the battle happens, so the settlement is only kept when
        // there is no garrison to be that.
        int mainIndex = -1;
        if (sprawl != Sprawl.REMOTE || !garrison) {
            GrownTrunkPlan.Profile main = GrownTrunkPlan.Profile.of(density, profile.link());
            int[] seed = placeSeed(taken, margin, separation, width, height, rng);
            mainIndex = out.size();
            out.add(Precinct.settlement("settlement", seed[0], seed[1], main));
        }

        if (garrison) {
            if (objectivePlace == null) {
                // A defended world never loses its objective to a small map: where
                // the draw finds no room, the garrison takes the emptiest cell
                // there is rather than nothing at all.
                int[] garrisonSeed = placeSeed(taken, margin, separation, width, height, rng);
                if (garrisonSeed == null) garrisonSeed = emptiestSeed(taken, margin, width, height);
                objectivePlace = garrison(profile, demand, garrisonSeed, width, height);
            }
            out.add(objectivePlace);
        }

        for (int i = 0; i < outlyingPlaces(profile.marketSize(), sprawl); i++) {
            int[] hamletSeed = placeSeed(taken, margin, separation, width, height, rng);
            if (hamletSeed == null) break;
            out.add(Precinct.settlement("outlying-" + (i + 1), hamletSeed[0], hamletSeed[1],
                    outlyingGrowth(sprawl)));
        }

        // What a place is, decided after where every place is: the character
        // draws come last so nothing about a place's interior moves its seed,
        // and the same world lays out the same map whatever it is built of.
        MapDistrictTheme leaning = EconomicZoning.dominantTheme(profile.functions());
        for (int i = 0; i < out.size(); i++) {
            Precinct precinct = out.get(i);
            if (precinct.isProgrammed()) continue;
            PrecinctCharacter character = i == mainIndex
                    ? PrecinctCharacter.TOWN.leaning(leaning)
                    : outlyingCharacter(leaning, sprawl, rng);
            out.set(i, precinct.withCharacter(character));
        }
        return new PrecinctPlan(out, attackerFrom);
    }

    /**
     * The garrison a defended world owes, at the seed it was given.
     *
     * <p>A garrison grows sparsely: it is an installation rather than a town, and
     * its ground comes from its program rather than from how far its streets
     * reach. How hard it is to take comes from two facts with different jobs —
     * the world's rating says what is there, the demand says what this operation
     * may be asked to face.
     */
    private static Precinct garrison(TargetProfile profile, Fortification.Demand demand,
                                     int[] seed, int width, int height) {
        return Precinct.garrison("garrison", seed[0], seed[1],
                GrownTrunkPlan.Profile.hamlet(),
                garrisonFor(profile).fittedTo(Math.round(FIT * width * height)),
                demand.resolve(profile.defenseLevel()));
    }

    /**
     * A seed inside a stated placement, kept off the places already taken.
     *
     * <p>The same bounded retry {@link #spaced} uses — a mission that asks for a
     * region means it, so the search stays inside the region and settles for the
     * roomiest candidate it found rather than wandering out of it. When even that
     * is closer than the separation the map can afford, the placement's own
     * emptiest cell is taken instead, which is the objective's standing exemption
     * from losing its ground to a small map.
     */
    private static int[] placedSeed(MapPlacement where, List<int[]> taken, int margin,
                                    int separation, int width, int height, Random rng) {
        int[] best = null;
        long bestGap = -1;
        for (int attempt = 0; attempt < 64; attempt++) {
            int[] candidate = where.resolve(width, height, margin, rng);
            long gap = Long.MAX_VALUE;
            for (int[] other : taken) {
                long dx = other[0] - candidate[0];
                long dy = other[1] - candidate[1];
                gap = Math.min(gap, dx * dx + dy * dy);
            }
            if (gap > bestGap) {
                bestGap = gap;
                best = candidate;
            }
            if (gap >= (long) separation * separation) break;
        }
        if (bestGap < (long) separation * separation) {
            int[] rect = where.bounds(width, height);
            return emptiestSeed(taken,
                    Math.max(rect[0], margin), Math.max(rect[1], margin),
                    Math.min(rect[2], width - 1 - margin),
                    Math.min(rect[3], height - 1 - margin));
        }
        taken.add(best);
        return best;
    }

    /**
     * What an outlying place is.
     *
     * <p>Half are the plain kind — hamlets around a town, districts in a city,
     * because most of what surrounds a place is more of the same. The rest are
     * what the world's economy makes of them: a depot on an industrial world,
     * a dormitory on one that mostly houses people. Coarse for the same reason
     * the rest of the derivation is: a mission that cares should say.
     */
    private static PrecinctCharacter outlyingCharacter(MapDistrictTheme leaning, Sprawl sprawl,
                                                       Random rng) {
        PrecinctCharacter plain = sprawl == Sprawl.DENSE
                ? PrecinctCharacter.QUARTER : PrecinctCharacter.HAMLET;
        return rng.nextFloat() < 0.5f ? plain : economic(leaning);
    }

    /** The outlying place an economy builds; a dormitory when it says nothing. */
    private static PrecinctCharacter economic(MapDistrictTheme leaning) {
        if (leaning == null) return PrecinctCharacter.SUBURB;
        return switch (leaning) {
            case INDUSTRIAL, MILITARY_FORT -> PrecinctCharacter.DEPOT;
            case CIVIC -> PrecinctCharacter.QUARTER;
            // A farming world's outlying places are more country, not less.
            case OUTSKIRTS -> PrecinctCharacter.HAMLET;
            default -> PrecinctCharacter.SUBURB;
        };
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
     *
     * <p>What the world owes, before the map is asked whether it has room for
     * it: the caller fits the result to the ground available.
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
    private static int[] placeSeed(List<int[]> taken, int margin, int separation,
                                   int width, int height, Random rng) {
        int spanX = Math.max(1, width - 2 * margin);
        int spanY = Math.max(1, height - 2 * margin);
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = margin + rng.nextInt(spanX);
            int y = margin + rng.nextInt(spanY);
            boolean clear = true;
            for (int[] other : taken) {
                int dx = other[0] - x;
                int dy = other[1] - y;
                if (dx * dx + dy * dy < separation * separation) {
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

    /**
     * The in-margin cell furthest from everything already placed.
     *
     * <p>The fallback for a place the map must have. It takes no draw, so what
     * comes out does not depend on how many times the rejection sampler missed
     * before giving up, and the first cell of the scan wins a tie so two runs
     * of the same world agree.
     *
     * <p>Only the garrison uses it. A map with no room for another hamlet
     * should have fewer hamlets; a defended world with no room for its garrison
     * is a mission with nothing to attack.
     */
    private static int[] emptiestSeed(List<int[]> taken, int margin, int width, int height) {
        return emptiestSeed(taken, margin, margin,
                margin + Math.max(1, width - 2 * margin) - 1,
                margin + Math.max(1, height - 2 * margin) - 1);
    }

    /** The same scan restricted to one inclusive rect, for a stated placement. */
    private static int[] emptiestSeed(List<int[]> taken, int x0, int y0, int x1, int y1) {
        int[] best = null;
        long bestGap = -1;
        for (int cellY = y0; cellY <= Math.max(y0, y1); cellY++) {
            for (int cellX = x0; cellX <= Math.max(x0, x1); cellX++) {
                long gap = Long.MAX_VALUE;
                for (int[] other : taken) {
                    long dx = other[0] - cellX;
                    long dy = other[1] - cellY;
                    gap = Math.min(gap, dx * dx + dy * dy);
                }
                if (gap > bestGap) {
                    bestGap = gap;
                    best = new int[]{cellX, cellY};
                }
            }
        }
        taken.add(best);
        return best;
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
