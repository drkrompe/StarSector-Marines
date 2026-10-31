package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.layout.Airfield;
import com.dillon.starsectormarines.battle.world.gen.layout.Block;
import com.dillon.starsectormarines.battle.world.gen.layout.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Endpoint;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Line;
import com.dillon.starsectormarines.battle.world.gen.layout.LineCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.LineStyle;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Phase;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.gen.layout.Ridge;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureFit;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * What the heart lays over its country (`heart-map-intent.md`): the works a
 * cordon that has faced this bloom for weeks dug and built, and the places the
 * story beats are staged in, each a named piece of the plan's mission layout
 * ({@code mission-layout.md}), so an objective asks "is this here" by name.
 *
 * <p>Every piece is optional: a map that cannot hold one reports it refused and
 * the battle stands. Nothing here is a plan-time site, so the places grow
 * exactly as they did before the layout existed and the pooled maps keep the
 * ground they were vetted on.
 */
public final class HeartLayout {

    private HeartLayout() { }

    /** The cordon ridge: the last high ground before the bloom, the forward line dug along its crest. */
    public static final String CORDON_RIDGE = "cordon-ridge";
    /** The origin facility's yard dressing (`heart-origins.md`): the clean props that say what the place was. */
    public static final String ORIGIN_DRESSING = "origin-dressing";
    /** Clear cells kept round each dressing group, and the step its anchors are tried at. */
    static final int DRESS_MARGIN = 2, DRESS_STEP = 2;
    /** An open green's reach round its pond: its things stand inside this, its edge of trees on a ring just out. */
    static final int GREEN_RADIUS = 18, EDGE_RADIUS = 24, EDGE_PITCH = 8;
    /** The forward line's runs, one between each pair of lane gaps: this plus 1, 2, ... */
    public static final String FORWARD_LINE = "forward-line-";
    /** The observation posts forward of the line: this plus 1 and 2. */
    public static final String OBSERVATION_POSTS = "observation-posts";
    public static final String OBSERVATION_POST = "op-";
    /** The logistics base's ammunition dumps on the farmland behind the line. */
    public static final String LOGISTICS = "logistics";
    public static final String AMMO_DUMP = "ammo-dump-";
    /** The supply road from the landing's farmland up to the settlement's near gate (F6), and its width. */
    public static final String SUPPLY_ROAD = "supply-road";
    static final int SUPPLY_ROAD_WIDTH = 5;
    /** The plan's landing place, the cordon's farmland. */
    static final String LANDING = "landing";
    /** The logistics base's vehicle park: open ground where the armour forms up (F6, B5). */
    public static final String VEHICLE_PARK = "vehicle-park";
    /** The open yard by the workings the lanes' last stretches come out onto (F5). */
    public static final String RESERVOIR_YARD = "reservoir-yard";
    /** The lost column's holdout at the market town's near edge, and the wreck in its yard (F1). */
    public static final String HOLDOUT = "holdout";
    public static final String HOLDOUT_WRECK = "holdout-wreck";
    /** The house still signalling at the village's near edge (B2). */
    public static final String VILLAGE_HOLDOUT = "village-holdout";
    /** The trench and wire on the battery's open flanks, where a breakout would come (B3). */
    public static final String GUN_FLANK = "gun-flank-";
    /** The company's ford over the brook (B4). */
    public static final String COMPANY_FORD = "company-ford";
    /** The Burn's allied airbase behind the start line (`heart-nouns.md`, Airbase). */
    public static final String AIRBASE = "airbase";

