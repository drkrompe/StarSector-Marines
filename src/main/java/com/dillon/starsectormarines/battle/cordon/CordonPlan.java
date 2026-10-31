package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.layout.Airfield;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Endpoint;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Line;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.LineStyle;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Phase;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Ridge;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBrief;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * The Cordon's map, laid out ({@code cordon-nouns.md}, The map): an open-field
 * landing in surveyed country against the rear edge, hamlets behind the line, a
 * row of field redoubts across the map's long axis, and the colony the bloom
 * came out of in front of them.
 *
 * <p>Every place is stated by its depth from the rear rather than by a compass
 * point, because which edge is the rear is the seed's: a depth of 0 is the
 * rear side of the ground the landing's country leaves, 1 the far edge. The
 * redoubts are programmed places, so they claim their ground first and the
 * hamlets and the colony grow round them; they are listed west to east, so a
 * redoubt's number is its order across the line.
 *
 * <p>The Road keeps the colony's civil-defence compound (its people are in it);
 * in the Line the compound fell with the colony and is not built.
 */
public final class CordonPlan {

    /** Redoubts across the line. */
    public static final int REDOUBTS = 4;
    /** The precinct name of redoubt {@code n} (1-based, west to east) is this plus {@code n}. */
    public static final String REDOUBT_PLACE = "cordon-redoubt-";
    /** Wall left in a field redoubt's panel: field grade, inside what a Brute can break. */
    public static final int REDOUBT_WALL_HP = 120;
    /** Ways in: one toward the front road, one toward the rear. */
    public static final int REDOUBT_GATES = 2;

    /** Depth band of the line, as fractions of the ground the country leaves. */
    static final float LINE_NEAR = 0.36f, LINE_FAR = 0.42f;

    /**
     * A redoubt's own roads: short arms, enough to cross its wall into the
     * roads either side and no more (the civil-defence compound's growth).
     */
    private static final GrownTrunkPlan.Profile REDOUBT_GROWTH =
            new GrownTrunkPlan.Profile(0, 0f, 0.04f, 0.06f, 0f, 4, SettlementLink.NONE);

    /**
     * The colony: a town, but a frontier one, its streets reaching a twentieth
     * to a ninth of the map's short side rather than the third a town's arms
     * run (which on a 560x336 map grew one town across the whole of it).
     */
    private static final GrownTrunkPlan.Profile COLONY_GROWTH =
            new GrownTrunkPlan.Profile(7, 0.7f, 0.05f, 0.11f, 0.4f, 12, SettlementLink.ROAD);
    /** A hamlet in open country: a few short roads. */
    private static final GrownTrunkPlan.Profile HAMLET_GROWTH =
            new GrownTrunkPlan.Profile(3, 0.5f, 0.04f, 0.09f, 0.3f, 8, SettlementLink.ROAD);
    /** The logistics base's place name; not a rear hamlet, so its people do not walk out. */
    public static final String BASE_PLACE = "logistics-base";
    /** Where the base sits: across the middle of the map, between the rear edge and the line. */
    static final float BASE_X0 = 0.38f;
    static final float BASE_X1 = 0.62f;
    static final float BASE_NEAR = 0.04f;
    static final float BASE_FAR = 0.26f;
    /**
     * The allies' air base's name ({@code cordon-nouns.md}, Air base): a field lot of its own in a corner of the rear,
     * on the side away from the front's installation, its strip along the edge toward the front.
     */
    public static final String AIR_BASE_PLACE = "air-base";
    /**
     * Cells from the rear edge, and from the near side edge, to the air base's reservation: a rear corner, behind the
     * hamlets' ground and far across from the relief corridor, which leaves the rear edge behind the logistics base.
     */
    static final int AIR_BASE_REAR = 4, AIR_BASE_SIDE = 6;
    /** A fence, not a redoubt's wall: the base is not meant to be fought from. */
    private static final int BASE_GATES = 2;
    private static final int BASE_FENCE_HP = 60;
    /**
     * The town compound's truck yard on the Road ({@code cordon-nouns.md}, Cut-off town): its rear gate, where the
     * corridor comes in, nine cells wide (at most ten, or the wall reading takes the gap for no gate), and inside it
     * a yard thirteen along the wall by nine deep, room for a truck three wide to drive in, turn and stand to load,
     * with seven cells of open ground beside it for the people waiting to board. Untuned.
     */
    static final int YARD_GATE = 9, YARD_DEPTH = 9, YARD_BREADTH = 13, YARD_STAGING = 7;
    private static final long SEED_SALT = 0x434F52444F4E504CL;
    /** The rear's side, its own splittable draw (see {@code BattleSetup.colonyPlanFor}). */
    private static final long REAR_SIDE_SALT = 0x5245415253494445L;

