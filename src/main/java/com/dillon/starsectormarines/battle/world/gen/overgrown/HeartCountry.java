package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBrief;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The country round a bloom's heart (`bloom-mission-arc.md`, the heart): green
 * farmland the world's military still holds along the edge the attack starts
 * from, a settlement that went dark weeks ago in the middle of the map, and the
 * reservoir's old workings at the far edge, where the heart's mother bores
 * stand. Woods fill the rest. The bloom itself is not painted on: the heart's
 * bores are opened at setup and their creep matured as weeks old
 * ({@code BattleSimulation.matureCreep}), so the overgrowth is what those bores made of this
 * country.
 *
 * <p>One plan, two shapes, because the two ways of fighting the heart want
 * different ground:
 *
 * <ul>
 * <li>{@link Form#BROAD_FRONT} is <b>wide</b>: the largest map the arc uses,
 *     the start line along its long edge, so several allied lanes and the
 *     company's own fit side by side on the way to the heart.</li>
 * <li>{@link Form#THE_BURN} is <b>deep</b>: the start line along a short edge
 *     and the heart at the far end, one approach through the forest sized for
 *     one company following its barrage.</li>
 * </ul>
 *
 * <p>The start line is an {@link LandingKind#OPEN_FIELD open-field} landing, so
 * the near edge is surveyed farmland (`mapgen-nouns.md`, Rural country): the
 * company comes down where it can see, as at Colony Breach. Laid out rather
 * than derived: the heart is the same kind of place whatever world the bloom is
 * on, and a fixture names no market.
 */
public final class HeartCountry {

    /** The settlement in the middle of the map that went dark. */
    public static final String SETTLEMENT = "settlement";
    /** The reservoir's old workings at the far edge: the heart's site. */
    public static final String WORKINGS = "workings";

    /** Which battle the country is for, which fixes its size and its axis. */
    public enum Form {
        /** Wide: allies and the company step off from the long south edge; the heart is north. */
        BROAD_FRONT(MapScale.CONQUEST, MapPlacement.SOUTH, MapPlacement.NORTH),
        /** Deep: the company steps off from the short west edge; the heart is east. */
        THE_BURN(MapScale.COLONY, MapPlacement.WEST, MapPlacement.EAST);

        public final MapScale scale;
        /** The edge the attack starts from: where the farmland and the landing are. */
        public final MapPlacement startEdge;
        /** The edge the heart stands against. */
        public final MapPlacement heartEdge;

        Form(MapScale scale, MapPlacement startEdge, MapPlacement heartEdge) {
            this.scale = scale;
            this.startEdge = startEdge;
            this.heartEdge = heartEdge;
        }

        /** Whether the attack runs along y (south to north) rather than along x. */
        public boolean advancesAlongY() { return this == BROAD_FRONT; }

        /**
         * How far a cell lies from the start edge toward the heart, 0 at the
         * start edge and 1 at the heart's.
         */
        public float depth(int x, int y) {
            return advancesAlongY()
                    ? y / (float) Math.max(1, scale.height - 1)
                    : x / (float) Math.max(1, scale.width - 1);
        }

        /**
         * How far a cell lies across the front, 0 to 1: the coordinate the
         * lanes are spread over.
         */
        public float across(int x, int y) {
            return advancesAlongY()
                    ? x / (float) Math.max(1, scale.width - 1)
                    : y / (float) Math.max(1, scale.height - 1);
        }

        /** The cell at a depth and an across, both 0 to 1. */
        public int[] cellAt(float depth, float across) {
            int w = scale.width - 1, h = scale.height - 1;
            return advancesAlongY()
                    ? new int[]{Math.round(across * w), Math.round(depth * h)}
                    : new int[]{Math.round(depth * w), Math.round(across * h)};
        }
    }

    /**
     * A dark settlement: on Broad Front's wide map a small market town, sparser
     * than the colony's; on The Burn's a village, so the forest is the country
     * the burn runs through rather than a town's yards.
     */
    private static final GrownTrunkPlan.Profile TOWN_GROWTH =
            new GrownTrunkPlan.Profile(8, 0.6f, 0.07f, 0.15f, 0.4f, 10, SettlementLink.ROAD);
    private static final GrownTrunkPlan.Profile VILLAGE_GROWTH =
            new GrownTrunkPlan.Profile(4, 0.5f, 0.06f, 0.12f, 0.3f, 8, SettlementLink.ROAD);
    /**
     * The reservoir's old workings: a few sheds and a yard on short roads of
     * their own, no road out. Growth with a hamlet's reach threw ruined
     * frontage across the whole far half of the map, which is forest.
     */
    private static final GrownTrunkPlan.Profile WORKINGS_GROWTH =
            new GrownTrunkPlan.Profile(3, 0.4f, 0.05f, 0.10f, 0.3f, 8, SettlementLink.NONE);
    /**
     * The Burn's village stands near the start, at the infection's edge, as a
     * fraction of the ground past the farmland: the brook and the heart lie
     * beyond it, so the burn reaches the village first and the brook runs
     * through forest rather than its yards.
     */
    private static final MapPlacement VILLAGE_PLACE = new MapPlacement(0.08f, 0.30f, 0.22f, 0.70f);

    private static final long PLAN_SALT = 0x4845_4152_5443_4E54L;
    private static final long BROOK_SALT = 0x4252_4F4F_4B53_414CL;

    private HeartCountry() { }

    /**
     * How built-up the country between the cordon and the heart is
     * (`heart-map-intent.md`, Density): the bloom rooted wherever it rooted.
     * Rural is farms, woods and a village; a town world puts market towns
     * across the approach; a city puts the colony's suburbs most of the way to
     * the workings, ground for infantry and walkers more than for wheels.
     */
    public enum Density {
        RURAL, TOWN, CITY;

        /** The density of a bloomed world of this vanilla market size. */
        public static Density of(int marketSize) {
            return marketSize >= CITY_SIZE ? CITY : marketSize >= TOWN_SIZE ? TOWN : RURAL;
        }

        /** Smallest vanilla market size that is a town world, and a city. */
        static final int TOWN_SIZE = 5, CITY_SIZE = 7;
    }

    /** A town world's market towns: bigger than the rural ones, still with fields between. */
    private static final GrownTrunkPlan.Profile MARKET_TOWN_GROWTH =
            new GrownTrunkPlan.Profile(10, 0.7f, 0.06f, 0.13f, 0.4f, 14, SettlementLink.ROAD);
    /** The second town a town world has across the approach. */
    static final String SECOND_TOWN = "town-2";
    /** A city's suburbs across the middle of the approach. */
    static final String SUBURBS = "suburbs";

    /** The places of a heart country of this form, laid out on its map, in open country. */
    public static PrecinctPlan planFor(Form form, long seed) {
        return planFor(form, Density.RURAL, seed);
    }

    /** The places of a heart country of this form and density, laid out on its map. */
    public static PrecinctPlan planFor(Form form, Density density, long seed) {
        Random rng = new Random(seed ^ PLAN_SALT);
        boolean broad = form == Form.BROAD_FRONT;
        GrownTrunkPlan.Profile settlementGrowth = switch (density) {
            case RURAL -> broad ? TOWN_GROWTH : VILLAGE_GROWTH;
            case TOWN -> broad ? MARKET_TOWN_GROWTH : TOWN_GROWTH;
            case CITY -> GrownTrunkPlan.Profile.city();
        };
        List<PrecinctBrief> places = new ArrayList<>(List.of(
                PrecinctBrief.settlement(SETTLEMENT, broad ? MapPlacement.CENTRE : VILLAGE_PLACE,
                        settlementGrowth, PrecinctCharacter.TOWN),
                origin(form, HeartOrigin.of(seed, form, density))));
        // Rural places stay first and alone, so an open-country plan draws exactly what it always drew.
        if (density == Density.TOWN) {
            places.add(PrecinctBrief.settlement(SECOND_TOWN, placement(form, 0.35f, 0.6f, 0.7f, 0.92f),
                    MARKET_TOWN_GROWTH, PrecinctCharacter.TOWN));
        } else if (density == Density.CITY) {
            places.add(PrecinctBrief.settlement(SUBURBS, placement(form, 0.35f, 0.62f, 0.05f, 0.95f),
                    GrownTrunkPlan.Profile.of(0.9f), PrecinctCharacter.SUBURB));
        }
        PrecinctPlan plan = PrecinctPlan.laidOut(places, form.startEdge, LandingKind.OPEN_FIELD,
                form.scale.width, form.scale.height, rng);
        Precinct workings = place(plan, WORKINGS);
        Landscape.Knoll knoll = form == Form.BROAD_FRONT && density != Density.CITY ? knoll(density) : null;
        Landscape landscape = landscapeFor(form, seed, plan, workings, knoll == null ? null : place(plan, SETTLEMENT),
                knoll);
        if (workings != null) landscape = landscape.withSealed(hollow(form, workings.seedX(), workings.seedY()));
        // The cordon ridge's ground is held before growth, as the Cordon holds its line's: in open country from
        // everything that grows, in a town from the fields alone. A city digs its forward line instead.
        if (density != Density.CITY) landscape = landscape.withHeld(List.of(ridge(form)));
        if (knoll != null) landscape = landscape.withKnolls(List.of(knoll));
        return plan.withLandscape(landscape).withLayout(HeartLayout.layout(form, density, seed));
    }

    /** The knoll Broad Front's market town stands on (`heart-map-intent.md`, The ground), named as the layout reports it. */
    public static final String KNOLL = "knoll";
    /** Half-sizes of the knoll's top round the town's seed: a town world's bigger town on a bigger top. */
    static final int KNOLL_HALF_W = 32, KNOLL_HALF_H = 24, TOWN_KNOLL_HALF_W = 40, TOWN_KNOLL_HALF_H = 28;

    /**
     * Broad Front's town on its knoll: the town grows only on the top, its gates straight roads down across the ring
     * toward the start and either side, so the lost column's holdout has height over the lanes round it.
     */
    static Landscape.Knoll knoll(Density density) {
        boolean town = density == Density.TOWN;
        return new Landscape.Knoll(KNOLL, SETTLEMENT, town ? TOWN_KNOLL_HALF_W : KNOLL_HALF_W,
                town ? TOWN_KNOLL_HALF_H : KNOLL_HALF_H, List.of(Direction.S, Direction.E, Direction.W), false);
    }

    /**
     * The far place as its origin's facility: a fenced, programmed place on the heart's edge (`heart-origins.md`); the
     * park is open ground, a green with no wall, since nobody was keeping anything in.
     */
    static PrecinctBrief origin(Form form, HeartOrigin origin) {
        if (!origin.fenced) {
            return new PrecinctBrief(WORKINGS, form.heartEdge, WORKINGS_GROWTH, origin.program, Precinct.Boundary.OPEN,
                    null, null);
        }
        return PrecinctBrief.garrison(WORKINGS, form.heartEdge, WORKINGS_GROWTH, origin.program,
                HeartOrigin.FENCE.laidSquare());
    }

    /** A placement given as depth and across shares, whichever way the form advances. */
    static MapPlacement placement(Form form, float depth0, float depth1, float across0, float across1) {
        return form.advancesAlongY() ? new MapPlacement(across0, depth0, across1, depth1)
                : new MapPlacement(depth0, across0, depth1, across1);
    }

    /** Wild ground the woods cover on Broad Front: wooded, with room between the groves for lanes to deploy. */
    static final float BROAD_FRONT_WOODLAND = 0.70f;
    /** Wild ground the woods cover on The Burn: thick forest, the fuel the burn line runs through. */
    static final float THE_BURN_WOODLAND = 0.80f;
    /**
     * Cells, at most, that a field's ragged edge of rough grass and scrub
     * wanders either side of the parcel's straight one: a burn over the fields,
     * and the creep it leaves, then follow an organic line.
     */
    static final int FIELD_MARGIN = 3;

    /** Cells across a cleared track. */
    static final int TRACK_WIDTH = 3;
    /** Where Broad Front's allied lanes run, across the front; the company's is the country road at the middle. */
    static final float[] LANE_ACROSS = {0.15f, 0.33f, 0.67f, 0.85f};

    /** Broad Front's allied lanes, as shares across the front, west to east. */
    public static float[] laneAcross() { return LANE_ACROSS.clone(); }
    /** How deep the lanes are cut toward the heart: they stop short of the workings. */
    static final float LANE_DEPTH = 0.80f;
    /** Where The Burn's firebreak rides cross its approach. */
    static final float[] RIDE_DEPTH = {0.30f, 0.47f};
    /** Where The Burn's brook crosses its approach, on average: deep in the bloom, a firebreak the creep has not crossed but at its fords. */
    static final float BROOK_DEPTH = 0.60f;
    /** How far the brook wanders either side of its line, in cells. */
    static final int BROOK_MEANDER = 14;
    /** The brook's water, its fords' spacing along it and a ford's width, in cells. */
    static final int BROOK_WIDTH = 5, BROOK_FORD_EVERY = 46, BROOK_FORD_WIDTH = 5;
    /**
     * Cells across a ride. Fire jumps up to three cells of ground with nothing
     * to burn, so a firebreak is five wide (`heart-nouns.md`, Ride).
     */
    static final int RIDE_WIDTH = 5;

    /**
     * The far edge of each of The Burn's firebreaks, as a depth into its
     * approach, near to far: each ride, then the brook at its farthest
     * meander. The barrage relights the burn just past each one.
     */
    public static float[] burnFirebreaks() {
        float w = Form.THE_BURN.scale.width - 1;
        float[] out = new float[RIDE_DEPTH.length + 1];
        for (int i = 0; i < RIDE_DEPTH.length; i++) out[i] = RIDE_DEPTH[i] + (RIDE_WIDTH / 2 + 1) / w;
        out[RIDE_DEPTH.length] = BROOK_DEPTH + (BROOK_MEANDER + BROOK_WIDTH / 2 + 2) / w;
        return out;
    }

    /**
     * The country between the places. Broad Front's woods have a track cut
     * along each allied lane from the farmland toward the heart, so a column
     * has a way in; The Burn's forest is thicker and crossed by rides, the
     * cleared strips a forester cuts to stop a fire, which are the firebreaks
     * the company lights its line behind. The brook bends round the workings'
     * hollow, when there is one, so its valley never cuts the rim, and a lane
     * that would run into the town's knoll turns along its foot to the south
     * gate's road ({@link #laneTracks}). Outcrops stand about the approach
     * ({@link #outcrops}).
     */
    static Landscape landscapeFor(Form form, long seed, PrecinctPlan plan, Precinct workings, Precinct town,
                                  Landscape.Knoll knoll) {
        int w = form.scale.width, h = form.scale.height;
        // The cordon ridge is the layout's (HeartLayout), raised before the works so the line is dug on its crest.
        List<Landscape.Track> tracks = new ArrayList<>();
        if (form == Form.BROAD_FRONT) {
            int top = Math.round(LANE_DEPTH * (h - 1));
            for (float across : LANE_ACROSS) {
                int x = Math.round(across * (w - 1)) - TRACK_WIDTH / 2;
                tracks.addAll(laneTracks(x, top, town, knoll));
            }
            return new Landscape(BROAD_FRONT_WOODLAND, tracks, List.of(), FIELD_MARGIN,
                    outcrops(form, seed, plan, knoll, null));
        }
        for (float depth : RIDE_DEPTH) {
            int x = Math.round(depth * (w - 1)) - RIDE_WIDTH / 2;
            tracks.add(new Landscape.Track(x, 0, x + RIDE_WIDTH - 1, h - 1));
        }
        Landscape.Brook brook = brook(w, h, seed);
        if (workings != null) brook = roundHollow(brook, workings.seedX(), workings.seedY());
        return new Landscape(THE_BURN_WOODLAND, tracks, List.of(brook), FIELD_MARGIN,
                outcrops(form, seed, plan, null, brook));
    }

    /** Outcrops each form tries to stand about its approach; The Burn's map is the smaller. */
    static final int BROAD_FRONT_OUTCROPS = 7, THE_BURN_OUTCROPS = 5;
    /**
     * An outcrop's thickness in cells, at least and at most, and the cells of length it runs past the shortest run a
     * ridge raises (twice its thickness and the gaps at its ends), at most: squat, a mesa rather than a ridge.
     */
    static final int OUTCROP_THICKNESS_MIN = 16, OUTCROP_THICKNESS_MAX = 22, OUTCROP_EXTRA_LENGTH = 10;
    /** Cells across an outcrop's ramp: a scout car's width, not a tank's. */
    static final int OUTCROP_RAMP_WIDTH = 7;
    /** Cells an outcrop's middle keeps from a lane, a ride or the company's road, and from the brook's line. */
    static final int OUTCROP_LANE_CLEAR = 18, OUTCROP_BROOK_CLEAR = 30;
    /** Cells an outcrop's middle keeps from a place's seed, and from another outcrop's middle. */
    static final int OUTCROP_PLACE_CLEAR = 44, OUTCROP_SPACING = 44;
    private static final long OUTCROP_SALT = 0x4f55_5443_524f_5053L;

    /**
     * Natural high ground about the approach, off every line the mission drew (owner, 2026-10-25: "hills and mesas
     * that aren't so convenient"): short bands of natural relief, each at a seeded depth between the cordon ridge
     * and the hollow and a seeded place across, lying at a seeded bearing with a bend in it, climbed from a seeded
     * side. None stands within reach of a lane, a ride, the company's road, the brook's valley, the hollow, the
     * knoll or a place's seed; whatever else generation lays across one breaks it as it breaks any ridge, and one
     * that finds no room is simply not raised. The mission's own ridge and rim stay where the story needs them;
     * these are the country's.
     */
    static List<RidgePlanner.Band> outcrops(Form form, long seed, PrecinctPlan plan, Landscape.Knoll knoll,
                                            Landscape.Brook brook) {
        int w = form.scale.width, h = form.scale.height, size = form.advancesAlongY() ? h : w;
        int acrossCells = form.advancesAlongY() ? w : h;
        Random rng = new Random(seed ^ OUTCROP_SALT);
        int longest = 2 * OUTCROP_THICKNESS_MAX + 8 + OUTCROP_EXTRA_LENGTH;
        float near = (form.advancesAlongY() ? BROAD_FRONT_RIDGE_DEPTH : THE_BURN_RIDGE_DEPTH)
                + (RIDGE_THICKNESS + 2f * longest / 3) / size;
        float far = 0.8f;
        int wanted = form.advancesAlongY() ? BROAD_FRONT_OUTCROPS : THE_BURN_OUTCROPS;
        List<int[]> middles = new ArrayList<>();
        List<RidgePlanner.Band> out = new ArrayList<>();
        for (int attempt = 0; attempt < wanted * 40 && out.size() < wanted; attempt++) {
            float depth = near + rng.nextFloat() * (far - near), across = 0.06f + rng.nextFloat() * 0.88f;
            int thickness = OUTCROP_THICKNESS_MIN + rng.nextInt(OUTCROP_THICKNESS_MAX - OUTCROP_THICKNESS_MIN + 1);
            int length = 2 * thickness + 8 + rng.nextInt(OUTCROP_EXTRA_LENGTH + 1);
            double bearing = rng.nextDouble() * Math.PI, bend = (rng.nextDouble() - .5) * length / 6;
            int side = rng.nextBoolean() ? 1 : -1;
            int[] c = form.cellAt(depth, across);
            if (!outcropClear(form, plan, knoll, brook, c, acrossCells, size, depth, across, middles)) continue;
            double ux = Math.cos(bearing), uy = Math.sin(bearing), half = length / 2.0;
            ElevationPlan.Point a = new ElevationPlan.Point(clampCell(c[0] - ux * half, w), clampCell(c[1] - uy * half, h));
            ElevationPlan.Point m = new ElevationPlan.Point(clampCell(c[0] - uy * bend, w), clampCell(c[1] + ux * bend, h));
            ElevationPlan.Point b = new ElevationPlan.Point(clampCell(c[0] + ux * half, w), clampCell(c[1] + uy * half, h));
            out.add(new RidgePlanner.Band(List.of(a, m, b), thickness, side, OUTCROP_RAMP_WIDTH));
            middles.add(c);
        }
        return out;
    }

    /** Whether an outcrop's middle at cell {@code c} keeps clear of every line, place and outcrop it must. */
    private static boolean outcropClear(Form form, PrecinctPlan plan, Landscape.Knoll knoll, Landscape.Brook brook,
                                        int[] c, int acrossCells, int size, float depth, float across,
                                        List<int[]> middles) {
        if (form.advancesAlongY()) {
            for (float lane : LANE_ACROSS) if (Math.abs(across - lane) * acrossCells < OUTCROP_LANE_CLEAR) return false;
            // The company's road up the middle.
            if (Math.abs(across - 0.5f) * acrossCells < OUTCROP_LANE_CLEAR) return false;
        } else {
            for (float ride : RIDE_DEPTH) if (Math.abs(depth - ride) * size < OUTCROP_LANE_CLEAR) return false;
        }
        if (brook != null) {
            for (int[] p : brook.line()) if (Math.hypot(p[0] - c[0], p[1] - c[1]) < OUTCROP_BROOK_CLEAR) return false;
        }
        for (Precinct place : plan.precincts()) {
            double reach = OUTCROP_PLACE_CLEAR;
            if (place.name().equals(WORKINGS)) reach = HOLLOW_RADIUS + HOLLOW_RIM + OUTCROP_PLACE_CLEAR / 2.0;
            if (knoll != null && place.name().equals(knoll.place())) {
                reach = Math.hypot(knoll.halfWidth(), knoll.halfHeight()) + knoll.ring() + OUTCROP_PLACE_CLEAR / 2.0;
            }
            if (Math.hypot(place.seedX() - c[0], place.seedY() - c[1]) < reach) return false;
        }
        Precinct landing = plan.landingPlace();
        if (landing != null && Math.hypot(landing.seedX() - c[0], landing.seedY() - c[1]) < OUTCROP_PLACE_CLEAR) {
            return false;
        }
        for (int[] m : middles) if (Math.hypot(m[0] - c[0], m[1] - c[1]) < OUTCROP_SPACING) return false;
        return true;
    }

    private static double clampCell(double v, int size) {
        return Math.max(2, Math.min(size - 3, v));
    }

    /**
     * One lane's track from the start edge to {@code top}, its west side at
     * {@code x}. A lane whose course meets the knoll's ground (its top and ring,
     * round the town's seed) stops a cell short of it and turns along the foot
     * to the south gate, whose road runs down from the ring at the seed's line:
     * a column on it reaches the town by the gate, not a cliff.
     */
    static List<Landscape.Track> laneTracks(int x, int top, Precinct town, Landscape.Knoll knoll) {
        int x1 = x + TRACK_WIDTH - 1;
        if (town == null || knoll == null) return List.of(new Landscape.Track(x, 0, x1, top));
        int kx0 = town.seedX() - knoll.halfWidth() - knoll.ring(), kx1 = town.seedX() + knoll.halfWidth() + knoll.ring();
        int foot = town.seedY() - knoll.halfHeight() - knoll.ring() - 1;
        if (x1 < kx0 || x > kx1 || top <= foot || foot < TRACK_WIDTH) return List.of(new Landscape.Track(x, 0, x1, top));
        int gate = town.seedX();
        return List.of(new Landscape.Track(x, 0, x1, foot),
                new Landscape.Track(Math.min(x, gate), foot - TRACK_WIDTH + 1, Math.max(x1, gate), foot));
    }

    /**
     * Where the cordon's ridge stands, as a depth into the approach: the last
     * high ground just past the farmland, which the line was dug along and
     * which looks down into the bloom (`heart-map-intent.md`). Far enough out
     * that the farm road along the farmland's edge stays clear of its apron;
     * The Burn's lies short of its village, which stands just beyond.
     */
    static final float BROAD_FRONT_RIDGE_DEPTH = 0.27f, THE_BURN_RIDGE_DEPTH = 0.215f;
    /** Cells through the ridge, start side to bloom side. */
    static final int RIDGE_THICKNESS = 14;
    /** Cells across each ridge segment's ramp: wide enough for a tank to climb it. */
    static final int RIDGE_RAMP_WIDTH = 9;

    /**
     * The cordon's ridge across the whole front, climbed from the start side.
     * Roads, lanes, rides and the settlement break it into passes. A berm the
     * colony graded years ago as planetary defence works, not a natural rise
     * (owner, 2026-10-25): straight runs, cut square at every pass.
     */
    static RidgePlanner.Band ridge(Form form) {
        int w = form.scale.width, h = form.scale.height;
        if (form.advancesAlongY()) {
            int y0 = Math.round(BROAD_FRONT_RIDGE_DEPTH * (h - 1));
            return RidgePlanner.Band.rect(0, y0, w - 1, y0 + RIDGE_THICKNESS - 1, true, -1, RIDGE_RAMP_WIDTH).berm();
        }
        int x0 = Math.round(THE_BURN_RIDGE_DEPTH * (w - 1));
        return RidgePlanner.Band.rect(x0, 0, x0 + RIDGE_THICKNESS - 1, h - 1, false, -1, RIDGE_RAMP_WIDTH).berm();
    }

    /**
     * Cells from the workings' seed to the middle of the hollow's rim: far enough that the rim and its apron clear
     * the corners of the biggest fenced origin's yard, which a tighter rim breaks on (The Burn's at 44 lost most of
     * its west face to them).
     */
    static final int HOLLOW_RADIUS = 52;
    /** Cells through the hollow's rim. */
    static final int HOLLOW_RIM = 10;
    /** Degrees either side of the start's direction that the rim wraps round the workings. */
    static final double HOLLOW_SWEEP = 100;
    /**
     * Degrees between the rim's passes: one straight toward the start. Roads and
     * routes generation lays across the rim break it into more.
     */
    static final double HOLLOW_PASS_EVERY = 180;
    /** Cells across each pass through the rim. */
    static final int HOLLOW_PASS = 16;
    /** Cells across each rim segment's ramp, climbed from outside. */
    static final int HOLLOW_RAMP_WIDTH = 7;

    /**
     * The hollow the workings sit in (`heart-map-intent.md`): a rim of high
     * ground wrapped round the workings on the side the attack comes from,
     * broken by a pass straight toward the start and wherever a road or route crosses it,
     * so the last stretch to the heart goes down into it. Each rim segment is
     * climbed from outside; its inner face is a cliff over the yard. Held
     * before the places grow ({@link Landscape#held}), because the workings'
     * roads and yards would otherwise leave no room for it.
     */
    static List<RidgePlanner.Band> hollow(Form form, int cx, int cy) {
        // The direction from the workings toward the start edge.
        double facing = form.advancesAlongY() ? -90 : 180;
        double pass = Math.toDegrees(HOLLOW_PASS / (double) HOLLOW_RADIUS) / 2;
        List<RidgePlanner.Band> out = new ArrayList<>();
        for (double from = -HOLLOW_SWEEP; from < HOLLOW_SWEEP; ) {
            double next = Math.floor((from + pass) / HOLLOW_PASS_EVERY + 1) * HOLLOW_PASS_EVERY;
            double to = Math.min(HOLLOW_SWEEP, next);
            double a = from == -HOLLOW_SWEEP ? from : from + pass;
            double b = to == HOLLOW_SWEEP ? to : to - pass;
            if (b > a) out.add(arc(cx, cy, HOLLOW_RADIUS, facing + a, facing + b));
            from = to;
        }
        return out;
    }

    /** One rim segment: an arc travelled counter-clockwise, so its left is the inside and it is climbed from the right. */
    private static RidgePlanner.Band arc(int cx, int cy, int radius, double fromDeg, double toDeg) {
        List<ElevationPlan.Point> path = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil(Math.toRadians(toDeg - fromDeg) * radius / 4));
        for (int i = 0; i <= steps; i++) {
            double t = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            path.add(new ElevationPlan.Point(cx + radius * Math.cos(t), cy + radius * Math.sin(t)));
        }
        return new RidgePlanner.Band(path, HOLLOW_RIM, -1, HOLLOW_RAMP_WIDTH);
    }

    /** Cells through each side of The Burn's brook valley. */
    static final int VALLEY_SIDE = 10;

    /**
     * The Burn's brook: from the north edge to the south across the approach,
     * wandering a little either side of its line, seeded.
     */
    static Landscape.Brook brook(int w, int h, long seed) {
        Random rng = new Random(seed ^ BROOK_SALT);
        int line = Math.round(BROOK_DEPTH * (w - 1));
        List<int[]> path = new ArrayList<>();
        int x = line;
        for (int y = 0; y < h; y += 20) {
            path.add(new int[]{x, y});
            x = Math.max(line - BROOK_MEANDER, Math.min(line + BROOK_MEANDER, x + rng.nextInt(13) - 6));
        }
        path.add(new int[]{x, h - 1});
        return new Landscape.Brook(path, BROOK_WIDTH, BROOK_FORD_EVERY, BROOK_FORD_WIDTH, VALLEY_SIDE);
    }

    /** Cells from the brook's middle to the outer foot of a valley side, its apron included (`OutdoorLandscapeStage`). */
    static final int BROOK_VALLEY_REACH = 25;
    /**
     * Cells, at least, from the workings' seed to the brook's middle: the
     * hollow out to the rim's outer foot and its apron, then the brook's valley
     * and a margin, so the valley side stops short of the rim.
     */
    static final int BROOK_HOLLOW_CLEARANCE = HOLLOW_RADIUS + HOLLOW_RIM / 2
            + PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1 + BROOK_VALLEY_REACH + 2;
    /** Cells, along the brook's line, between the points of a stretch bent round the hollow. */
    private static final int BROOK_BEND_STEP = 4;

    /**
     * The brook bent round the hollow centred on {@code (cx, cy)}: a stretch that
     * would come within {@link #BROOK_HOLLOW_CLEARANCE} is pushed out to that
     * circle on whichever side it ran, west of the workings or east; the rest of
     * its course is the brook as it was.
     */
    static Landscape.Brook roundHollow(Landscape.Brook brook, int cx, int cy) {
        List<int[]> path = brook.path();
        List<int[]> out = new ArrayList<>();
        out.add(clear(path.get(0), cx, cy));
        for (int i = 0; i + 1 < path.size(); i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            int steps = Math.max(1, Math.abs(b[1] - a[1]) / BROOK_BEND_STEP);
            List<int[]> bent = new ArrayList<>();
            boolean moved = false;
            for (int k = 1; k <= steps; k++) {
                int[] p = {Math.round(a[0] + (b[0] - a[0]) * k / (float) steps), Math.round(a[1] + (b[1] - a[1]) * k / (float) steps)};
                int[] c = clear(p, cx, cy);
                moved |= c[0] != p[0];
                bent.add(c);
            }
            if (moved) out.addAll(bent);
            else out.add(b);
        }
        return new Landscape.Brook(out, brook.width(), brook.fordEvery(), brook.fordWidth(), brook.banks());
    }

    /** The point, or the point on the clearance circle level with it on its own side when it lies inside. */
    private static int[] clear(int[] p, int cx, int cy) {
        int dy = p[1] - cy;
        long r2 = (long) BROOK_HOLLOW_CLEARANCE * BROOK_HOLLOW_CLEARANCE;
        if ((long) dy * dy >= r2) return p;
        int half = (int) Math.ceil(Math.sqrt(r2 - (long) dy * dy));
        if (Math.abs(p[0] - cx) >= half) return p;
        return new int[]{p[0] < cx ? cx - half : cx + half, p[1]};
    }

    /** The plan's place of this name, or {@code null}. */
    public static Precinct place(PrecinctPlan plan, String name) {
        for (Precinct precinct : plan.precincts()) if (precinct.name().equals(name)) return precinct;
        return null;
    }
}