    /** Cells either side of a lane's middle the line leaves open, so a column drives through. */
    static final int LANE_GAP = 6;
    /** Shortest run of line worth digging between two gaps. */
    static final int MIN_RUN = 12;
    /** Rows the forward line is dug, its road margin, funnel pitch and nest pitch. */
    static final int LINE_DEPTH = 2, ROAD_GAP = 3, FUNNEL_EVERY = 14, NEST_EVERY = 40;
    /** Where the lanes run through the forward line, as shares across: Broad Front's four and the company's road. */
    static final float[] BROAD_GAPS = {0.15f, 0.33f, 0.50f, 0.67f, 0.85f};
    /** The Burn's battlegroups' sectors and the company's, as shares across. */
    static final float[] BURN_GAPS = {0.18f, 0.36f, 0.50f, 0.64f, 0.82f};
    /** Rows short of a city's first built-up row its forward line is dug, and where the search for that row starts. */
    static final int CITY_STANDOFF = 4;
    static final float CITY_SCAN_FROM = 0.10f;
    /** Cells forward of the line its listening posts stand, and where across. */
    static final int OP_FORWARD = 6, OP_SEARCH = 10;
    /** Cells past the ridge's far edge that its faces and apron take. */
    static final int RIDGE_FOOT = 5;
    static final float[] BROAD_OPS = {0.24f, 0.76f}, BURN_OPS = {0.27f, 0.73f};
    /** Where the logistics base stands on the farmland, as depth and across. */
    static final float BROAD_BASE_DEPTH = 0.14f, BROAD_BASE_ACROSS = 0.42f;
    static final float BURN_BASE_DEPTH = 0.10f, BURN_BASE_ACROSS = 0.40f;
    /** Cells round the base's middle searched for room, and the vehicle park's size. */
    static final int BASE_SEARCH = 30, PARK_ALONG = 16, PARK_DEEP = 10;
    /** The reservoir yard's side, how far toward the start of the workings' seed it is wanted, and the search. */
    static final int YARD_SIDE = 16, YARD_TOWARD_START = 14, YARD_SEARCH = 36;
    /** The holdout's side (a sandbag ring with a way in), and how far outside the place's claim it is searched. */
    static final int HOLDOUT_SIDE = 9, HOLDOUT_SEARCH = 20, CITY_HOLDOUT_SEARCH = 40;
    /** The Burn's gun-flank trenches: how deep into the farmland they run and how far in from the map's sides. */
    static final float GUN_FLANK_DEPTH = 0.10f;
    static final int GUN_FLANK_INSET = 4;
    /**
     * The Burn's airbase: a station lying along the map's far side, the village's side, its strip along that edge
     * (owner, 2026-10-31: turned so the columns have the rear's width to advance across), its reservation this many
     * cells in from the start edge and back from the side's gun-flank trench.
     */
    static final int BURN_AIRBASE_REAR = 4, BURN_AIRBASE_TRENCH_GAP = 2;
    /** Cells either side of the ford's middle the ford's site holds. */
    static final int FORD_HALF = 4;

    /**
     * The heart's layout for this form of country. A city's height is its buildings (`elevation-nouns.md`), so a
     * city's cordon has no ridge: its forward line is dug on the last open ground before the streets.
     */
    static MissionLayout layout(Form form, Density density, long seed) {
        List<Piece> pieces = new ArrayList<>();
        if (density == Density.CITY) {
            pieces.add(new Deferred(FORWARD_LINE, Phase.WORKS, ground -> forwardLine(form, cityLineDepth(ground, form)), false));
        } else {
            pieces.add(new Ridge(CORDON_RIDGE, HeartCountry.ridge(form), false));
            pieces.addAll(forwardLine(form));
        }
        pieces.add(new Deferred(OBSERVATION_POSTS, Phase.STRUCTURES, ground -> observationPosts(ground, form), false));
        Side front = form.advancesAlongY() ? Side.NORTH : Side.EAST;
        pieces.add(new Road(SUPPLY_ROAD, Endpoint.beside(LANDING, front, 0, 0),
                Endpoint.gate(HeartCountry.SETTLEMENT, front.opposite()), SUPPLY_ROAD_WIDTH, false));
        pieces.add(new Deferred(LOGISTICS, Phase.STRUCTURES, ground -> ammoDumps(ground, form), false));
        pieces.add(new Deferred(VEHICLE_PARK, Phase.WORKS, ground -> vehiclePark(ground, form), false));
        pieces.add(new Deferred(RESERVOIR_YARD, Phase.WORKS, ground -> reservoirYard(ground, form), false));
        HeartOrigin origin = HeartOrigin.of(seed, form, density);
        pieces.add(new Deferred(ORIGIN_DRESSING, Phase.WORKS, ground -> originDressing(ground, origin), false));
        if (form == Form.BROAD_FRONT) {
            pieces.add(new Deferred(HOLDOUT, Phase.WORKS,
                    ground -> holdout(ground, form, HOLDOUT, true, density), false));
        } else {
            pieces.add(new Deferred(VILLAGE_HOLDOUT, Phase.WORKS,
                    ground -> holdout(ground, form, VILLAGE_HOLDOUT, false, density), false));
            pieces.addAll(gunFlanks(form));
            pieces.add(airbase(form));
            pieces.add(new Deferred(COMPANY_FORD, Phase.WORKS,
                    ground -> companyFord(form, ground.plan().landscape().brooks().get(0)), false));
        }
        return new MissionLayout(pieces);
    }