    private CordonPlan() {}

    /** Whether the rear is the south edge for this seed (else the north). */
    public static boolean rearSouth(long seed) {
        return new SplittableRandom(seed ^ REAR_SIDE_SALT).nextBoolean();
    }

    /**
     * How built-up the country the line is drawn across is: the colony's world
     * sets it (a frontier farm world, a settled one, a core world's sprawl).
     * Rural is the open country the Cordon was first laid out in; a town world
     * grows its hamlets into towns and puts a village on the line itself; an
     * urban one is built across the whole width, so the redoubts stand as
     * fortified blocks in streets and the front is suburbs, ground for
     * infantry and walkers more than for wheels.
     */
    public enum Density {
        RURAL(null, COLONY_GROWTH, HAMLET_GROWTH, true),
        TOWN(new GrownTrunkPlan.Profile(5, 0.6f, 0.04f, 0.1f, 0.4f, 10, SettlementLink.ROAD),
                new GrownTrunkPlan.Profile(10, 0.7f, 0.06f, 0.13f, 0.4f, 14, SettlementLink.ROAD),
                new GrownTrunkPlan.Profile(6, 0.6f, 0.05f, 0.11f, 0.4f, 10, SettlementLink.ROAD), true),
        URBAN(GrownTrunkPlan.Profile.city(),
                GrownTrunkPlan.Profile.city(),
                GrownTrunkPlan.Profile.of(0.9f, SettlementLink.ROAD), false);

        /** Growth of the built ground the line runs through, or null where it runs through fields. */
        final GrownTrunkPlan.Profile lineGrowth;
        final GrownTrunkPlan.Profile colonyGrowth;
        final GrownTrunkPlan.Profile placeGrowth;
        /**
         * Whether the line stands on a ridge. A city's height is its buildings (`elevation-nouns.md`): an urban
         * line is built across the whole width, every stretch of a ridge there would be broken, and ground held
         * for one would gut the streets the line is fought in.
         */
        final boolean ridge;

        /** Smallest vanilla market size that is a town world, and a city. */
        static final int TOWN_SIZE = 5, URBAN_SIZE = 7;

        /** The density of a bloomed world of this vanilla market size. */
        public static Density of(int marketSize) {
            return marketSize >= URBAN_SIZE ? URBAN : marketSize >= TOWN_SIZE ? TOWN : RURAL;
        }

        Density(GrownTrunkPlan.Profile lineGrowth, GrownTrunkPlan.Profile colonyGrowth,
                GrownTrunkPlan.Profile placeGrowth, boolean ridge) {
            this.lineGrowth = lineGrowth;
            this.colonyGrowth = colonyGrowth;
            this.placeGrowth = placeGrowth;
            this.ridge = ridge;
        }
    }

    /** The plan for one Cordon battle in open country. */
    public static PrecinctPlan planFor(CordonVariant variant, int width, int height, long seed) {
        return planFor(variant, Density.RURAL, width, height, seed);
    }

