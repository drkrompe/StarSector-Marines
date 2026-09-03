package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicZoning;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

import java.util.ArrayList;
import java.util.Collections;
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
                           Standoff standoff, Lanes lanes,
                           List<String> unplacedLanePlaces,
                           List<String> movedLaneWaypoints) {

    public PrecinctPlan {
        precincts = List.copyOf(precincts);
        // A plan that says nothing about its approach gets the one every
        // precinct map had before a standoff existed: the beachhead on the map
        // edge the attacker's own band sits against.
        if (standoff == null) standoff = Standoff.FAR;
        unplacedLanePlaces = unplacedLanePlaces == null
                ? List.of() : List.copyOf(unplacedLanePlaces);
        movedLaneWaypoints = movedLaneWaypoints == null
                ? List.of() : List.copyOf(movedLaneWaypoints);
    }

    /** A plan whose lane diagnostics are only what could not be seated. */
    public PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom,
                        Standoff standoff, Lanes lanes,
                        List<String> unplacedLanePlaces) {
        this(precincts, attackerFrom, standoff, lanes, unplacedLanePlaces, List.of());
    }

    /** A plan with no opinion about where the attack comes from. */
    public PrecinctPlan(List<Precinct> precincts) {
        this(precincts, null, Standoff.FAR, null, List.of(), List.of());
    }

    /** A plan that states where the attack comes from but not how far out. */
    public PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom) {
        this(precincts, attackerFrom, Standoff.FAR, null, List.of(), List.of());
    }

    /** A plan whose places, approach and standoff are known but which has no lanes. */
    public PrecinctPlan(List<Precinct> precincts, MapPlacement attackerFrom,
                        Standoff standoff) {
        this(precincts, attackerFrom, standoff, null, List.of(), List.of());
    }

    /**
     * The same plan with a stated approach length.
     *
     * <p>Layered on rather than taken at derivation, because how far a force
     * lands from the objective is a mission's statement about its own battle and
     * has nothing to do with the world the places were derived from — the same
     * derived map is a long approach or a short one depending only on who is
     * being sent.
     *
     * <p><b>It does not move a landing place.</b> A plan whose beachhead is a
     * precinct seeded that precinct against a standoff already; restating one
     * afterwards changes what {@link ApproachRegion} measures and leaves the
     * place where it was grown. A derived Conquest therefore states its
     * standoff at derivation instead.
     */
    public PrecinctPlan withStandoff(Standoff standoff) {
        return new PrecinctPlan(precincts, attackerFrom, standoff, lanes,
                unplacedLanePlaces, movedLaneWaypoints);
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
     * The place the attacking force comes ashore on, or {@code null} on a map
     * that has none — which is every map but a Conquest's.
     */
    public Precinct landingPlace() {
        for (Precinct precinct : precincts) {
            if (precinct.isLanding()) return precinct;
        }
        return null;
    }

    /** Where the landing place sits in {@link #precincts}, or {@code -1}. */
    public int landingIndex() {
        for (int i = 0; i < precincts.size(); i++) {
            if (precincts.get(i).isLanding()) return i;
        }
        return -1;
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

    /**
     * How many lanes of resistance run between the attacker and the objective,
     * and what stands on each of them.
     *
     * <p>A lane is the map's side of a command track: a ribbon along the
     * traversal axis carrying one programmed place per front band, so a force
     * working up its track has something to take on the way rather than open
     * city and then a wall. It is the third thing a Conquest states after where
     * its objective goes and how far out its force lands.
     *
     * <p>The count defaults to {@link #DEFAULT_COUNT} so the map and the
     * commanders agree by construction. A lane whose {@link LaneResistance} is
     * absent — a shorter list, or a {@code null} entry — derives its ladder from
     * the objective's own rung; a stated one wins, which is how a mission says
     * that one lane is a feint and another is the grind.
     *
     * <p>A lane's <b>path</b> is stated the same way and for the same reason. A
     * lane is a route rather than a ribbon — {@link LanePath} says where it
     * goes, its rungs stand on its waypoints one per waypoint in path order,
     * and a mission that wants a zig-zag round a ridge writes one down. A lane
     * with no path derives one.
     *
     * @param count      how many lanes the map lays; one per command track
     * @param resistance a ladder per lane, or {@code null} entries for derived
     * @param paths      a route per lane, or {@code null} entries for derived
     */
    public record Lanes(int count, List<LaneResistance> resistance, List<LanePath> paths) {

        /**
         * How many lanes a Conquest lays when nobody says.
         *
         * <p>Equal to {@code ConquestTrackLayout.DEFAULT_TRACK_COUNT}, and
         * restated here rather than imported because the commander package
         * reads the generator and not the other way round.
         * {@code LaneTrackAgreementTest} pins the two together.
         */
        public static final int DEFAULT_COUNT = 3;

        public Lanes {
            if (count <= 0) {
                throw new IllegalArgumentException("a map with " + count + " lanes has none");
            }
            resistance = resistance == null
                    ? Collections.emptyList() : Collections.unmodifiableList(
                            new ArrayList<>(resistance));
            if (resistance.size() > count) {
                throw new IllegalArgumentException("stated " + resistance.size()
                        + " ladders for " + count + " lanes");
            }
            paths = paths == null
                    ? Collections.emptyList() : Collections.unmodifiableList(
                            new ArrayList<>(paths));
            if (paths.size() > count) {
                throw new IllegalArgumentException("stated " + paths.size()
                        + " paths for " + count + " lanes");
            }
        }

        /** Lanes whose ladders may be stated and whose routes are all derived. */
        public Lanes(int count, List<LaneResistance> resistance) {
            this(count, resistance, List.of());
        }

        /** The default count, every lane deriving its own ladder and route. */
        public static Lanes derived() {
            return new Lanes(DEFAULT_COUNT, List.of(), List.of());
        }

        /** This many lanes, every one of them deriving its own ladder and route. */
        public static Lanes of(int count) {
            return new Lanes(count, List.of(), List.of());
        }

        /** This many lanes on stated routes, every one deriving its own ladder. */
        public static Lanes along(List<LanePath> paths) {
            if (paths == null || paths.isEmpty()) {
                throw new IllegalArgumentException("no paths is not a statement of lanes");
            }
            return new Lanes(paths.size(), List.of(), paths);
        }

        /**
         * The ladder lane {@code index} carries: the stated one where there is
         * one, and otherwise the objective's rung stepped down.
         */
        public LaneResistance ladderFor(int index, Fortification.Strength objectiveRung) {
            if (index >= 0 && index < resistance.size() && resistance.get(index) != null) {
                return resistance.get(index);
            }
            return LaneResistance.derive(objectiveRung);
        }

        /** The route lane {@code index} was told to take, or {@code null} for derived. */
        public LanePath pathFor(int index) {
            if (index >= 0 && index < paths.size()) return paths.get(index);
            return null;
        }
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
        return derive(profile, sprawl, demand, objective, attackerFrom, null,
                width, height, rng);
    }

    /**
     * The same derivation with lanes of resistance laid between the attacker
     * and the objective.
     *
     * <p><b>Lanes are seeded after the objective and before the settlement</b>,
     * which is the whole of why they are a derivation input rather than
     * something layered on afterwards the way a {@link Standoff} is. A lane
     * place has to take its ground while there is ground to take: seeded after
     * the town, it would be dropped for want of separation or swallowed by a
     * claim that had already pooled through it. Seeded before, the town grows
     * around them — a strongpoint stands in the streets, an outpost in the
     * fields.
     *
     * @param lanes what the map lays between the two, or {@code null} for a
     *              mission with no opinion, which is every mission but Conquest
     */
    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      Fortification.Demand demand,
                                      MapPlacement objective, MapPlacement attackerFrom,
                                      Lanes lanes,
                                      int width, int height, Random rng) {
        return derive(profile, sprawl, demand, objective, attackerFrom, lanes,
                Standoff.FAR, null, width, height, rng);
    }

    /**
     * The same derivation with the ground the attacking force comes ashore on
     * laid out as a place of its own.
     *
     * <p><b>The landing place is seeded after the objective and the lanes and
     * before the settlement</b>, for the reason the lanes are: it has to take
     * its ground while there is ground to take. Seeded after the town, the
     * beachhead is streets — which is what it was before this, when it was not
     * a place at all but the first open ground a terminal scan found inside the
     * attacker's region, base district included.
     *
     * <p><b>The standoff enters the derivation here rather than being layered
     * on.</b> A landing place is seeded against it: where the force lands is
     * what the standoff states, so a plan whose beachhead is a precinct cannot
     * decide where that precinct goes without knowing it.
     * {@link #withStandoff} remains for a plan with no landing place, where the
     * statement is read once at the end by {@link ApproachRegion}.
     *
     * @param standoff how far short of the objective's claim the force lands
     * @param landing  what it comes down on, or {@code null} for a map with no
     *                 landing place — which is every mission but Conquest
     */
    public static PrecinctPlan derive(TargetProfile profile, Sprawl sprawl,
                                      Fortification.Demand demand,
                                      MapPlacement objective, MapPlacement attackerFrom,
                                      Lanes lanes, Standoff standoff, LandingKind landing,
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

        // The beachhead next, and it has to come before the ladders.
        //
        // A landing place is decided by arithmetic rather than by a draw — the
        // approach region, the standoff and the program's own radius — so it
        // cannot give way to anything, while a lane rung is jittered and has a
        // whole path to slide along. Seeded the other way round, the outermost
        // rung took ground the landing claim then grew over, and the marines
        // came ashore beside an enemy outpost standing inside their own landing
        // zone. Measured on reinforced-south: a lane barracks inside the
        // beachhead's own footprint. It takes no draw of its own, so the town
        // and the outlying places fall exactly where they did.
        Precinct landingPlace = null;
        if (landing != null && attackerFrom != null) {
            landingPlace = seedLanding(landing, standoff, objectivePlace, attackerFrom,
                    taken, margin, width, height);
        }

        // The ladders take what the objective and the beachhead have left.
        // They are built here and added after the garrison below, so the
        // objective stays the first programmed place in the list and everything
        // that reads objective() keeps reading the fortress.
        List<Precinct> lanePlaces = new ArrayList<>();
        List<String> unplacedLanes = new ArrayList<>();
        List<String> movedWaypoints = new ArrayList<>();
        if (lanes != null && objectivePlace != null && attackerFrom != null) {
            seedLanes(lanes, demand.rung(profile.defenseLevel()),
                    objectivePlace, objective, attackerFrom, taken, separation,
                    margin, width, height, rng, lanePlaces, unplacedLanes,
                    movedWaypoints);
        }

        // A remote map is an installation in country: adding a town to it is
        // the one thing that would stop it being one. The garrison is then the
        // somewhere the battle happens, so the settlement is only kept when
        // there is no garrison to be that.
        int mainIndex = -1;
        if (sprawl != Sprawl.REMOTE || !garrison) {
            GrownTrunkPlan.Profile main = GrownTrunkPlan.Profile.of(density, profile.link());
            // One settlement always, because a battle happens somewhere: where
            // the draw finds no room the town takes the emptiest cell there is,
            // the same standing exemption the garrison has. Without it a map
            // crowded enough to exhaust the sampler crashed generation outright
            // rather than coming out as a tighter map.
            int[] seed = placeSeed(taken, margin, separation, width, height, rng);
            if (seed == null) seed = emptiestSeed(taken, margin, width, height);
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
        out.addAll(lanePlaces);
        if (landingPlace != null) out.add(landingPlace);

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
        return new PrecinctPlan(out, attackerFrom, standoff, lanes, unplacedLanes,
                movedWaypoints);
    }

    /**
     * How much of the map one landing place's ground may be.
     *
     * <p>Between {@link #FIT} and {@link #LANE_FIT}: a beachhead is a real
     * place with an apron on it and not merely a post, but it is not the
     * installation the battle is about either. Like both of those it is a first
     * guess to be measured; on a Conquest map the program comes nowhere near
     * it, and what it is for is the small map, where the apron comes down
     * rather than swallowing the approach.
     */
    private static final float LANDING_FIT = 0.05f;

    /**
     * How much larger a programmed precinct's claim comes out than the envelope
     * its program asked for.
     *
     * <p>An allowance is the program's envelope <em>plus the road that grew
     * through it</em> ({@code PrecinctAllowance}), and the road is only known
     * after growth — which is after every seed is placed. So the landing seed
     * needs an estimate of how far a claim reaches, and this is it: measured on
     * the garrison, whose 5293-cell envelope claims 8566 to 9908 cells, a ratio
     * of 1.6 to 1.9.
     *
     * <p>Deliberately coarse, and it only has to be. What it decides is where
     * inside the approach band the beachhead sits, and the band is a third of
     * the map deep; being ten cells out moves the walk by ten cells.
     */
    private static final float CLAIM_ROAD_SLACK = 1.75f;

    /**
     * Lays the beachhead inside the attacker's own region, at the standoff.
     *
     * <p>The region is resolved by {@link ApproachRegion} — the same function
     * the spawn anchor and the berth scan read at the end of generation — with
     * the objective's grown claim, which does not exist yet, estimated from its
     * program. That estimate is the one piece of arithmetic here that is not
     * exact, and it cannot be: the claim is a consequence of growth and every
     * seed is placed before growth runs.
     *
     * <p>The seed sits its own claim-radius in from the side of the region that
     * faces the approach, so the beachhead's near edge lands on that side —
     * which is the side the berths are scanned inward from, and therefore the
     * side the walk is measured from.
     *
     * <p><b>Placement wins over separation</b>, as it does for a stated
     * objective: the marines land where the mission says they land, and a
     * beachhead nudged away from something else is a beachhead at a different
     * standoff.
     */
    private static Precinct seedLanding(LandingKind kind, Standoff standoff,
                                        Precinct objectivePlace, MapPlacement attackerFrom,
                                        List<int[]> taken, int margin,
                                        int width, int height) {
        int budget = Math.round(LANDING_FIT * width * height);
        FortressProgram program = kind.program();
        if (program.apron() > budget) program = program.withApron(budget);
        program = program.fittedTo(budget);

        ApproachRegion region = ApproachRegion.resolve(attackerFrom, standoff,
                estimatedClaim(objectivePlace, width, height), width, height);
        int radius = claimRadius(program);
        boolean forwardIsY = region.approach() == LandingPad.Approach.SOUTH
                || region.approach() == LandingPad.Approach.NORTH;
        boolean towardHigher = region.approach() == LandingPad.Approach.SOUTH
                || region.approach() == LandingPad.Approach.WEST;
        int near = switch (region.approach()) {
            case SOUTH -> region.y0();
            case NORTH -> region.y1();
            case WEST -> region.x0();
            case EAST -> region.x1();
        };
        int forward = near + (towardHigher ? radius : -radius);
        int lateral = forwardIsY
                ? (region.x0() + region.x1()) / 2
                : (region.y0() + region.y1()) / 2;
        int seedX = clamp(forwardIsY ? lateral : forward, margin, width - 1 - margin);
        int seedY = clamp(forwardIsY ? forward : lateral, margin, height - 1 - margin);

        int[] seed = {seedX, seedY};
        taken.add(seed);
        return Precinct.landing("landing", seedX, seedY,
                GrownTrunkPlan.Profile.hamlet(), kind, program);
    }

    /**
     * How far a precinct's claim is likely to reach from its seed, for a
     * decision that has to be made before any claim exists.
     */
    private static int claimRadius(FortressProgram program) {
        return Math.max(1, Math.round((float) Math.sqrt(
                program.envelopeArea() * CLAIM_ROAD_SLACK / Math.PI)));
    }

    /**
     * The ground the objective is likely to claim, as an inclusive rect around
     * its seed, or {@code null} when there is nothing to stand off from.
     */
    private static int[] estimatedClaim(Precinct objectivePlace, int width, int height) {
        if (objectivePlace == null) return null;
        int radius = claimRadius(objectivePlace.program());
        return new int[]{
                clamp(objectivePlace.seedX() - radius, 0, width - 1),
                clamp(objectivePlace.seedY() - radius, 0, height - 1),
                clamp(objectivePlace.seedX() + radius, 0, width - 1),
                clamp(objectivePlace.seedY() + radius, 0, height - 1)};
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * How much of the map one lane place's ground may be.
     *
     * <p>Far smaller than {@link #FIT}, and it has to be: nine of these stand on
     * one map beside the fortress they lead to, and a post that competed with
     * the objective for ground would be an installation rather than something on
     * the way to one. Like {@code FIT} it is a first guess to be measured, and
     * on a Conquest map neither program comes near it — what it is for is the
     * small map, where an outpost trims itself rather than swallowing the
     * approach.
     */
    private static final float LANE_FIT = 0.02f;

    /**
     * Minimum cells between two places on the same lane.
     *
     * <p>{@link #MIN_SEED_SEPARATION} is calibrated for a town and a garrison,
     * whose claims run to fifty cells and would grow into one another at
     * anything less. A lane place claims about four hundred cells — a dozen
     * cells of radius — so two of them thirty cells apart are two places, and
     * holding them sixty apart would put fewer rungs on a lane than the ladder
     * says it has. Separation from everything that is <em>not</em> a lane place
     * is unchanged: a post is kept out of the fortress and the town the way any
     * other place is.
     *
     * <p><b>The two separations only stay distinct while the two lists do.</b>
     * A seated rung belongs on the map's list of taken ground — the town and
     * the hamlets are seeded after the ladders and must keep clear of it — but
     * testing the <em>next</em> rung against that merged list finds it there at
     * the ordinary sixty and this number never applies to anything. It was dead
     * code for exactly that reason, and the symptom was a middle lane one rung
     * short on both canonical fixtures: the middle lane is the shortest, so it
     * is the one that loses a rung first.
     */
    public static final int LANE_SEED_SEPARATION = 32;

    /**
     * Where each rung stands, as a fraction of the way from the attacker's own
     * region to the objective's.
     *
     * <p><b>Ordered in path order</b>, so index 0 is the outermost rung — the
     * one nearest the beachhead — and the last is band 1 abutting the objective.
     * That is the order a lane's waypoints run in, and a rung stands on one
     * waypoint each. The shallowest is kept clear of the beachhead and the
     * deepest clear of the objective's claim, which is what the numbers are: a
     * rung at the objective end would be a suburb of the fortress and one at the
     * attacker end would be a place the marines land on top of.
     */
    private static final float[] RUNG_FRACTIONS = {0.30f, 0.55f, 0.80f};

    /**
     * How far a rung may be jittered sideways from its lane's centre line, as a
     * share of the lane's own width.
     *
     * <p>Enough that the ladder is not a ruled line and that a rung crowded out
     * by its neighbour has somewhere else to stand; not so much that a lane
     * stops reading as a lane. A quarter of a lane's width either side of the
     * waypoint is the retry's whole search space, and it is centred on the
     * waypoint rather than on the lane, so a rung stays beside the place it was
     * told to stand rather than being moved across the strip.
     */
    private static final int LANE_JITTER_SHARE = 4;

    /**
     * Lays one programmed place per rung on each lane, between the attacker's
     * region and the objective's.
     *
     * <p>The frame comes from the two placements rather than from a stated axis:
     * whichever of the two spans further is the forward direction, and the other
     * is the lateral one the lanes are cut across. That is the same pairing
     * {@code BattleSetup.conquestPlanFor} makes from its traversal axis — north
     * against south, east against west — arrived at without the generator having
     * to be told about a Conquest.
     *
     * <p><b>A lane is a route and its rungs stand on it.</b> The path is the
     * mission's where it stated one and {@link LanePath#meandering} otherwise,
     * and each rung takes one waypoint in path order — the outermost first, band
     * 1 last, the objective beyond. Before anything is seeded the path is
     * {@linkplain LanePath#fitted fitted} against the places already taken, so a
     * stated waypoint sitting inside the fortress or another lane's post slides
     * along its own route rather than being seeded on top of one; every move is
     * recorded, because a path that was quietly changed is a path nobody wrote.
     *
     * <p>What could not be seated is <b>recorded, never thrown</b>, on the same
     * law as the unbuilt program and the unplaced defences: a small map with a
     * short ladder should say how short, because a lane with two rungs on it and
     * a lane that was never asked for look identical on a finished map.
     */
    private static void seedLanes(Lanes lanes, Fortification.Strength objectiveRung,
                                  Precinct objectivePlace, MapPlacement objective,
                                  MapPlacement attackerFrom, List<int[]> taken,
                                  int separation, int margin, int width, int height,
                                  Random rng, List<Precinct> out, List<String> unplaced,
                                  List<String> moved) {
        int[] objectiveCentre = objective.centre(width, height);
        int[] attackerCentre = attackerFrom.centre(width, height);
        boolean forwardIsX = Math.abs(objectiveCentre[0] - attackerCentre[0])
                >= Math.abs(objectiveCentre[1] - attackerCentre[1]);
        int lateralExtent = forwardIsX ? height : width;
        int attackerForward = forwardIsX ? attackerCentre[0] : attackerCentre[1];
        // The objective's own seed rather than the middle of the region it was
        // asked for: the fortress landed somewhere inside that region and the
        // deepest rung is measured against where it actually is.
        int objectiveForward = forwardIsX ? objectivePlace.seedX() : objectivePlace.seedY();

        // The paths draw from a stream of their own, salted off where the
        // objective actually landed. Deterministic in the plan, because that
        // seed was drawn from the plan's rng; and it takes nothing out of that
        // rng, so laying a route does not move the settlement and the hamlets.
        // Drawn from the plan's own stream, three draws a lane would shift
        // every seed after them, and the difference between a bent lane and a
        // straight one would be measured against a different map.
        Random pathRng = new Random(objectivePlace.seedX() * 0x9E3779B97F4A7C15L
                ^ objectivePlace.seedY() * 0xC2B2AE3D27D4EB4FL);

        // The ground that was taken before any rung was: the fortress, the
        // beachhead, and whatever a stated plan had already placed. Held
        // separate from the rungs for the whole of the seeding, because the two
        // lists are tested at different separations and a merged one is tested
        // at the stricter of them — see LANE_SEED_SEPARATION. A seated rung
        // still joins `taken`, so the town and the hamlets seeded afterwards
        // keep the ordinary distance from it.
        List<int[]> settlements = List.copyOf(taken);
        List<int[]> laneSeeds = new ArrayList<>();
        // The lane separation is a relaxation of the ordinary one and must never
        // become a tightening of it. On a skirmish map `separationFor` is already
        // below thirty-two, and holding rungs further apart than settlements
        // there would be the exact inversion of what this number is for.
        int laneSeparation = Math.min(LANE_SEED_SEPARATION, separation);
        // The jitter window is bounded by the map's own margin rather than by
        // the lane's third. The third says where a lane is aimed; the margin is
        // the only real wall, and a rung whose waypoint sits near the edge of
        // its third was being handed a window of a few cells and refused for a
        // reason that had nothing to do with the ground.
        int lateralLow = margin;
        int lateralHigh = lateralExtent - 1 - margin;
        for (int lane = 0; lane < lanes.count(); lane++) {
            LaneResistance ladder = lanes.ladderFor(lane, objectiveRung);
            int laneStart = Math.max(margin,
                    LaneGeometry.startInclusive(lane, lanes.count(), lateralExtent));
            int laneEnd = Math.min(lateralExtent - 1 - margin,
                    LaneGeometry.endInclusive(lane, lanes.count(), lateralExtent));
            LanePath.Frame frame = new LanePath.Frame(forwardIsX, attackerForward,
                    objectiveForward, laneStart, laneEnd, width, height);
            LanePath stated = lanes.pathFor(lane);
            LanePath path = stated != null
                    ? stated
                    : LanePath.meandering(frame, RUNG_FRACTIONS, margin, pathRng);
            // Room measured against everything already placed — the fortress,
            // the earlier lanes' posts — which is what "inside another place's
            // claim" means before any claim has been grown. Each list at its
            // own separation, for the reason the seeding tests them separately.
            LanePath.Fit fit = path.fitted(width, height,
                    (x, y) -> hasRoom(new int[]{x, y}, settlements, separation,
                            laneSeeds, laneSeparation),
                    2 * separation);
            for (LanePath.Move move : fit.moved()) {
                moved.add("lane-" + (lane + 1) + " waypoint " + (move.index() + 1)
                        + " moved " + move.cells() + " cells along its path, "
                        + move.fromX() + "," + move.fromY() + " to "
                        + move.toX() + "," + move.toY());
            }
            List<int[]> cells = fit.path().cells(width, height);
            int jitter = Math.max(1, (laneEnd - laneStart + 1) / LANE_JITTER_SHARE);
            int rungs = LaneResistance.OUTERMOST_BAND - LaneResistance.INNERMOST_BAND + 1;
            for (int rung = 0; rung < rungs; rung++) {
                LaneResistance.Rung step =
                        ladder.at(LaneResistance.OUTERMOST_BAND - rung);
                if (step == null) continue;
                // A stated path shorter than the ladder is a lane with fewer
                // places on it than rungs, and which rungs went unplaced is
                // exactly what the diagnostic is for.
                if (rung >= cells.size()) {
                    unplaced.add("lane-" + (lane + 1) + "-band-" + step.band());
                    continue;
                }
                int[] waypoint = cells.get(rung);
                int forward = forwardIsX ? waypoint[0] : waypoint[1];
                int lateral = forwardIsX ? waypoint[1] : waypoint[0];
                String name = "lane-" + (lane + 1) + "-band-" + step.band();
                int[] seed = laneSeed(lateral, jitter, lateralLow, lateralHigh, forward,
                        forwardIsX, settlements, laneSeeds, separation, laneSeparation, rng);
                if (seed == null) {
                    unplaced.add(name);
                    continue;
                }
                taken.add(seed);
                laneSeeds.add(seed);
                out.add(Precinct.garrison(name, seed[0], seed[1],
                        GrownTrunkPlan.Profile.hamlet(),
                        step.program().fittedTo(Math.round(LANE_FIT * width * height)),
                        step.fortification()));
            }
        }
    }

    /**
     * A cell on this lane beside its waypoint, far enough from everything
     * already placed, or {@code null} when the lane has no room for it.
     *
     * <p>The jitter window is centred on the <em>waypoint's</em> lateral rather
     * than the lane's, because the waypoint is where the rung was told to stand.
     * It is still the retry's whole search space: a rung that cannot find room
     * beside its own waypoint is dropped rather than sent looking up the map.
     *
     * <p><b>What bounds that window is the map margin, not the lane.</b> A
     * lane's third is where the ladder is aimed; the margin is the only edge a
     * seed genuinely cannot cross. Clamped to the third, a rung whose waypoint
     * sat near — or, on a bent path, outside — its own strip was handed a
     * window of a few cells and refused for a reason that had nothing to do
     * with whether there was room.
     *
     * <p>Two separations, because the two questions are different. Against the
     * fortress, the beachhead and the places seeded before the ladders the
     * ordinary {@code separation} applies: those claims run to fifty cells and a
     * post inside one is a pocket rather than a place. Against the other rungs
     * of the ladder {@link #LANE_SEED_SEPARATION} applies, because a lane whose
     * rungs had to be sixty cells apart would have fewer of them than it claims
     * to. The two lists are therefore passed separately and never merged.
     */
    private static int[] laneSeed(int waypointLateral, int jitter,
                                  int lateralLow, int lateralHigh,
                                  int forward, boolean forwardIsX, List<int[]> settlements,
                                  List<int[]> laneSeeds, int separation,
                                  int laneSeparation, Random rng) {
        int lo = Math.max(lateralLow, waypointLateral - jitter);
        int hi = Math.min(lateralHigh, waypointLateral + jitter);
        if (hi < lo) return null;
        int[] best = null;
        long bestShortfall = Long.MAX_VALUE;
        for (int attempt = 0; attempt < 64; attempt++) {
            int lateral = lo + rng.nextInt(hi - lo + 1);
            int[] candidate = forwardIsX
                    ? new int[]{forward, lateral} : new int[]{lateral, forward};
            long shortfall = Math.max(
                    shortfall(candidate, settlements, separation),
                    shortfall(candidate, laneSeeds, laneSeparation));
            if (shortfall < bestShortfall) {
                bestShortfall = shortfall;
                best = candidate;
            }
            if (shortfall <= 0) break;
        }
        return bestShortfall <= 0 ? best : null;
    }

    /**
     * Whether a cell is clear of the places seeded before the ladders and of the
     * rungs already on them, each at its own separation.
     */
    private static boolean hasRoom(int[] candidate, List<int[]> settlements, int separation,
                                   List<int[]> laneSeeds, int laneSeparation) {
        return shortfall(candidate, settlements, separation) <= 0
                && shortfall(candidate, laneSeeds, laneSeparation) <= 0;
    }

    /**
     * By how much (squared) this candidate is closer to something already placed
     * than {@code separation} allows; zero or less when it is clear of them all.
     */
    private static long shortfall(int[] candidate, List<int[]> placed, int separation) {
        long worst = Long.MIN_VALUE;
        for (int[] other : placed) {
            long dx = other[0] - candidate[0];
            long dy = other[1] - candidate[1];
            worst = Math.max(worst, (long) separation * separation - (dx * dx + dy * dy));
        }
        return worst == Long.MIN_VALUE ? 0 : worst;
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