    /**
     * The origin facility's yard dressed as what it was: a few of the origin's groups (a stack of containers, a truck
     * stopped with its crates, a row of tank racks), each set down whole where its box and a margin are open yard
     * inside the facility's claim, the first nearest the core building and each next as far from those taken as the
     * yard allows. Props are clean (the creep is the live layer's); none where the facility claimed nothing.
     */
    static List<Piece> originDressing(LayoutGround ground, HeartOrigin origin) {
        int[] claim = ground.claimBounds(HeartCountry.WORKINGS);
        Precinct workings = HeartCountry.place(ground.plan(), HeartCountry.WORKINGS);
        if (claim == null || workings == null) return List.of();
        int[] core = origin.opensAt != null ? null
                : HeartOrigin.core(ground.topology(), origin, workings.seedX(), workings.seedY());
        int[] near = core != null ? core : new int[]{workings.seedX(), workings.seedY()};
        List<int[]> taken = new ArrayList<>();
        List<Props.Placement> out = new ArrayList<>();
        for (DressingGroup group : dressing(origin)) {
            int[] best = null;
            long bestScore = Long.MIN_VALUE;
            for (int y = claim[1]; y + group.height <= claim[3]; y += DRESS_STEP) {
                for (int x = claim[0]; x + group.width <= claim[2]; x += DRESS_STEP) {
                    if (!fits(ground, x, y, group, taken)) continue;
                    int[] mid = {x + group.width / 2, y + group.height / 2};
                    if (!origin.fenced && dist2(mid, near) > (long) GREEN_RADIUS * GREEN_RADIUS) continue;
                    long score;
                    if (taken.isEmpty()) {
                        score = -dist2(mid, near);
                    } else {
                        score = Long.MAX_VALUE;
                        for (int[] t : taken) score = Math.min(score, dist2(mid, new int[]{t[0] + t[2] / 2, t[1] + t[3] / 2}));
                    }
                    if (score > bestScore) { bestScore = score; best = new int[]{x, y}; }
                }
            }
            if (best == null) continue;
            taken.add(new int[]{best[0], best[1], group.width, group.height});
            for (DressingGroup.Part part : group.parts) {
                out.add(new Props.Placement(part.doodad(), best[0] + part.dx(), best[1] + part.dy(), 0));
            }
        }
        if (!origin.fenced) out.addAll(edge(near));
        return out.isEmpty() ? List.of() : List.of(new Props(ORIGIN_DRESSING, out, false));
    }

    /**
     * An open green's edge, where a facility has its fence: a ring of trees round it at an even pitch, each planted
     * where its ground lets it (the stage refuses one over a road or a house), so the paths in stay open.
     */
    static List<Props.Placement> edge(int[] centre) {
        List<Props.Placement> out = new ArrayList<>();
        TreeCanopy[] kinds = {TreeCanopy.OAK, TreeCanopy.BIRCH, TreeCanopy.OAK_SMALL};
        int count = (int) Math.round(2 * Math.PI * EDGE_RADIUS / EDGE_PITCH);
        for (int i = 0; i < count; i++) {
            double a = 2 * Math.PI * i / count;
            TreeCanopy kind = kinds[i % kinds.length];
            int x = (int) Math.round(centre[0] + Math.cos(a) * EDGE_RADIUS) - kind.width / 2;
            int y = (int) Math.round(centre[1] + Math.sin(a) * EDGE_RADIUS) - kind.height / 2;
            out.add(new Props.Placement(kind.id, x, y, 0));
        }
        return out;
    }