    /** The plan for one Cordon battle in country as built-up as {@code density}. */
    public static PrecinctPlan planFor(CordonVariant variant, Density density, int width, int height, long seed) {
        Random rng = new Random(seed ^ SEED_SALT);
        boolean south = rearSouth(seed);
        boolean installationEast = rng.nextBoolean();
        List<PrecinctBrief> places = new ArrayList<>();
        // The line first: programmed places pool their claims first whatever the order,
        // and listing them first keeps a redoubt's index its number less one.
        Fortification wall = new Fortification(REDOUBT_GATES, REDOUBT_WALL_HP).laidSquare();
        for (int i = 0; i < REDOUBTS; i++) {
            float x0 = (i + 0.3f) / REDOUBTS, x1 = (i + 0.7f) / REDOUBTS;
            places.add(PrecinctBrief.garrison(REDOUBT_PLACE + (i + 1),
                    band(south, x0, x1, LINE_NEAR, LINE_FAR), REDOUBT_GROWTH,
                    FortressProgram.fieldRedoubt(), wall));
        }
        // The front: the colony, overgrown, with its installation and suburb either side.
        places.add(PrecinctBrief.settlement("colony", band(south, 0.38f, 0.62f, 0.72f, 0.92f),
                density.colonyGrowth, PrecinctCharacter.TOWN));
        places.add(PrecinctBrief.settlement("installation",
                installationEast ? band(south, 0.72f, 0.95f, 0.55f, 0.85f) : band(south, 0.05f, 0.28f, 0.55f, 0.85f),
                density.placeGrowth, PrecinctCharacter.DEPOT));
        places.add(PrecinctBrief.settlement("suburb",
                installationEast ? band(south, 0.05f, 0.28f, 0.55f, 0.85f) : band(south, 0.72f, 0.95f, 0.55f, 0.85f),
                density.placeGrowth, PrecinctCharacter.SUBURB));
        // The rear: the hamlets behind the line whose people walk out.
        places.add(PrecinctBrief.settlement("rear-west", band(south, 0.1f, 0.35f, 0.04f, 0.2f),
                density.placeGrowth, PrecinctCharacter.HAMLET));
        places.add(PrecinctBrief.settlement("rear-east", band(south, 0.65f, 0.9f, 0.04f, 0.2f),
                density.placeGrowth, PrecinctCharacter.HAMLET));
        if (density.lineGrowth != null) {
            // Built ground the line was drawn through: a village between the middle redoubts, or,
            // in a city, streets across the whole width either side of the line.
            float x0 = density == Density.URBAN ? 0.05f : 0.4f, x1 = density == Density.URBAN ? 0.95f : 0.6f;
            places.add(PrecinctBrief.settlement("line-ground", band(south, x0, x1, 0.3f, 0.5f),
                    density.lineGrowth, density == Density.URBAN ? PrecinctCharacter.TOWN : PrecinctCharacter.HAMLET));
        }
        // The allies' logistics base, behind the middle of the line: where their machines are built
        // and their squads form up to be carried forward.
        places.add(PrecinctBrief.garrison(BASE_PLACE, band(south, BASE_X0, BASE_X1, BASE_NEAR, BASE_FAR),
                REDOUBT_GROWTH, FortressProgram.logisticsBase(),
                new Fortification(BASE_GATES, BASE_FENCE_HP).laidSquare()));
        if (variant == CordonVariant.ROAD) {
            // The colony's people are still in its compound, on the town's near side where the road comes in; the
            // convoys drive in through its rear gate to a yard inside it.
            Side rear = south ? Side.SOUTH : Side.NORTH;
            places.add(PrecinctBrief.garrison(CivilDefenceCompound.PLACE, band(south, 0.42f, 0.58f, 0.6f, 0.7f),
                    REDOUBT_GROWTH, FortressProgram.civilDefence(),
                    new Fortification(CivilDefenceCompound.GATES, CivilDefenceCompound.WALL_HP).laidSquare()
                            .withYard(new Fortification.GateYard(rear, YARD_GATE, YARD_DEPTH, YARD_BREADTH,
                                    YARD_STAGING))));
        }
        PrecinctPlan plan = PrecinctPlan.laidOut(places, south ? MapPlacement.SOUTH : MapPlacement.NORTH,
                LandingKind.OPEN_FIELD, width, height, rng).withLayout(layout(south, density, variant, width, height, installationEast));
        if (!density.ridge) return plan;
        List<int[]> seeds = new ArrayList<>();
        for (Precinct r : redoubts(plan)) seeds.add(new int[]{r.seedX(), r.seedY(), r.seedX(), r.seedY()});
        List<RidgePlanner.Band> held = heldBands(seeds, new int[]{0, south ? 1 : -1}, width);
        // In open country the band is held from everything that grows; a town's places grow as they would and only
        // its fields keep off (held from its growth, five of the pooled town maps lost their Cordon). Round each
        // redoubt, in either, the ground its terrace's cliff stands in is kept from the other places.
        Landscape landscape = density == Density.RURAL ? plan.landscape().withHeld(held)
                : plan.landscape().withUnploughed(held);
        return plan.withLandscape(landscape.withKept(crestGround(seeds)));
    }

