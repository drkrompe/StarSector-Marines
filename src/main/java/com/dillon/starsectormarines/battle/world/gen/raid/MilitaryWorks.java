package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Endpoint;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Phase;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.RoadCourse;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A military base's site (owner, 2026-11-10: rebuilt to the mine's standard as a real process): the patrol base that
 * keeps the planet's patrols on the road. Convoys come in by the rear gate to the supply depot the patrols draw
 * from; the magazine stands apart at the back behind its sandbags; the fuel point and the motor sheds (the raid's
 * machines, one per machine) work in the yard round the vehicle park; dispatch, where the patrols are briefed and
 * sent out, stands by the gate the attacker faces, its radar mast beside it. Patrol roads run out of two of its gates
 * to the observation posts that watch the planet's approaches, each with a radio hut. The barracks are the garrison
 * beside it, the force the base exists to keep moving.
 */
public final class MilitaryWorks implements RaidIndustry {

    /** A motor shed: where the patrol vehicles stand and are worked on; the floor one charge wrecks. */
    private static final RoomShape MOTOR_SHED = RoomShape.rectangle(14, 9);
    /** The supply depot: a long bonded store of rations, parts and kit the patrols draw from. */
    private static final RoomShape DEPOT_SHED = RoomShape.rectangle(16, 8);
    /** The magazine: squat because it is mostly wall. */
    private static final RoomShape MAGAZINE_BUNKER = RoomShape.rectangle(9, 7);
    /** The fuel point: the pump house the vehicles fill at. */
    private static final RoomShape FUEL_HOUSE = RoomShape.rectangle(8, 6);
    /** Dispatch: the operations room the patrols are briefed and sent out from. */
    private static final RoomShape DISPATCH_ROOM = RoomShape.rectangle(8, 6);
    /** The medical post: a treatment room and a few beds, where the patrols' wounded come in by the gate. */
    private static final RoomShape AID_POST = RoomShape.rectangle(10, 7);

    /** Vehicle park a base keeps per motor shed, round the sheds and the fuel point. UNTUNED. */
    static final int POOL_PER_SHED = 220;
    /** Motor sheds from which the base keeps a second supply depot. UNTUNED. */
    static final int SECOND_DEPOT_FROM = 4;

    /** The room each step of the base is. */
    public static final RoomPurpose SUPPLY_DEPOT = RoomPurpose.STOCKROOM;
    public static final RoomPurpose MAGAZINE = RoomPurpose.ARMORY;
    public static final RoomPurpose FUEL_POINT = RoomPurpose.ENGINE_ROOM;
    public static final RoomPurpose MOTOR_POOL = RoomPurpose.PARTS_CAGE;
    public static final RoomPurpose DISPATCH = RoomPurpose.CONTROL_ROOM;
    public static final RoomPurpose MEDICAL_POST = RoomPurpose.PATIENT_WARD;

    /** Width of a patrol road, cells: a track for patrol vehicles, narrower than the feeders. UNTUNED. */
    static final int PATROL_ROAD_WIDTH = 2;
    /** How far out past the works' side a patrol road runs, and how far to one side it bends, cells. UNTUNED. */
    static final int PATROL_OUT = 18, PATROL_SPREAD = 22;
    /** Cells beyond its road's end an observation post stands, so it never stands on the road. UNTUNED. */
    static final int POST_BEYOND = 4;
    /** The name every patrol road's and post's name starts with. */
    public static final String PATROL = "patrol-";

    @Override public String key() { return "military"; }

    @Override public Set<String> industryIds() { return Set.of("militarybase", "patrolhq", "highcommand"); }

    /**
     * The motor pool stands against the garrison's flank, between it and the town, so the barracks, the parade
     * ground, the sheds and the magazine read as one base inside its perimeter rather than a fort beside a works.
     * UNTUNED.
     */
    static final MapPlacement WORKS_AT = new MapPlacement(0.66f, 0.38f, 0.86f, 0.58f);