    /** Whether a group's box, with its margin, is open yard nobody holds and clear of the groups already taken. */
    private static boolean fits(LayoutGround ground, int x0, int y0, DressingGroup group, List<int[]> taken) {
        for (int[] t : taken) {
            if (x0 - DRESS_MARGIN < t[0] + t[2] && t[0] < x0 + group.width + DRESS_MARGIN
                    && y0 - DRESS_MARGIN < t[1] + t[3] && t[1] < y0 + group.height + DRESS_MARGIN) return false;
        }
        for (int y = y0 - DRESS_MARGIN; y < y0 + group.height + DRESS_MARGIN; y++) {
            for (int x = x0 - DRESS_MARGIN; x < x0 + group.width + DRESS_MARGIN; x++) {
                if (!ground.buildable(x, y) || ground.isHeld(x, y)) return false;
            }
        }
        return true;
    }

    /** Props set down together as one thing in a yard, each part at its offset from the group's low corner. */
    record DressingGroup(int width, int height, List<Part> parts) {
        /** One doodad of a group, its footprint and its offset. */
        record Part(String doodad, int width, int height, int dx, int dy) {}

        static DressingGroup of(Part... parts) {
            int w = 0, h = 0;
            for (Part p : parts) {
                w = Math.max(w, p.dx() + p.width());
                h = Math.max(h, p.dy() + p.height());
            }
            return new DressingGroup(w, h, List.of(parts));
        }
    }

    /** A clean origin prop as a group's part, its footprint from its tileset entry (one cell with no tiles installed). */
    private static DressingGroup.Part part(OriginProp prop, int dx, int dy) {
        DoodadDef def = TileRegistry.installed() == null ? null : prop.def(OriginProp.State.CLEAN);
        return def == null ? new DressingGroup.Part(prop.id(OriginProp.State.CLEAN), 1, 1, dx, dy)
                : new DressingGroup.Part(prop.id(OriginProp.State.CLEAN), def.footprintCellsX, def.footprintCellsY, dx, dy);
    }

    /** A tree as a group's part: its canopy is its footprint. */
    private static DressingGroup.Part tree(TreeCanopy canopy, int dx, int dy) {
        return new DressingGroup.Part(canopy.id, canopy.width, canopy.height, dx, dy);
    }