    /**
     * The allies' air base ({@code cordon-nouns.md}, Air base): an allied {@link AirbaseLot.Size#FIELD} lot (a strip,
     * three berths, two sheds: room for the wing's four airframes, where a station's sixty cells would crowd the rear
     * hamlets), held from before the places grow, in the rear corner on the side away from the front's installation,
     * its strip along the edge toward the front.
     */
    static Airfield airBase(boolean rearSouth, boolean installationEast, int width, int height) {
        AirbaseLot.Facing facing = rearSouth ? AirbaseLot.Facing.NORTH : AirbaseLot.Facing.SOUTH;
        AirbaseLot.Size size = AirbaseLot.Size.FIELD;
        int spanX = AirbaseLot.reservedSpanX(size, facing), spanY = AirbaseLot.reservedSpanY(size, facing);
        int x0 = installationEast ? AIR_BASE_SIDE : width - AIR_BASE_SIDE - spanX;
        int y0 = rearSouth ? AIR_BASE_REAR : height - AIR_BASE_REAR - spanY;
        return new Airfield(AIR_BASE_PLACE, x0, y0, facing, size, LandingPad.Purpose.ALLIED_AIRFIELD, false);
    }

    /**
     * Cells either side of a redoubt's seed kept open for its terrace: half its claim, the terrace's margin, the
     * cliff and its apron, and the landscape's two-cell reservation round whatever grows beside it.
     */
    static final int CREST_HALF = 28;

    /** The ground kept round each redoubt's seed for the terrace that stands it on the crest. */
    static List<Landscape.Track> crestGround(List<int[]> seeds) {
        List<Landscape.Track> out = new ArrayList<>();
        for (int[] s : seeds) out.add(new Landscape.Track(s[0] - CREST_HALF, s[1] - CREST_HALF, s[0] + CREST_HALF,
                s[1] + CREST_HALF));
        return out;
    }

    /** The ridge the line stands on ({@code cordon-map-intent.md}, The line on the high ground). */
    public static final String RIDGE = "line-ridge";
    /** Cells through the ridge, rear to front: room on its crest for the trench, its parapet and its wire. */
    static final int RIDGE_THICKNESS = 16;
    /**
     * Cells the ridge's middle lies in front of the picket courses' line, so the trench and the ground worked
     * behind it stand on the crest and the wire just behind its front edge.
     */
    static final int RIDGE_FORWARD = 1;
    /** Cells across each ridge segment's ramp, climbed from the rear: wide enough for a tank. */
    static final int RIDGE_RAMP_WIDTH = 9;
    /** Cells through the ground held for the ridge before the places grow: the ridge's own, with room for a claim off its seed. */
    static final int HELD_THICKNESS = 24;
    /** Cells kept from the map's side edges by the ridge's path. */
    static final int RIDGE_EDGE = 2;

    /**
     * The ridge along the line, an engineered berm ({@link RidgePlanner.Relief#BERM}): from the map's west edge to
     * its east, through each redoubt at the depth of its middle and straight between neighbours' facing edges, which
     * is the course each picket line is dug along, lying {@link #RIDGE_FORWARD} cells to the front of it. Climbed
     * from the rear.
     * The redoubts and the roads through the line break it into passes when it
     * is raised.
     *
     * @param bounds each redoubt's claimed ground west to east, {x0, y0, x1, y1} inclusive
     * @param front  the unit step toward the front, {0, +1} or {0, -1}
     */
    static RidgePlanner.Band ridgeBand(List<int[]> bounds, int[] front, int width) {
        return new RidgePlanner.Band(path(bounds, front, width), RIDGE_THICKNESS, -front[1], RIDGE_RAMP_WIDTH).berm();
    }