    @Override public MapPlacement worksAt() { return WORKS_AT; }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    @Override public PrecinctCharacter industryCharacter() { return PrecinctCharacter.DEPOT; }

    /**
     * The patrol base: a motor shed per machine, the supply depot (two from {@link #SECOND_DEPOT_FROM} sheds), the
     * magazine and the fuel point, dispatch and the medical post by the gate (the wounded come in the way the patrols
     * go out), and the posts its gates are watched from.
     */
    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a motor pool has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(SUPPLY_DEPOT, DEPOT_SHED, Ward.REAR, sheds >= SECOND_DEPOT_FROM ? 2 : 1),
                new FortressBuilding(MAGAZINE, MAGAZINE_BUNKER, Ward.REAR, 1),
                new FortressBuilding(FUEL_POINT, FUEL_HOUSE, Ward.YARD, 1),
                new FortressBuilding(MOTOR_POOL, MOTOR_SHED, Ward.YARD, sheds),
                new FortressBuilding(DISPATCH, DISPATCH_ROOM, Ward.FRONTAGE, 1),
                new FortressBuilding(MEDICAL_POST, AID_POST, Ward.FRONTAGE, 1),
                RaidWorksParts.guardPosts(2)),
                0, sheds * POOL_PER_SHED, true);
    }

    /** Patrol posts a base sends out. UNTUNED. */
    static final int PATROL_POSTS = 2;
    /** Cells a post keeps from the map's edge. */
    static final int POST_MARGIN = 3;
    /** Farthest a post moves from its road's end to find open ground, cells. UNTUNED. */
    static final int POST_SEARCH = 20;

    /**
     * The patrol roads and their posts: out of a works gate and bent to one side, each to an observation post with a
     * radio hut beside it. The ways out are tried in turn, the gate toward the landing first (the approach the company
     * comes by), then the inner, outer and deep gates, and the first {@link #PATROL_POSTS} whose posts would stand on
     * the map are kept, one to a gate. A road is laid through the districts on its course, as their street; its post
     * is stood once the places have grown ({@link #patrolPost}).
     */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct works = frame.works();
        if (works == null) return;
        Side deep = frame.mirrorX() ? Side.WEST : Side.EAST;
        Side outer = frame.mirrorY() ? Side.SOUTH : Side.NORTH;
        Side landing = deep.opposite(), inner = outer.opposite();
        int reach = RaidSiteTemplate.worksReach(worksFit(), frame.security(), frame.width(), frame.height());
        Side[][] ways = {{landing, outer}, {landing, inner}, {inner, deep}, {inner, landing},
                {outer, landing}, {outer, deep}, {deep, inner}, {deep, outer}};
        int kept = 0;
        EnumSet<Side> gates = EnumSet.noneOf(Side.class);
        for (Side[] way : ways) {
            if (kept >= PATROL_POSTS) break;
            Side gate = way[0], bend = way[1];
            if (gates.contains(gate)) continue;
            int dx = gate.dx * PATROL_OUT + bend.dx * PATROL_SPREAD, dy = gate.dy * PATROL_OUT + bend.dy * PATROL_SPREAD;
            // Where the post would stand, reckoned from the works' seed and reach before the places have grown.
            int[] post = postAt(new int[]{works.seedX() - reach, works.seedY() - reach, works.seedX() + reach,
                    works.seedY() + reach}, gate, dx, dy);
            if (!onMap(post, frame.width(), frame.height())) continue;
            String name = PATROL + gate.name().toLowerCase(Locale.ROOT) + "-" + bend.name().toLowerCase(Locale.ROOT);
            // From the post in to the gate, as the feeders run: the road joins the network where it meets the works.
            out.add(new Road(name, Endpoint.beside(RaidSiteTemplate.WORKS, gate, dx, dy),
                    Endpoint.gate(RaidSiteTemplate.WORKS, gate), PATROL_ROAD_WIDTH, false).laidThrough());
            out.add(new Deferred(name + "-post", Phase.STRUCTURES,
                    ground -> patrolPost(ground, name, gate, bend, dx, dy), false));
            gates.add(gate);
            kept++;
        }
    }

    /**
     * A patrol road's observation post and radio hut, stood on the grown ground: {@link #POST_BEYOND} cells past
     * where the road ends beside the works, the hut to the side the road bent; or on the nearest open country within
     * {@link #POST_SEARCH} cells of it. Where none is open both are stated where they would stand, so the layout
     * reports why they were refused.
     */
    static List<Piece> patrolPost(LayoutGround ground, String name, Side gate, Side bend, int dx, int dy) {
        int[] bounds = ground.claimBounds(RaidSiteTemplate.WORKS);
        if (bounds == null) return List.of();
        int[] at = postAt(bounds, gate, dx, dy);
        List<Piece> first = null;
        for (int r = 0; r <= POST_SEARCH; r++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int ox = -r; ox <= r; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != r) continue;
                    List<Piece> pair = postPair(name, bend, at[0] + ox, at[1] + oy);
                    if (first == null) first = pair;
                    if (stands(ground, (Structure) pair.get(0)) && stands(ground, (Structure) pair.get(1))) return pair;
                }
            }
        }
        return first;
    }

    /** The centre of a post off {@code gate}'s side of {@code bounds}, past the road's end moved {@code (dx, dy)}. */
    private static int[] postAt(int[] bounds, Side gate, int dx, int dy) {
        int[] end = RoadCourse.point(bounds, gate, List.of(), dx, dy, (x, y) -> false);
        int on = POST_BEYOND + StructureKind.OBSERVATION_POST.along / 2;
        return new int[]{end[0] + gate.dx * on, end[1] + gate.dy * on};
    }

    /** An observation post centred on {@code (x, y)} and its radio hut beside it toward {@code bend}. */
    private static List<Piece> postPair(String name, Side bend, int x, int y) {
        int half = StructureKind.OBSERVATION_POST.along / 2;
        int hutX = x + bend.dx * (half + 1 + StructureKind.RADIO_HUT.along / 2);
        int hutY = y + bend.dy * (half + 1 + StructureKind.RADIO_HUT.deep / 2);
        return List.of(post(name + "-post", StructureKind.OBSERVATION_POST, x, y),
                post(name + "-radio", StructureKind.RADIO_HUT, hutX, hutY));
    }

    private static boolean onMap(int[] post, int width, int height) {
        int edge = POST_MARGIN + Math.max(StructureKind.RADIO_HUT.along, StructureKind.OBSERVATION_POST.along);
        return post[0] >= edge && post[1] >= edge && post[0] < width - edge && post[1] < height - edge;
    }

    /** Whether every cell of {@code s} is open country: buildable, no place's, and no way over it. */
    private static boolean stands(LayoutGround ground, Structure s) {
        if (s.x() < 1 || s.y() < 1 || s.x() + s.width() >= ground.width() || s.y() + s.height() >= ground.height()) {
            return false;
        }
        for (int x = s.x(); x < s.x() + s.width(); x++) {
            for (int y = s.y(); y < s.y() + s.height(); y++) {
                if (!ground.buildable(x, y) || ground.isWay(x, y) || ground.isClaimed(x, y)) return false;
            }
        }
        return true;
    }

    /** A structure of {@code kind} centred on {@code (cx, cy)}. */
    private static Structure post(String name, StructureKind kind, int cx, int cy) {
        return new Structure(name, kind, cx - kind.along / 2, cy - kind.deep / 2, 0, false);
    }

    /**
     * The base at work (owner, 2026-11-10: each structure a real base needs, placed for its job): a belt of hedgehogs
     * round its wall and a sandbagged checkpoint at every gate. By each building what it works with: armour in rows
     * by the motor sheds; pallets, crates and a cargo truck at the supply depot; the fuel depot's tanks beside the
     * fuel point; sandbags round the magazine; the radar mast by dispatch. In the garrison ({@link #garrisonWorks}):
     * the command post's sat comms dishes and radar, the generator hall's generator sets, the armoury's sandbags, the
     * workshops' machine tools by the vehicle sheds. The open ground left is then the parade square, the drill yards
     * and the vehicle park.
     */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        perimeter(c, plan, works);
        int[] bounds = claimBounds(c, plan.precincts().indexOf(works));
        if (bounds != null) MilitaryGround.watchtowers(c, bounds);
        // The base's buildings and its garrison's are built alike, so the two read as one cantonment: olive sheet
        // walls, hangar roofs over the sheds, the depot and the fuel house, hut roofs over dispatch and the barracks.
        for (PointOfInterest b : WorksProps.buildings(c, plan, works)) {
            oliveWalls(c, b);
            RoomPurpose purpose = WorksProps.purposeOf(c, b);
            if (purpose == MOTOR_POOL || purpose == SUPPLY_DEPOT || purpose == FUEL_POINT) {
                roof(c, b, CellTopology.RoofKind.HANGAR);
            } else if (purpose == DISPATCH) {
                roof(c, b, CellTopology.RoofKind.HUT);
            }
        }
        for (Precinct p : plan.precincts()) {
            if (!RaidSiteTemplate.GARRISON.equals(p.name())) continue;
            for (PointOfInterest b : WorksProps.buildings(c, plan, p)) {
                oliveWalls(c, b);
                RoomPurpose purpose = WorksProps.purposeOf(c, b);
                roof(c, b, purpose == RoomPurpose.VEHICLE_BAY ? CellTopology.RoofKind.HANGAR : CellTopology.RoofKind.HUT);
            }
        }
        int n = 0;
        for (PointOfInterest b : WorksProps.buildings(c, plan, works)) {
            RoomPurpose purpose = WorksProps.purposeOf(c, b);
            if (purpose == null) continue;
            switch (purpose) {
                case PARTS_CAGE -> motorPark(c, b, n++);
                case STOCKROOM -> depotYard(c, b);
                case ENGINE_ROOM -> fuelPoint(c, b);
                case ARMORY -> revetment(c, b);
                case CONTROL_ROOM -> {
                    if (width(b) > RaidWorksParts.GUARD_POST.width() || height(b) > RaidWorksParts.GUARD_POST.height()) {
                        aside(c, b, 2, List.of("doodad.military-radar-dish"), 1);
                    }
                }
                default -> { }
            }
        }
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct p = plan.precincts().get(i);
            if (!RaidSiteTemplate.GARRISON.equals(p.name())) continue;
            garrisonWorks(c, plan, p);
            MilitaryGround.drillYards(c, i, MilitaryGround.paradeSquare(c, i));
        }
        vehiclePark(c, plan, works);
    }

    /** Sat comms dishes the command post keeps beside it. UNTUNED. */
    static final int SATCOM_DISHES = 2;

    /**
     * The garrison's buildings at their jobs, on a military site: the command post with its sat comms dishes, radar
     * and flag; the generator hall with its generator sets, a cable reel and a fuel tank; the armoury behind
     * sandbags; the workshops with machine tools at their doors; armour by the vehicle sheds; a tanker and tanks at
     * the fuel point. Each stands along its building's wall, so it reads as that building's.
     */
    private static void garrisonWorks(GenContext c, PrecinctPlan plan, Precinct garrison) {
        int sheds = 0;
        boolean generators = false;
        PointOfInterest hq = null;
        for (PointOfInterest b : WorksProps.buildings(c, plan, garrison)) {
            RoomPurpose purpose = WorksProps.purposeOf(c, b);
            if (purpose == null) continue;
            switch (purpose) {
                case KEEP_THRONE -> {
                    commandPost(c, b);
                    hq = b;
                }
                case ENGINE_ROOM -> generators |= near(c, b, 1, GENERATOR_SETS) > 0;
                case ARMORY -> revetment(c, b);
                case PARTS_CAGE -> aside(c, b, 1, List.of("doodad.industrial-machine-tool",
                        "doodad.industrial-crate-stack", "doodad.industrial-machine-tool",
                        "doodad.industrial-drum-cluster"), 4);
                case VEHICLE_BAY -> motorPark(c, b, sheds++);
                case STOCKROOM -> {
                    if (Math.min(width(b), height(b)) <= FUEL_HOUSE.height()
                            && Math.max(width(b), height(b)) <= FUEL_HOUSE.width()) {
                        fuelPoint(c, b);
                    }
                }
                default -> { }
            }
        }
        // A garrison whose generator hall did not stand still powers its command post: the sets stand by it.
        if (!generators && hq != null) near(c, hq, 2, GENERATOR_SETS);
    }

    /** The generator sets, their cable reel and their day tank, stood by whatever they power. */
    private static final List<String> GENERATOR_SETS = List.of("doodad.industrial-generator",
            "doodad.industrial-generator", "doodad.industrial-generator", "doodad.industrial-cable-reel",
            "doodad.industrial-fluid-tank");

    /** Farthest out from its building a structure looks for open ground, cells. UNTUNED. */
    static final int NEAR_MOST = 14;
    /**
     * Cells a building's drawn walls lean out over its low-x and high-y sides (the view looks down from that way), so
     * a prop stood within them there is hidden under its roof. UNTUNED.
     */
    static final int LEAN = 6;

    /**
     * The command post: its sat comms dishes on the nearest open ground round it, then the radar mast and the flag,
     * so the dishes, mast and flag mark it out from the barracks round it.
     */
    private static void commandPost(GenContext c, PointOfInterest hq) {
        List<String> dishes = new ArrayList<>();
        for (int i = 0; i < SATCOM_DISHES; i++) dishes.add("doodad.listening-dish");
        near(c, hq, 2, dishes);
        near(c, hq, 1, List.of("doodad.military-radar-dish", "doodad.flag-mast"));
    }

    /**
     * {@code props} in order on the open ground nearest building {@code b}: each at the first spot, ring by ring from
     * {@code gap} cells off its walls out to {@link #NEAR_MOST}, where its whole footprint stands. Returns how many
     * stood.
     */
    static int near(GenContext c, PointOfInterest b, int gap, List<String> props) {
        int placed = 0;
        for (String id : props) {
            boolean stood = false;
            for (int r = gap + 1; r <= NEAR_MOST && !stood; r++) {
                for (int x = b.left - r; x <= b.right + r && !stood; x++) {
                    for (int y = b.top - r; y <= b.bottom + r && !stood; y++) {
                        boolean ring = x == b.left - r || x == b.right + r || y == b.top - r || y == b.bottom + r;
                        if (!ring) continue;
                        // A prop beyond the building's left or top side stands back from its corner by its size.
                        int size = footprint(id);
                        int px = x < b.left ? x - size + 1 : x, py = y < b.top ? y - size + 1 : y;
                        if (hidden(b, px, py, size)) continue;
                        stood = WorksProps.place(c, px, py, id, 0);
                    }
                }
            }
            if (stood) placed++;
        }
        return placed;
    }

    /** Whether a {@code size}-square prop at {@code (x, y)} stands under building {@code b}'s leaning walls. */
    private static boolean hidden(PointOfInterest b, int x, int y, int size) {
        boolean alongY = y + size - 1 >= b.top && y <= b.bottom + LEAN;
        boolean alongX = x + size - 1 >= b.left - LEAN && x <= b.right;
        return (x + size - 1 < b.left && b.left - (x + size - 1) <= LEAN && alongY)
                || (y > b.bottom && y - b.bottom <= LEAN && alongX);
    }

    /** The larger side of a prop's footprint, cells; one where it is not installed. */
    private static int footprint(String id) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(id);
        if (def == null) return 1;
        Doodad d = new Doodad(0, 0, def, 0);
        return Math.max(d.footprintCellsX, d.footprintCellsY);
    }

    /** Cells out from the works' ground the hedgehog belt runs, and cells between two hedgehogs along it. UNTUNED. */
    static final int BELT_OUT = 2, BELT_STEP = 3;
    /** Cells out from a gate's mouth its checkpoint's sandbag positions stand, and to either side of the gate. UNTUNED. */
    static final int CHECKPOINT_OUT = 2, CHECKPOINT_ASIDE = 2, CHECKPOINT_RUN = 3;

    /**
     * The base's perimeter: a hedgehog belt {@link #BELT_OUT} cells outside its ground, every {@link #BELT_STEP}th
     * cell so a man can pass between two (never across a road, which stays open), and at each gate a checkpoint, a
     * run of sandbags either side of the mouth facing out.
     */
    private static void perimeter(GenContext c, PrecinctPlan plan, Precinct works) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM), owner = c.get(BspKeys.PRECINCT_ROAD);
        int index = plan.precincts().indexOf(works);
        if (claim == null || index < 0) return;
        int step = 0;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != GrownTrunkPlan.UNOWNED || distanceTo(claim, index, x, y, BELT_OUT) != BELT_OUT) continue;
                if ((x + y) % BELT_STEP == 0) WorksProps.place(c, x, y, "doodad.fort-hedgehog", 0);
            }
        }
        if (owner == null) return;
        for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, index, c.width, c.height,
                Integer.MAX_VALUE)) {
            checkpoint(c, claim, index, gate);
        }
    }

    /** A sandbag run either side of a gate's mouth, {@link #CHECKPOINT_OUT} cells out, facing away from the works. */
    private static void checkpoint(GenContext c, int[][] claim, int index, PrecinctBoundary.Gate gate) {
        if (gate.cells().isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int[] cell : gate.cells()) {
            minX = Math.min(minX, cell[0]); minY = Math.min(minY, cell[1]);
            maxX = Math.max(maxX, cell[0]); maxY = Math.max(maxY, cell[1]);
        }
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        for (Side out : Side.values()) {
            int ox = cx + out.dx * (CHECKPOINT_OUT + 1), oy = cy + out.dy * (CHECKPOINT_OUT + 1);
            if (!c.grid.inBounds(ox, oy) || claim[ox][oy] == index) continue;
            // The mouth runs across the outward side; its sandbags stand beyond each end of it.
            boolean across = out.dx == 0;
            int lo = across ? minX : minY, hi = across ? maxX : maxY;
            String facing = "doodad.sandbag-straight-" + out.name().substring(0, 1).toLowerCase(Locale.ROOT);
            for (int k = 0; k < CHECKPOINT_RUN; k++) {
                int a = lo - CHECKPOINT_ASIDE - 1 - k, b = hi + CHECKPOINT_ASIDE + 1 + k;
                int line = (across ? cy : cx) + (across ? out.dy : out.dx) * CHECKPOINT_OUT;
                WorksProps.place(c, across ? a : line, across ? line : a, facing, 0);
                WorksProps.place(c, across ? b : line, across ? line : b, facing, 0);
            }
            return;
        }
    }

    /** Chebyshev distance from {@code (x, y)} to the nearest cell of place {@code index}, up to {@code most + 1}. */
    private static int distanceTo(int[][] claim, int index, int x, int y, int most) {
        for (int r = 1; r <= most; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < claim.length && ny < claim[0].length && claim[nx][ny] == index) {
                        return r;
                    }
                }
            }
        }
        return most + 1;
    }

    /** The surface a base building's walls are built of: olive sheet, so its roof is the sheet roof a base wears. */
    static final String BASE_WALL = "colony.olive-wall";

    /** The inclusive bounds {x0, y0, x1, y1} of place {@code index}'s ground, or null where it has none. */
    private static int[] claimBounds(GenContext c, int index) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        if (claim == null || index < 0) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != index) continue;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
    }

    /** Marks a building's roof {@code kind}; a gun lot's building keeps its fort works roof. */
    private static void roof(GenContext c, PointOfInterest b, CellTopology.RoofKind kind) {
        for (int x = b.left; x <= b.right; x++) {
            for (int y = b.top; y <= b.bottom; y++) {
                if (c.topology.getWorksStyle(x, y) != 0) return;
            }
        }
        for (int x = b.left; x <= b.right; x++) {
            for (int y = b.top; y <= b.bottom; y++) c.topology.setRoofKind(x, y, kind);
        }
    }

    /** Rebuilds a building's walls in {@link #BASE_WALL}. */
    private static void oliveWalls(GenContext c, PointOfInterest b) {
        int surface = c.topology.surfaceIndex(BASE_WALL);
        for (int x = b.left - 1; x <= b.right + 1; x++) {
            for (int y = b.top - 1; y <= b.bottom + 1; y++) {
                if (c.grid.inBounds(x, y) && c.topology.isWall(x, y)) c.topology.setSurface(x, y, surface);
            }
        }
    }

    /** Cells between ranks of the vehicle park, and between vehicles along a rank. UNTUNED. */
    static final int PARK_RANKS = 7, PARK_FILE = 3;
    /** Most vehicles the park stands. UNTUNED. */
    static final int PARK_MOST = 36;
    /** Cells the park keeps from any building, so a shed's apron and doors stay clear. UNTUNED. */
    static final int PARK_CLEAR = 4;

    /**
     * The vehicle park: patrol vehicles nose to tail in ranks across the works' open yard, a lane between every two
     * ranks and a gap between every two vehicles, kept clear of the buildings' aprons.
     */
    private static void vehiclePark(GenContext c, PrecinctPlan plan, Precinct works) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        if (claim == null || index < 0) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != index) continue;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
        }
        // A rank is one kind: the tanks drawn up together, the carriers in the next rank.
        int placed = 0, rank = 0;
        for (int y = y0 + PARK_CLEAR; y <= y1 - PARK_CLEAR && placed < PARK_MOST; y += PARK_RANKS, rank++) {
            String vehicle = ARMOUR.get(rank % ARMOUR.size());
            for (int x = x0 + PARK_CLEAR; x <= x1 - PARK_CLEAR && placed < PARK_MOST; x += PARK_FILE) {
                if (claim[x][y] != index || nearBuilding(c, x, y, PARK_CLEAR)) continue;
                if (WorksProps.place(c, x, y, vehicle, 1)) placed++;
            }
        }
    }

    /** Whether a building stands within {@code r} cells of {@code (x, y)}. */
    private static boolean nearBuilding(GenContext c, int x, int y, int r) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, ny = y + dy;
                if (c.grid.inBounds(nx, ny) && c.topology.hasBuildingKindHint(nx, ny)) return true;
            }
        }
        return false;
    }

    /** The base's own vehicles, the whole fleet but its logistics trucks: tanks and armoured personnel carriers. */
    private static final List<String> ARMOUR = List.of("doodad.parked-tank", "doodad.parked-apc-army");

    /** A row of the base's vehicles along a motor shed's long side, and drums at its corner. */
    private static void motorPark(GenContext c, PointOfInterest shed, int n) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < 3; i++) row.add(ARMOUR.get((n + i) % ARMOUR.size()));
        aside(c, shed, 2, row, 3);
        corner(c, shed, "doodad.industrial-drum-cluster");
    }

    /** Pallets and crates stacked along the depot's walls, a cargo truck being loaded at it. */
    private static void depotYard(GenContext c, PointOfInterest depot) {
        aside(c, depot, 1, List.of("doodad.industrial-pallet-stack", "doodad.industrial-crate-stack",
                "doodad.industrial-pallet-stack", "doodad.industrial-crate-stack"), 4);
        aside(c, depot, 3, List.of("doodad.parked-cargo-truck"), 1);
    }

    /** Fuel tanks the fuel depot keeps beside the fuel point. UNTUNED. */
    static final int DEPOT_TANKS = 4;

    /**
     * The fuel point and its fuel depot: the tanker filling at the pump house, and on its other side the depot's
     * tanks in a row a little further out, drums at the row's end.
     */
    private static void fuelPoint(GenContext c, PointOfInterest fuel) {
        aside(c, fuel, 2, List.of("doodad.parked-tanker-truck", "doodad.industrial-drum-cluster"), 2);
        List<String> tanks = new ArrayList<>();
        for (int i = 0; i < DEPOT_TANKS; i++) tanks.add("doodad.industrial-fluid-tank");
        tanks.add("doodad.industrial-drum-cluster");
        aside(c, fuel, 4, tanks, tanks.size());
    }

    /** Sandbags round the magazine, a cell out from its walls, broken only where they would block its door. */
    private static void revetment(GenContext c, PointOfInterest mag) {
        int x0 = mag.left - 2, x1 = mag.right + 2, y0 = mag.top - 2, y1 = mag.bottom + 2;
        for (int x = x0; x <= x1; x++) {
            WorksProps.place(c, x, y0, "doodad.sandbag-straight-s", 0);
            WorksProps.place(c, x, y1, "doodad.sandbag-straight-n", 0);
        }
        for (int y = y0 + 1; y < y1; y++) {
            WorksProps.place(c, x0, y, "doodad.sandbag-straight-w", 0);
            WorksProps.place(c, x1, y, "doodad.sandbag-straight-e", 0);
        }
    }

    /**
     * Props in a row along the first of a building's sides (its long sides first) where they all stand, {@code gap}
     * cells out from its wall, up to {@code count} of them in order, each tried turned both ways.
     */
    private static void aside(GenContext c, PointOfInterest b, int gap, List<String> props, int count) {
        boolean wide = width(b) >= height(b);
        int[][] sides = wide
                ? new int[][]{{0, b.top - gap - 1}, {0, b.bottom + gap + 1}, {1, b.left - gap - 1}, {1, b.right + gap + 1}}
                : new int[][]{{1, b.right + gap + 1}, {1, b.left - gap - 1}, {0, b.top - gap - 1}, {0, b.bottom + gap + 1}};
        for (int[] side : sides) {
            int placed = 0;
            boolean along = side[0] == 0;
            int from = along ? b.left : b.top, to = along ? b.right : b.bottom;
            for (int t = from; t <= to && placed < count; t++) {
                String id = props.get(placed % props.size());
                int x = along ? t : side[1], y = along ? side[1] : t;
                if (WorksProps.place(c, x, y, id, along ? 0 : 1) || WorksProps.place(c, x, y, id, along ? 1 : 0)) {
                    placed++;
                    t += 2;
                }
            }
            if (placed > 0) return;
        }
    }

    /** A prop at the first of a building's outside corners where it stands. */
    private static void corner(GenContext c, PointOfInterest b, String id) {
        int[][] at = {{b.right + 2, b.bottom + 2}, {b.left - 3, b.bottom + 2}, {b.right + 2, b.top - 3},
                {b.left - 3, b.top - 3}};
        for (int[] p : at) if (WorksProps.place(c, p[0], p[1], id, 0)) return;
    }

    private static int width(PointOfInterest b) { return b.right - b.left + 1; }

    private static int height(PointOfInterest b) { return b.bottom - b.top + 1; }
}