    /**
     * What an origin's yard holds, the first group set down nearest the core building; the park's first is its pond,
     * set down nearest the place's seed, where the first mother opens.
     */
    static List<DressingGroup> dressing(HeartOrigin origin) {
        return switch (origin) {
            case FREIGHT -> {
                DressingGroup unloading = DressingGroup.of(part(OriginProp.UNLOADING_TRUCK, 0, 0),
                        part(OriginProp.SEAFOOD_CRATES, 3, 0),
                        part(OriginProp.SEAFOOD_CRATES, 3, 2));
                DressingGroup stack = DressingGroup.of(part(OriginProp.CONTAINER_RUST, 0, 0),
                        part(OriginProp.CONTAINER_BLUE, 0, 2),
                        part(OriginProp.CONTAINER_RUST, 0, 4));
                DressingGroup pair = DressingGroup.of(part(OriginProp.CONTAINER_BLUE, 0, 0),
                        part(OriginProp.CONTAINER_BLUE, 4, 0));
                yield List.of(unloading, stack, stack, pair);
            }
            case RESEARCH -> {
                DressingGroup racks = DressingGroup.of(part(OriginProp.TANK_RACK, 0, 0),
                        part(OriginProp.TANK_RACK, 0, 2),
                        part(OriginProp.TANK_RACK, 0, 4),
                        part(OriginProp.SAMPLE_FREEZER, 4, 0),
                        part(OriginProp.SAMPLE_FREEZER, 4, 2));
                DressingGroup cold = DressingGroup.of(part(OriginProp.TANK_RACK, 0, 0),
                        part(OriginProp.SAMPLE_FREEZER, 0, 2));
                yield List.of(racks, racks, cold);
            }
            case WORKINGS -> {
                DressingGroup pumping = DressingGroup.of(part(OriginProp.PUMP_SHED, 0, 0),
                        part(OriginProp.PIPE_RUN, 3, 0),
                        part(OriginProp.PIPE_RUN, 6, 0));
                DressingGroup outfall = DressingGroup.of(part(OriginProp.PIPE_RUN, 0, 0),
                        part(OriginProp.PIPE_RUN, 3, 0),
                        part(OriginProp.PIPE_MOUTH, 6, 0));
                yield List.of(pumping, outfall, pumping);
            }
            case PARK -> {
                DressingGroup pondside = DressingGroup.of(part(OriginProp.POND, 0, 0),
                        part(OriginProp.BENCH, 0, 4),
                        part(OriginProp.BENCH, 3, 4));
                DressingGroup bandstand = DressingGroup.of(part(OriginProp.BANDSTAND, 0, 0));
                DressingGroup playground = DressingGroup.of(part(OriginProp.SWINGS, 0, 0),
                        part(OriginProp.SLIDE, 4, 0),
                        part(OriginProp.CLIMBER, 0, 3),
                        part(OriginProp.ROUNDABOUT, 4, 3));
                DressingGroup benches = DressingGroup.of(part(OriginProp.BENCH, 0, 0),
                        part(OriginProp.BENCH, 3, 0));
                DressingGroup grove = DressingGroup.of(tree(TreeCanopy.OAK, 0, 0), tree(TreeCanopy.OAK_SMALL, 6, 1),
                        tree(TreeCanopy.BIRCH, 2, 6));
                // A grove on the lawn; the edge's ring of trees is planted after the groups.
                yield List.of(pondside, bandstand, playground, grove, benches);
            }
        };
    }

    private static long dist2(int[] a, int[] b) {
        return (long) (a[0] - b[0]) * (a[0] - b[0]) + (long) (a[1] - b[1]) * (a[1] - b[1]);
    }

    /** The cell at a depth and an across, both in cells. */
    static int[] cell(Form form, int depth, int across) {
        return form.advancesAlongY() ? new int[]{across, depth} : new int[]{depth, across};
    }

    /** Cells across the front. */
    private static int acrossCells(Form form) {
        return form.advancesAlongY() ? form.scale.width : form.scale.height;
    }

    /** One cell toward the heart. */
    private static int[] frontward(Form form) {
        return form.advancesAlongY() ? new int[]{0, 1} : new int[]{1, 0};
    }

    /** Depth, in cells, of the ridge's near edge. */
    private static int ridgeDepth(Form form) {
        int depthCells = form.advancesAlongY() ? form.scale.height : form.scale.width;
        float share = form.advancesAlongY() ? HeartCountry.BROAD_FRONT_RIDGE_DEPTH : HeartCountry.THE_BURN_RIDGE_DEPTH;
        return Math.round(share * (depthCells - 1));
    }

    /** Depth, in cells, of the forward line: the ridge's crest, a little forward of its middle. */
    static int lineDepth(Form form) {
        return ridgeDepth(form) + HeartCountry.RIDGE_THICKNESS / 2 + 1;
    }

    /** Depth of the ground just past the ridge's foot on the bloom side. */
    private static int pastRidge(Form form) {
        return ridgeDepth(form) + HeartCountry.RIDGE_THICKNESS - 1 + RIDGE_FOOT;
    }

    /**
     * The forward line: trench and wire across the whole front along the
     * ridge's crest, facing the bloom, broken at every lane so the columns
     * drive through.
     */
    static List<Piece> forwardLine(Form form) {
        return forwardLine(form, lineDepth(form));
    }

    /** The forward line dug at this depth. */
    static List<Piece> forwardLine(Form form, int depth) {
        int across = acrossCells(form);
        float[] gaps = form == Form.BROAD_FRONT ? BROAD_GAPS : BURN_GAPS;
        List<Piece> out = new ArrayList<>();
        int from = 2, n = 1;
        for (int i = 0; i <= gaps.length; i++) {
            int to = i < gaps.length ? Math.round(gaps[i] * (across - 1)) - LANE_GAP : across - 3;
            if (to - from >= MIN_RUN) {
                out.add(new Line(FORWARD_LINE + n++, List.of(cell(form, depth, from), cell(form, depth, to)),
                        LineStyle.TRENCH_WIRE, frontward(form), ROAD_GAP, FUNNEL_EVERY, true, false, LINE_DEPTH,
                        NEST_EVERY, false));
            }
            if (i < gaps.length) from = Math.round(gaps[i] * (across - 1)) + LANE_GAP;
        }
        return out;
    }