    /**
     * The ground held for the ridge before the places grow: the same course
     * through the redoubts' seeds, thick enough that the ridge raised off their
     * claims lies inside it. None unless every redoubt has its seed.
     */
    static List<RidgePlanner.Band> heldBands(List<int[]> seeds, int[] front, int width) {
        if (seeds.size() != REDOUBTS) return List.of();
        return List.of(new RidgePlanner.Band(path(seeds, front, width), HELD_THICKNESS, -front[1], RIDGE_RAMP_WIDTH)
                .berm());
    }

    /**
     * West edge, each box's west and east edge at its middle depth, east edge;
     * shifted {@link #RIDGE_FORWARD} cells to the front.
     */
    private static List<ElevationPlan.Point> path(List<int[]> bounds, int[] front, int width) {
        List<ElevationPlan.Point> path = new ArrayList<>();
        double shift = front[1] * (double) RIDGE_FORWARD;
        int[] first = bounds.get(0), last = bounds.get(bounds.size() - 1);
        path.add(new ElevationPlan.Point(RIDGE_EDGE, (first[1] + first[3]) / 2 + shift));
        for (int[] b : bounds) {
            double y = (b[1] + b[3]) / 2 + shift;
            path.add(new ElevationPlan.Point(b[0] - 1, y));
            path.add(new ElevationPlan.Point(b[2] + 1, y));
        }
        path.add(new ElevationPlan.Point(width - 1 - RIDGE_EDGE, (last[1] + last[3]) / 2 + shift));
        return path;
    }

    /** The terrace that stands redoubt {@code n} on the ridge's crest is this plus the redoubt's place name. */
    public static final String CREST = "crest-";
    /**
     * Cells a redoubt's terrace box lies inside its claimed ground: the claim is the wall with a three-cell lawn
     * ring round it, and the terrace's own margin puts that ring on the top anyway, so the box is the wall and one
     * cell beyond it. Two cells less on every side is two cells more for the cliff between the redoubt and whatever
     * grew beside it.
     */
    static final int CREST_INSET = 2;

    /**
     * The ridge resolved off the redoubts' claimed ground, or none when a redoubt has none. It carries every
     * redoubt on its crest ({@code cordon-map-intent.md}, The line on the high ground): each claimed box is a
     * terrace the ridge runs on into, its roads ramped down from its gates, and a map on which one cannot stand is
     * not a Cordon map.
     */
    static List<Piece> ridge(LayoutGround ground, int[] front) {
        List<int[]> bounds = new ArrayList<>();
        List<TerracePlanner.Terrace> terraces = new ArrayList<>();
        for (int n = 1; n <= REDOUBTS; n++) {
            int[] b = ground.claimBounds(REDOUBT_PLACE + n);
            if (b == null) return List.of();
            bounds.add(b);
            // Supplied from the rear: its cliff there is ramped whether or not the supply road was laid to it.
            terraces.add(new TerracePlanner.Terrace(CREST + REDOUBT_PLACE + n, b[0] + CREST_INSET, b[1] + CREST_INSET,
                    b[2] - CREST_INSET, b[3] - CREST_INSET, true, rear(front)));
        }
        return List.of(new Ridge(RIDGE, ridgeBand(bounds, front, ground.width()), false, terraces));
    }

    /** The side of a redoubt facing the rear, where its supply comes from: y is up, so a front up the map is a rear down it. */
    static TerracePlanner.Edge rear(int[] front) {
        return front[1] > 0 ? TerracePlanner.Edge.SOUTH : TerracePlanner.Edge.NORTH;
    }

