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
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

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

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    @Override public PrecinctCharacter industryCharacter() { return PrecinctCharacter.DEPOT; }

    /**
     * The patrol base: a motor shed per machine, the supply depot (two from {@link #SECOND_DEPOT_FROM} sheds), the
     * magazine and the fuel point, dispatch by the gate, and the posts its gates are watched from.
     */
    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a motor pool has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(SUPPLY_DEPOT, DEPOT_SHED, Ward.REAR, sheds >= SECOND_DEPOT_FROM ? 2 : 1),
                new FortressBuilding(MAGAZINE, MAGAZINE_BUNKER, Ward.REAR, 1),
                new FortressBuilding(FUEL_POINT, FUEL_HOUSE, Ward.YARD, 1),
                new FortressBuilding(MOTOR_POOL, MOTOR_SHED, Ward.YARD, sheds),
                new FortressBuilding(DISPATCH, DISPATCH_ROOM, Ward.FRONTAGE, 1),
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
     * The base at work: the vehicle park's ranks on the open yard; trucks in rows by the motor sheds and oil drums at
     * their corners; pallets and crates along the supply depot and a cargo truck at it; a tanker, a tank and drums at the fuel point; sandbags round the magazine;
     * the radar mast by dispatch.
     */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        vehiclePark(c, plan, works);
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
        int placed = 0;
        for (int y = y0 + PARK_CLEAR; y <= y1 - PARK_CLEAR && placed < PARK_MOST; y += PARK_RANKS) {
            for (int x = x0 + PARK_CLEAR; x <= x1 - PARK_CLEAR && placed < PARK_MOST; x += PARK_FILE) {
                if (claim[x][y] != index || nearBuilding(c, x, y, PARK_CLEAR)) continue;
                if (WorksProps.place(c, x, y, TRUCKS.get(placed % TRUCKS.size()), 1)) placed++;
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

    private static final List<String> TRUCKS = List.of("doodad.parked-cargo-truck", "doodad.parked-flatbed-truck",
            "doodad.parked-utility-truck");

    /** A row of patrol trucks along a motor shed's long side, and drums at its corner. */
    private static void motorPark(GenContext c, PointOfInterest shed, int n) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < 3; i++) row.add(TRUCKS.get((n + i) % TRUCKS.size()));
        aside(c, shed, 2, row, 3);
        corner(c, shed, "doodad.industrial-drum-cluster");
    }

    /** Pallets and crates stacked along the depot's walls, a cargo truck being loaded at it. */
    private static void depotYard(GenContext c, PointOfInterest depot) {
        aside(c, depot, 1, List.of("doodad.industrial-pallet-stack", "doodad.industrial-crate-stack",
                "doodad.industrial-pallet-stack", "doodad.industrial-crate-stack"), 4);
        aside(c, depot, 3, List.of("doodad.parked-cargo-truck"), 1);
    }

    /** The tanker at the fuel point, its tank and drums. */
    private static void fuelPoint(GenContext c, PointOfInterest fuel) {
        aside(c, fuel, 2, List.of("doodad.parked-tanker-truck", "doodad.industrial-fluid-tank",
                "doodad.industrial-drum-cluster"), 3);
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
                : new int[][]{{1, b.left - gap - 1}, {1, b.right + gap + 1}, {0, b.top - gap - 1}, {0, b.bottom + gap + 1}};
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