    /** A city's forward line: a few rows short of the first built-up row, never deeper than the ridge's crest. */
    static int cityLineDepth(LayoutGround ground, Form form) {
        int depthCells = form.advancesAlongY() ? form.scale.height : form.scale.width;
        int deepest = lineDepth(form);
        int edge = builtUpDepth((d, a) -> {
            int[] c = cell(form, d, a);
            return ground.buildable(c[0], c[1]);
        }, acrossCells(form), Math.round(CITY_SCAN_FROM * (depthCells - 1)), deepest + CITY_STANDOFF);
        return Math.min(deepest, edge - CITY_STANDOFF);
    }

    /**
     * The first depth in {@code [from, to)} at which at least half the row across is not open ground, or {@code to}
     * when every row there is mostly open.
     */
    static int builtUpDepth(BiPredicate<Integer, Integer> open, int across, int from, int to) {
        for (int d = from; d < to; d++) {
            int shut = 0;
            for (int a = 0; a < across; a++) if (!open.test(d, a)) shut++;
            if (shut * 2 >= across) return d;
        }
        return to;
    }

    /** A listening post forward of the line in each half of the front, on the open ground nearest its place. */
    static List<Piece> observationPosts(LayoutGround ground, Form form) {
        float[] at = form == Form.BROAD_FRONT ? BROAD_OPS : BURN_OPS;
        int across = acrossCells(form), depth = pastRidge(form) + OP_FORWARD, side = StructureKind.OBSERVATION_POST.along;
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < at.length; i++) {
            int[] want = cell(form, depth, Math.round(at[i] * (across - 1)));
            int[] c = StructureFit.nearest(country(ground), around(ground, want, OP_SEARCH), side, side,
                    want[0], want[1], 1);
            if (c != null) {
                out.add(new Structure(OBSERVATION_POST + (i + 1), StructureKind.OBSERVATION_POST, c[0], c[1], 0, false));
            }
        }
        return out;
    }

    /** Open ground nobody built on: the cordon's own farmland counts, the landing's ground claimed or not. */
    private static LineCourse.CellTest country(LayoutGround ground) {
        return ground::buildable;
    }

    /** Open country a base's works may stand on: on The Burn, never the airbase's held ground, which the works lay round. */
    private static LineCourse.CellTest country(LayoutGround ground, Form form) {
        if (form != Form.THE_BURN) return country(ground);
        Site held = airbase(form).reservation();
        return (x, y) -> ground.buildable(x, y) && (x < held.x0() || x > held.x1() || y < held.y0() || y > held.y1());
    }

    /** The logistics base's middle: on the farmland behind the line, beside the company's road. */
    private static int[] baseMiddle(Form form) {
        return form == Form.BROAD_FRONT ? form.cellAt(BROAD_BASE_DEPTH, BROAD_BASE_ACROSS)
                : form.cellAt(BURN_BASE_DEPTH, BURN_BASE_ACROSS);
    }

    private static int[] around(LayoutGround ground, int[] middle, int half) {
        return new int[]{Math.max(1, middle[0] - half), Math.max(1, middle[1] - half),
                Math.min(ground.width() - 2, middle[0] + half), Math.min(ground.height() - 2, middle[1] + half)};
    }

    /** Two ammunition dumps either side of the logistics base's middle. */
    static List<Piece> ammoDumps(LayoutGround ground, Form form) {
        int[] middle = baseMiddle(form);
        int side = StructureKind.AMMO_DUMP.along, step = PARK_ALONG / 2 + side;
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int offset = i == 0 ? -step : step;
            int[] want = form.advancesAlongY() ? new int[]{middle[0] + offset, middle[1]}
                    : new int[]{middle[0], middle[1] + offset};
            int[] at = StructureFit.nearest(country(ground, form), around(ground, want, BASE_SEARCH / 2), side, side,
                    want[0], want[1], 2);
            // The Burn's airbase holds ground beside the base, so a dump the near search cannot seat looks further.
            if (at == null) at = StructureFit.nearest(country(ground, form), around(ground, want, BASE_SEARCH), side, side,
                    want[0], want[1], 2);
            if (at != null) out.add(new Structure(AMMO_DUMP + (i + 1), StructureKind.AMMO_DUMP, at[0], at[1], 0, false));
        }
        return out;
    }

    /** The vehicle park between the dumps: open ground the armour waves form up on. */
    static List<Piece> vehiclePark(LayoutGround ground, Form form) {
        int[] middle = baseMiddle(form);
        int w = form.advancesAlongY() ? PARK_ALONG : PARK_DEEP, h = form.advancesAlongY() ? PARK_DEEP : PARK_ALONG;
        int[] at = StructureFit.nearest(country(ground, form), around(ground, middle, BASE_SEARCH), w, h,
                middle[0] - w / 2, middle[1] - h / 2, 2);
        if (at == null) return List.of();
        return List.of(new Site(VEHICLE_PARK, at[0], at[1], at[0] + w - 1, at[1] + h - 1, SitePurpose.OPEN, false));
    }

    /** The yard by the workings, on its start side where the lanes come in. */
    static List<Piece> reservoirYard(LayoutGround ground, Form form) {
        Precinct workings = HeartCountry.place(ground.plan(), HeartCountry.WORKINGS);
        if (workings == null) return List.of();
        int[] back = frontward(form);
        int[] want = {workings.seedX() - back[0] * YARD_TOWARD_START, workings.seedY() - back[1] * YARD_TOWARD_START};
        // Off the hollow's rim, which is crossed only at its passes.
        LineCourse.CellTest open = (x, y) -> ground.buildable(x, y) && !ground.isHeld(x, y);
        int[] at = StructureFit.nearest(open, around(ground, want, YARD_SEARCH), YARD_SIDE, YARD_SIDE,
                want[0] - YARD_SIDE / 2, want[1] - YARD_SIDE / 2, 2);
        if (at == null) return List.of();
        return List.of(new Site(RESERVOIR_YARD, at[0], at[1], at[0] + YARD_SIDE - 1, at[1] + YARD_SIDE - 1,
                SitePurpose.OPEN, false));
    }

    /**
     * A holdout at the settlement's near edge: a sandbag ring with its way in
     * on the start side, facing the bloom, and, for the lost column, its
     * wrecked carrier in the yard. A city's settlement starts where its line is dug, so there the holdout stands
     * wholly past the line's trench and wire, looked for further into the streets.
     */
    static List<Piece> holdout(LayoutGround ground, Form form, String name, boolean wreck, Density density) {
        int[] claim = ground.claimBounds(HeartCountry.SETTLEMENT);
        Precinct settlement = HeartCountry.place(ground.plan(), HeartCountry.SETTLEMENT);
        if (claim == null || settlement == null) return List.of();
        // The settlement's near edge, toward the start, at its seed's across.
        int[] want = form.advancesAlongY() ? new int[]{settlement.seedX(), claim[1]}
                : new int[]{claim[0], settlement.seedY()};
        int s = HOLDOUT_SIDE;
        boolean city = density == Density.CITY;
        int pastLine = city ? cityLineDepth(ground, form) + LINE_DEPTH + 2 : Integer.MIN_VALUE;
        // Off held ground: on Broad Front the town stands on its knoll, and the ring round the top is the cliff.
        int[] at = StructureFit.nearest((x, y) -> ground.buildable(x, y) && !ground.isHeld(x, y)
                        && (form.advancesAlongY() ? y : x) >= pastLine,
                around(ground, want, city ? CITY_HOLDOUT_SEARCH : HOLDOUT_SEARCH), s, s,
                want[0] - s / 2, want[1] - s / 2, 1);
        if (at == null) return List.of();
        int[] front = frontward(form);
        List<int[]> ring = new ArrayList<>();
        for (int x = at[0]; x < at[0] + s; x++) {
            for (int y = at[1]; y < at[1] + s; y++) {
                boolean edge = x == at[0] || y == at[1] || x == at[0] + s - 1 || y == at[1] + s - 1;
                if (!edge) continue;
                // The way in: the middle of the start-side wall.
                int along = form.advancesAlongY() ? x - at[0] : y - at[1];
                boolean startSide = form.advancesAlongY() ? y == at[1] : x == at[0];
                if (startSide && Math.abs(along - s / 2) <= 1) continue;
                ring.add(new int[]{x, y});
            }
        }
        List<Piece> out = new ArrayList<>();
        out.add(new Block(name, ring, BlockKind.SANDBAG, front, false));
        if (wreck) {
            int cx = at[0] + s / 2, cy = at[1] + s / 2;
            out.add(new Block(HOLDOUT_WRECK, List.of(new int[]{cx, cy}, new int[]{cx + 1, cy}), BlockKind.WRECK,
                    null, false));
        }
        return out;
    }

    /** Trench and wire along each side of The Burn's battery, facing out, where a breakout would come at the guns. */
    /**
     * The military's airbase on station behind The Burn's start line: a station lot lying along the map's far side,
     * its strip on that edge, held from before the places grow, its berths and sheds the allied wing's own.
     */
    public static Airfield airbase(Form form) {
        AirbaseLot.Size size = AirbaseLot.Size.STATION;
        // The strip runs along the high side across the advance: north of an advance along x, east of one along y.
        AirbaseLot.Facing facing = form.advancesAlongY() ? AirbaseLot.Facing.EAST : AirbaseLot.Facing.NORTH;
        int side = acrossCells(form) - 1 - GUN_FLANK_INSET - BURN_AIRBASE_TRENCH_GAP;
        if (form.advancesAlongY()) {
            int x0 = side - AirbaseLot.reservedSpanX(size, facing) + 1;
            return new Airfield(AIRBASE, x0, BURN_AIRBASE_REAR, facing, size, LandingPad.Purpose.ALLIED_AIRFIELD, false);
        }
        int y0 = side - AirbaseLot.reservedSpanY(size, facing) + 1;
        return new Airfield(AIRBASE, BURN_AIRBASE_REAR, y0, facing, size, LandingPad.Purpose.ALLIED_AIRFIELD, false);
    }

    static List<Piece> gunFlanks(Form form) {
        int across = acrossCells(form);
        int far = Math.round(GUN_FLANK_DEPTH * ((form.advancesAlongY() ? form.scale.height : form.scale.width) - 1));
        List<Piece> out = new ArrayList<>();
        int[][] sides = {{GUN_FLANK_INSET, -1}, {across - 1 - GUN_FLANK_INSET, 1}};
        for (int i = 0; i < sides.length; i++) {
            int a = sides[i][0];
            int[] outward = form.advancesAlongY() ? new int[]{sides[i][1], 0} : new int[]{0, sides[i][1]};
            out.add(new Line(GUN_FLANK + (i + 1), List.of(cell(form, 2, a), cell(form, far, a)), LineStyle.TRENCH_WIRE,
                    outward, ROAD_GAP, FUNNEL_EVERY, false, false, LINE_DEPTH, 0, false));
        }
        return out;
    }

    /** The ford nearest the company's sector, held open with the banks either side of it. */
    static List<Piece> companyFord(Form form, Landscape.Brook brook) {
        List<int[]> line = brook.line();
        int middle = acrossCells(form) / 2;
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < line.size(); i++) {
            if (!brook.isFord(i) || brook.isFord(i - 1)) continue;
            int[] c = line.get(Math.min(line.size() - 1, i + brook.fordWidth() / 2));
            int d = Math.abs((form.advancesAlongY() ? c[0] : c[1]) - middle);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        if (best == null) return List.of();
        return List.of(new Site(COMPANY_FORD, best[0] - FORD_HALF, best[1] - FORD_HALF, best[0] + FORD_HALF,
                best[1] + FORD_HALF, SitePurpose.OPEN, false));
    }
}