    /** The layout's picket courses, resolved once the redoubts have claimed their ground. */
    public static final String PICKETS = "picket";
    /** The picket course between redoubts {@code n} and {@code n + 1} is this plus "n-(n+1)". */
    public static final String PICKET_LINE = "picket-";
    /** The switch line (beat L3), resolved behind the middle redoubt with the most room. */
    public static final String SWITCH = "switch";
    public static final String SWITCH_LINE = "switch-line";
    /** The beats' structures (farmstead, ammo dump, bell square, granary, observation posts). */
    public static final String SET_PIECES = "set-pieces";
    /** The Road's pieces on its corridor (the wreck and the gap's sandbag positions). */
    public static final String CORRIDOR_PIECES = "corridor-pieces";
    /**
     * The wreck across the Road's corridor past the gap (beat R1). The layout holds its ground; laid as a block it
     * closes the road to trucks until a dozer clears it, which acceptance proves the corridor through.
     */
    public static final String CORRIDOR_WRECK = "corridor-wreck";
    /** A city's supply roads, followed along its streets with the works. */
    public static final String SUPPLY_STREETS = "supply-streets";
    /** A city's set pieces in standing buildings (the tenement and the observation posts). */
    public static final String CITY_PIECES = "city-pieces";
    /** The eruption site (beat L4), resolved behind the middle of the line; in a city, a cleared lot. */
    public static final String ERUPTION = "eruption";
    public static final String ERUPTION_SITE = "eruption-site";
    /** The supply road from the base to redoubt {@code n} (beat L2) is this plus n. */
    public static final String SUPPLY_ROAD = "supply-";
    /** The Road's second drivable way through the line, beside redoubt 2 (beat R5). */
    public static final String SECOND_ROAD = "second-road";
    /** Cells wide a supply road and the second road are carved. */
    static final int ROAD_WIDTH = 5;
    /** Where the second road crosses the line: this far east of redoubt 2. */
    static final int SECOND_ROAD_ASIDE = 12;
    /** Cells of road margin a picket course leaves open either side of a road it crosses. */
    static final int PICKET_ROAD_GAP = 2;
    /** Pitch of the funnels opened in a picket course's wire. */
    static final int PICKET_FUNNEL_EVERY = 14;
    /** Rows a picket trench is dug, where the ground allows. */
    static final int PICKET_DEPTH = 2;
    /** Pitch of the nests along a picket run, between the ones at its ends. */
    static final int PICKET_NEST_EVERY = 40;
    /** Cells behind a middle redoubt's rear edge its switch line runs, and how far it reaches either side. */
    static final int SWITCH_BACK = 16, SWITCH_HALF = 24;
    /** Side of the eruption site, in cells. */
    static final int ERUPTION_SIDE = 12;

    /**
     * What the Cordon lays over its country ({@code cordon-map-intent.md}): a
     * picket course between each pair of neighbouring redoubts, facing the
     * front, a switch line behind the middle, and the open ground a bore may
     * erupt on behind the line.
     */
    static MissionLayout layout(boolean south, Density density, CordonVariant variant, int width, int height,
                                boolean installationEast) {
        int[] front = {0, south ? 1 : -1};
        Side toward = south ? Side.NORTH : Side.SOUTH;
        List<Piece> pieces = new ArrayList<>();
        // The allies' air base, held before anything grows.
        pieces.add(airBase(south, installationEast, width, height));
        List<Piece> supply = new ArrayList<>();
        for (int n = 1; n <= REDOUBTS; n++) {
            supply.add(new Road(SUPPLY_ROAD + n, Endpoint.gate(BASE_PLACE, toward),
                    Endpoint.gate(REDOUBT_PLACE + n, toward.opposite()), ROAD_WIDTH, false));
        }
        boolean city = density == Density.URBAN;
        // Supply roads first: they are carved as the places grow, so the works resolved later see them as road. A
        // city leaves no open country to carve them across: its supply roads are its streets, followed with the
        // works, first, so the works after them keep off them.
        if (city) pieces.add(new Deferred(SUPPLY_STREETS, Phase.WORKS, ground -> supply, false));
        else pieces.addAll(supply);
        if (variant == CordonVariant.ROAD) {
            // From the base's own gate, so the base keeps the gates it had, through the line beside redoubt 2.
            pieces.add(new Road(SECOND_ROAD, Endpoint.gate(BASE_PLACE, toward),
                    Endpoint.beside(REDOUBT_PLACE + 2, Side.EAST, SECOND_ROAD_ASIDE, 0),
                    Endpoint.gate(CivilDefenceCompound.PLACE, toward.opposite()), ROAD_WIDTH, false));
        }
        LineStyle style = density == Density.URBAN ? LineStyle.STREETS : LineStyle.TRENCH_WIRE;
        LineStyle fallback = density == Density.URBAN ? LineStyle.STREETS : LineStyle.TRENCH;
        boolean streets = density != Density.RURAL;
        // The eruption site before the works: it holds its ground against the works resolved after it.
        // The beats' set pieces: structures raised before the map is finished, the Road's corridor pieces with
        // the works.
        pieces.add(new Deferred(SET_PIECES, Phase.STRUCTURES, ground -> CordonSetPieces.structures(ground, front[1]),
                false));
        if (city) pieces.add(new Deferred(CITY_PIECES, Phase.WORKS, ground -> CordonCityPieces.pieces(ground, front[1]), false));
        pieces.add(new Deferred(ERUPTION, Phase.WORKS, ground -> eruptionSite(ground, south, city), false));
        if (variant == CordonVariant.ROAD) {
            pieces.add(new Deferred(CORRIDOR_PIECES, Phase.WORKS, CordonSetPieces::corridorPieces, false));
        }
        if (density.ridge) pieces.add(new Deferred(RIDGE, Phase.WORKS, ground -> ridge(ground, front), false));
        pieces.add(new Deferred(PICKETS, Phase.WORKS, ground -> pickets(ground, front, style, streets), false));
        pieces.add(new Deferred(SWITCH, Phase.WORKS, ground -> switchLine(ground, front, fallback, streets), false));
        return new MissionLayout(pieces);
    }

