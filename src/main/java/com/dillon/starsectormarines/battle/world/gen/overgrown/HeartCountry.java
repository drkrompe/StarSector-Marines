package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
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

    /** The places of a heart country of this form, laid out on its map. */
    public static PrecinctPlan planFor(Form form, long seed) {
        Random rng = new Random(seed ^ PLAN_SALT);
        List<PrecinctBrief> places = List.of(
                PrecinctBrief.settlement(SETTLEMENT, form == Form.BROAD_FRONT ? MapPlacement.CENTRE : VILLAGE_PLACE,
                        form == Form.BROAD_FRONT ? TOWN_GROWTH : VILLAGE_GROWTH, PrecinctCharacter.TOWN),
                PrecinctBrief.settlement(WORKINGS, form.heartEdge, WORKINGS_GROWTH, PrecinctCharacter.DEPOT));
        return PrecinctPlan.laidOut(places, form.startEdge, LandingKind.OPEN_FIELD,
                form.scale.width, form.scale.height, rng).withLandscape(landscapeFor(form, seed));
    }

    /** Wild ground the woods cover on Broad Front: wooded, with room between the groves for lanes to deploy. */
    static final float BROAD_FRONT_WOODLAND = 0.70f;
    /** Wild ground the woods cover on The Burn: thick forest, the fuel the burn line runs through. */
    static final float THE_BURN_WOODLAND = 0.80f;
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
    static final int BROOK_WIDTH = 3, BROOK_FORD_EVERY = 46, BROOK_FORD_WIDTH = 5;

    /**
     * The country between the places. Broad Front's woods have a track cut
     * along each allied lane from the farmland toward the heart, so a column
     * has a way in; The Burn's forest is thicker and crossed by rides, the
     * cleared strips a forester cuts to stop a fire, which are the firebreaks
     * the company lights its line behind.
     */
    static Landscape landscapeFor(Form form, long seed) {
        int w = form.scale.width, h = form.scale.height;
        List<Landscape.Track> tracks = new ArrayList<>();
        if (form == Form.BROAD_FRONT) {
            int top = Math.round(LANE_DEPTH * (h - 1));
            for (float across : LANE_ACROSS) {
                int x = Math.round(across * (w - 1)) - TRACK_WIDTH / 2;
                tracks.add(new Landscape.Track(x, 0, x + TRACK_WIDTH - 1, top));
            }
            return new Landscape(BROAD_FRONT_WOODLAND, tracks, List.of());
        }
        for (float depth : RIDE_DEPTH) {
            int x = Math.round(depth * (w - 1)) - TRACK_WIDTH / 2;
            tracks.add(new Landscape.Track(x, 0, x + TRACK_WIDTH - 1, h - 1));
        }
        return new Landscape(THE_BURN_WOODLAND, tracks, List.of(brook(w, h, seed)));
    }

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
        return new Landscape.Brook(path, BROOK_WIDTH, BROOK_FORD_EVERY, BROOK_FORD_WIDTH);
    }

    /** The plan's place of this name, or {@code null}. */
    public static Precinct place(PrecinctPlan plan, String name) {
        for (Precinct precinct : plan.precincts()) if (precinct.name().equals(name)) return precinct;
        return null;
    }
}