    /** A picket course from each redoubt's claimed edge to the next's, at their depths. */
    static List<Piece> pickets(LayoutGround ground, int[] front, LineStyle style, boolean streets) {
        List<Piece> out = new ArrayList<>();
        for (int n = 1; n < REDOUBTS; n++) {
            int[] a = ground.claimBounds(REDOUBT_PLACE + n), b = ground.claimBounds(REDOUBT_PLACE + (n + 1));
            if (a == null || b == null) continue;
            int[] from = {a[2] + 1, (a[1] + a[3]) / 2}, to = {b[0] - 1, (b[1] + b[3]) / 2};
            if (to[0] - from[0] < LineCourse.MIN_RUN) continue;
            out.add(new Line(PICKET_LINE + n + "-" + (n + 1), List.of(from, to), style, front,
                    PICKET_ROAD_GAP, PICKET_FUNNEL_EVERY, true, false, PICKET_DEPTH, PICKET_NEST_EVERY, streets));
        }
        return out;
    }

    /**
     * The switch line (beat L3): a shorter trench behind whichever middle
     * redoubt has the most open ground behind it, unmanned, no wire and no
     * nests, where a garrison falls back to.
     */
    static List<Piece> switchLine(LayoutGround ground, int[] front, LineStyle style, boolean streets) {
        List<int[]> best = null;
        int bestRoom = -1;
        for (int n = 2; n <= REDOUBTS - 1; n++) {
            int[] r = ground.claimBounds(REDOUBT_PLACE + n);
            if (r == null) continue;
            int cx = (r[0] + r[2]) / 2;
            int y = front[1] > 0 ? r[1] - SWITCH_BACK : r[3] + SWITCH_BACK;
            int x0 = Math.max(1, cx - SWITCH_HALF), x1 = Math.min(ground.width() - 2, cx + SWITCH_HALF);
            if (y < 1 || y > ground.height() - 2 || x1 - x0 < LineCourse.MIN_NESTED_RUN) continue;
            int room = 0;
            for (int x = x0; x <= x1; x++) {
                for (int row = 0; row < PICKET_DEPTH; row++) {
                    int cy = y - front[1] * row;
                    if (ground.walkable(x, cy) && !ground.isRoad(x, cy) && !ground.isClaimed(x, cy)) room++;
                }
            }
            if (room > bestRoom) {
                bestRoom = room;
                best = List.of(new int[]{x0, y}, new int[]{x1, y});
            }
        }
        if (best == null) return List.of();
        return List.of(new Line(SWITCH_LINE, best, style, front, PICKET_ROAD_GAP, 0, false, false,
                PICKET_DEPTH, 0, streets));
    }

    /**
     * Open ground between the line and the logistics base, behind the middle,
     * clear of roads and of every place's ground: the nearest such square to
     * the middle of that band. A city's ground is all some place's, so in a
     * {@code city} the site is a cleared lot: open ground inside the city's
     * blocks (a yard, a square, a park's lawn), off the streets, with no prop or
     * building on it, on no redoubt's nor the base's ground.
     */
    static List<Piece> eruptionSite(LayoutGround ground, boolean south, boolean city) {
        int w = ground.width();
        int[] base = ground.claimBounds(BASE_PLACE);
        if (base == null) return List.of();
        // The ground behind the line as deep as the base reaches (the base's own ground is claimed and so ruled
        // out), up to the nearest redoubt's rear edge: the supply roads cross the strip in front of the base.
        int y0 = south ? base[1] : 0, y1 = south ? ground.height() - 1 : base[3];
        for (int n = 1; n <= REDOUBTS; n++) {
            int[] r = ground.claimBounds(REDOUBT_PLACE + n);
            if (r == null) continue;
            if (south) y1 = Math.min(y1, r[1] - 1);
            else y0 = Math.max(y0, r[3] + 1);
        }
        int cx = (base[0] + base[2]) / 2 - ERUPTION_SIDE / 2, cy = (y0 + y1) / 2 - ERUPTION_SIDE / 2;
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int y = y0; y + ERUPTION_SIDE - 1 <= y1; y += 2) {
            for (int x = Math.round(w * 0.15f); x + ERUPTION_SIDE - 1 <= Math.round(w * 0.85f); x += 2) {
                long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                if (d >= bestDistance || !(city ? lot(ground, x, y) : open(ground, x, y))) continue;
                best = new int[]{x, y};
                bestDistance = d;
            }
        }
        if (best == null) return List.of();
        return List.of(new Site(ERUPTION_SITE, best[0], best[1], best[0] + ERUPTION_SIDE - 1,
                best[1] + ERUPTION_SIDE - 1, SitePurpose.OPEN, false));
    }

    private static boolean open(LayoutGround ground, int x0, int y0) {
        for (int y = y0; y < y0 + ERUPTION_SIDE; y++) {
            for (int x = x0; x < x0 + ERUPTION_SIDE; x++) {
                if (ground.isRoad(x, y) || ground.isClaimed(x, y) || !ground.walkable(x, y)) return false;
            }
        }
        return true;
    }

    private static boolean lot(LayoutGround ground, int x0, int y0) {
        for (int y = y0; y < y0 + ERUPTION_SIDE; y++) {
            for (int x = x0; x < x0 + ERUPTION_SIDE; x++) {
                if (!ground.buildable(x, y) || ground.isHeld(x, y)) return false;
            }
        }
        // On a works' ground the bore would open inside its walls.
        for (int n = 0; n <= REDOUBTS; n++) {
            int[] b = ground.claimBounds(n == 0 ? BASE_PLACE : REDOUBT_PLACE + n);
            if (b != null && x0 <= b[2] && x0 + ERUPTION_SIDE - 1 >= b[0] && y0 <= b[3] && y0 + ERUPTION_SIDE - 1 >= b[1]) {
                return false;
            }
        }
        return true;
    }

    /** The redoubts of a plan laid out by {@link #planFor}, west to east. */
    public static List<Precinct> redoubts(PrecinctPlan plan) {
        List<Precinct> out = new ArrayList<>();
        for (int n = 1; n <= REDOUBTS; n++) {
            for (Precinct precinct : plan.precincts()) {
                if (precinct.name().equals(REDOUBT_PLACE + n)) out.add(precinct);
            }
        }
        return out;
    }

    /**
     * A placement across {@code x0..x1} of the map's width and {@code d0..d1}
     * of its depth from the rear. Y is up, so a southern rear is low y.
     */
    static MapPlacement band(boolean rearSouth, float x0, float x1, float d0, float d1) {
        return rearSouth ? new MapPlacement(x0, d0, x1, d1) : new MapPlacement(x0, 1f - d1, x1, 1f - d0);
    }
}
