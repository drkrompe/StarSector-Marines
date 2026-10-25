package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.CordonedFacility;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.District;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.QuarterClaim;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The cordoned facility layout ({@code set-piece-districts.md}) over a planned {@link QuarterClaim.Quarter}.
 * One wall runs round the quarter's outer rim, the precinct wall's stuff ({@link InstallationWalls#PERIMETER},
 * {@link CompoundWallApertures} slits, turreted corners as on a military base), at the strength the
 * facility's character asks ({@link Fortification#GARRISON}, or a colony's civil wall for a civil-defence
 * centre). Each gatehouse is a solid block either side of the lane its program kept, a boom gate across the lane
 * just inside it beside a sandbag course facing out, and bollards before its jambs where the street outside
 * allows ({@link StreetDressing}). The inner streets are the facility's own: paved city street, kept clear for
 * vehicles on the road reservation, with hedgehogs and street lamps by turns at their kerbs. Every building faces the main cross street with its
 * doors and windows there and blank walls elsewhere, standing back from the wall behind an apron: the admin
 * block, the labs or workshops, and a vehicle shed with a forecourt of parked vehicles. The yard is
 * hardstanding of vehicles and stores, the courtyard an open paved square. Draws only from the map's random
 * stream, inside the shell carves.
 */
final class CordonedFacilityLayout {

    /** Cells between the quarter's edge and a building: the wall, which the building backs onto. */
    static final int WALL_SETBACK = 1;
    /** Cells between an inner street and a building: the pavement, which runs round to the wall. */
    static final int STREET_SETBACK = 1;
    /** The least shell side that stands a room. */
    static final int SHELL_MIN = 5;
    /** Rows of a depot's forecourt in front of its shed: at least the least, more where the leaf is deep. */
    static final int FORECOURT_MIN = 3, FORECOURT_MAX = 5;
    /** Cells along a kerb between one barrier and the next. */
    static final int BARRIER_RHYTHM = 4;

    private static final String SANDBAG_PREFIX = "doodad.sandbag-straight-";
    private static final String HEDGEHOG = "doodad.fort-hedgehog";
    private static final String PLANTER_H = "doodad.residential-planter-h";
    private static final String PLANTER_V = "doodad.residential-planter-v";
    private static final String RADAR = "doodad.military-radar-dish";
    private static final String[] YARD_PROPS = {
            "doodad.industrial-crate-stack", "doodad.industrial-pallet-stack", "doodad.industrial-drum-cluster"
    };

    private CordonedFacilityLayout() { }

    /** Lays the facility over {@code district}. Publishes its mark. */
    static CordonedFacility place(GenContext c, District district) {
        QuarterClaim.Quarter q = (QuarterClaim.Quarter) district.claim;
        TileRegistry registry = TileRegistry.installed();
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        boolean[][] reservation = c.get(BspKeys.ROAD_RESERVATION);
        int[] b = q.bounds();
        DistrictMap zoning = c.get(BspKeys.DISTRICT_MAP);
        CordonedFacility.Character character = CordonedFacilityProgram.character(
                zoning == null ? null : zoning.themeAt((b[0] + b[2]) / 2, (b[1] + b[3]) / 2));
        List<CordonedFacilityProgram.Gate> gates = CordonedFacilityProgram.gates(q, road);

        streets(c, q, road);
        List<CordonedFacility.Building> buildings = new ArrayList<>();
        int[] courtyard = {-1, -1, -1, -1};
        for (BlockLeaf leaf : q.members()) {
            District.Role role = district.roles.get(leaf);
            if (role == District.Role.COURTYARD) {
                courtyard = courtyard(c, q, leaf, character, registry);
                continue;
            }
            buildings.add(member(c, q, leaf, role, character, registry));
        }
        int hp = character == CordonedFacility.Character.CIVIL_DEFENCE
                ? CivilDefenceCompound.WALL_HP : Fortification.GARRISON.wallHp();
        List<CordonedFacility.Gatehouse> gatehouses = wall(c, q, gates, road, hp);
        for (CordonedFacilityProgram.Gate g : gates) {
            checkpoint(c, g, road, reservation);
            sandbags(c, g, road, reservation, registry);
        }
        barriers(c, q, road, reservation, gates, registry);

        CordonedFacility facility = new CordonedFacility(character, b[0], b[1], b[2], b[3], gatehouses,
                courtyard[0], courtyard[1], courtyard[2], courtyard[3], buildings);
        c.cordonedFacilities.add(facility);
        return facility;
    }

    /** The side a member's front faces: onto the main cross street. */
    static BuildingPlacement.Side front(QuarterClaim.Quarter q, BlockLeaf leaf) {
        boolean beforeMain = q.rowOf(leaf) < q.rows.size() - 1;
        if (q.horizontal) return beforeMain ? BuildingPlacement.Side.BOTTOM : BuildingPlacement.Side.TOP;
        return beforeMain ? BuildingPlacement.Side.RIGHT : BuildingPlacement.Side.LEFT;
    }

    /**
     * The shell a member's building stands in: back from the wall on its outer sides and from the street on
     * its inner ones, or null where that leaves no room.
     */
    static int[] shell(QuarterClaim.Quarter q, BlockLeaf leaf) {
        int[] b = q.bounds();
        int l = leaf.left + (leaf.left == b[0] ? WALL_SETBACK : STREET_SETBACK);
        int t = leaf.top + (leaf.top == b[1] ? WALL_SETBACK : STREET_SETBACK);
        int r = leaf.right - (leaf.right == b[2] ? WALL_SETBACK : STREET_SETBACK);
        int bo = leaf.bottom - (leaf.bottom == b[3] ? WALL_SETBACK : STREET_SETBACK);
        if (r - l + 1 < SHELL_MIN || bo - t + 1 < SHELL_MIN) return null;
        return new int[]{l, t, r, bo};
    }

    /** A building member: its apron, then its shell facing the main street. */
    private static CordonedFacility.Building member(GenContext c, QuarterClaim.Quarter q, BlockLeaf leaf,
                                                   District.Role role, CordonedFacility.Character character,
                                                   TileRegistry registry) {
        GroundKind apron = role == District.Role.DEPOT || role == District.Role.YARD
                ? GroundKind.STRIPED : GroundKind.STONE;
        ground(c, leaf, apron);
        BuildingPlacement.Side side = front(q, leaf);
        int[] shell = shell(q, leaf);
        if (role == District.Role.YARD || shell == null) {
            if (role == District.Role.YARD) yard(c, leaf, registry);
            return new CordonedFacility.Building(role, leaf.left, leaf.top, leaf.right, leaf.bottom, -1, -1);
        }
        int[] forecourt = null;
        if (role == District.Role.DEPOT) {
            forecourt = forecourt(shell, side);
            if (forecourt != null) shell = behind(shell, forecourt, side);
        }
        BlockLeaf rect = new BlockLeaf(shell[0], shell[1], shell[2], shell[3], false);
        PointOfInterest poi = BuildingShellCore.carve(rect, c.grid, c.topology, c.doodads, c.rng,
                config(role, character), BuildingPlacement.frontOnly(side));
        if (poi != null) c.pois.add(poi);
        int[] door = door(c.grid, shell, side);
        if (forecourt != null) forecourt(c, forecourt, side, character, registry);
        return new CordonedFacility.Building(role, shell[0], shell[1], shell[2], shell[3], door[0], door[1]);
    }

    private static BuildingShellCore.BuildingConfig config(District.Role role, CordonedFacility.Character character) {
        if (role == District.Role.ADMIN) return BuildingCivicFiller.CONFIG;
        if (role == District.Role.DEPOT) return MilitaryBaseFiller.VEHICLE_BAY_CONFIG;
        return character == CordonedFacility.Character.DEPOT
                ? BuildingIndustrialFiller.CONFIG : MedicalCampusFiller.CLINIC_CONFIG;
    }

    /** The front rows of a depot's shell given to its forecourt, or null where the shed would not stand behind it. */
    private static int[] forecourt(int[] shell, BuildingPlacement.Side side) {
        int depth = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? shell[3] - shell[1] + 1 : shell[2] - shell[0] + 1;
        int rows = Math.min(FORECOURT_MAX, depth - SHELL_MIN);
        if (rows < FORECOURT_MIN) return null;
        return switch (side) {
            case TOP -> new int[]{shell[0], shell[1], shell[2], shell[1] + rows - 1};
            case BOTTOM -> new int[]{shell[0], shell[3] - rows + 1, shell[2], shell[3]};
            case LEFT -> new int[]{shell[0], shell[1], shell[0] + rows - 1, shell[3]};
            case RIGHT -> new int[]{shell[2] - rows + 1, shell[1], shell[2], shell[3]};
        };
    }

    /** The shell less its forecourt. */
    private static int[] behind(int[] shell, int[] court, BuildingPlacement.Side side) {
        return switch (side) {
            case TOP -> new int[]{shell[0], court[3] + 1, shell[2], shell[3]};
            case BOTTOM -> new int[]{shell[0], shell[1], shell[2], court[1] - 1};
            case LEFT -> new int[]{court[2] + 1, shell[1], shell[2], shell[3]};
            case RIGHT -> new int[]{shell[0], shell[1], court[0] - 1, shell[3]};
        };
    }

    /** The doorway in a shell's front wall nearest its middle, or {@code -1, -1}. */
    private static int[] door(NavigationGrid grid, int[] shell, BuildingPlacement.Side side) {
        boolean row = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM;
        int fixed = switch (side) {
            case TOP -> shell[1];
            case BOTTOM -> shell[3];
            case LEFT -> shell[0];
            case RIGHT -> shell[2];
        };
        int lo = row ? shell[0] : shell[1], hi = row ? shell[2] : shell[3], mid = (lo + hi) / 2, best = -1;
        for (int a = lo; a <= hi; a++) {
            if (!grid.isDoorway(row ? a : fixed, row ? fixed : a)) continue;
            if (best < 0 || Math.abs(a - mid) < Math.abs(best - mid)) best = a;
        }
        if (best < 0) return new int[]{-1, -1};
        return row ? new int[]{best, fixed} : new int[]{fixed, best};
    }

    /** A depot's forecourt: striped hardstanding with a vehicle parked on it, across the shed's front. */
    private static void forecourt(GenContext c, int[] court, BuildingPlacement.Side side,
                                  CordonedFacility.Character character, TileRegistry registry) {
        boolean row = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM;
        String id = character == CordonedFacility.Character.CIVIL_DEFENCE ? "doodad.parked-armored-apc"
                : character == CordonedFacility.Character.DEPOT ? "doodad.parked-cargo-truck"
                : "doodad.parked-utility-truck";
        DoodadDef def = registry.doodad(id);
        if (def == null) return;
        // Parked along the shed's front, a cell clear of its wall and to one side of its middle doors.
        Doodad probe = new Doodad(0, 0, def, row ? 0 : 1);
        int x = row ? court[0] + 1 : (side == BuildingPlacement.Side.LEFT ? court[0] + 1 : court[2] - probe.footprintCellsX);
        int y = row ? (side == BuildingPlacement.Side.TOP ? court[1] + 1 : court[3] - probe.footprintCellsY) : court[1] + 1;
        if (x + probe.footprintCellsX - 1 > court[2] || y + probe.footprintCellsY - 1 > court[3]) return;
        prop(c, x, y, def, row ? 0 : 1);
    }

    /** The yard: hardstanding with stores stacked in a loose row along its back half. */
    private static void yard(GenContext c, BlockLeaf leaf, TileRegistry registry) {
        int[] shell = {leaf.left + WALL_SETBACK, leaf.top + WALL_SETBACK, leaf.right - WALL_SETBACK,
                leaf.bottom - WALL_SETBACK};
        int k = 0;
        for (int y = shell[1]; y <= shell[3]; y += 3) {
            for (int x = shell[0]; x <= shell[2]; x += 3) {
                if (c.rng.nextInt(3) == 0) continue;
                DoodadDef def = registry.doodad(YARD_PROPS[k++ % YARD_PROPS.length]);
                if (def != null && c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y)) {
                    c.doodads.add(new Doodad(x, y, def));
                }
            }
        }
    }

    /**
     * The courtyard: an open paved square with a stone border, a planter at each corner and, at a
     * civil-defence centre or research station, the mast in the middle. Returns its bounds.
     */
    private static int[] courtyard(GenContext c, QuarterClaim.Quarter q, BlockLeaf leaf,
                                   CordonedFacility.Character character, TileRegistry registry) {
        int[] b = q.bounds();
        int l = leaf.left + (leaf.left == b[0] ? 1 : 0), t = leaf.top + (leaf.top == b[1] ? 1 : 0);
        int r = leaf.right - (leaf.right == b[2] ? 1 : 0), bo = leaf.bottom - (leaf.bottom == b[3] ? 1 : 0);
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                c.grid.setWalkableFloor(x, y);
                c.grid.openAllEdges(x, y);
                boolean ring = x == l || x == r || y == t || y == bo;
                c.topology.setGroundKind(x, y, ring ? GroundKind.STONE : GroundKind.BRICK);
                c.markMadeGround(x, y);
            }
        }
        boolean wide = r - l >= bo - t;
        DoodadDef planter = registry.doodad(wide ? PLANTER_H : PLANTER_V);
        for (int[] corner : new int[][]{{l + 1, t + 1}, {r - 1, t + 1}, {l + 1, bo - 1}, {r - 1, bo - 1}}) {
            solid(c, corner[0], corner[1], planter);
        }
        DoodadDef mast = registry.doodad(RADAR);
        if (character != CordonedFacility.Character.DEPOT && mast != null && r - l >= 6 && bo - t >= 6) {
            solid(c, (l + r) / 2, (t + bo) / 2, mast);
        }
        return new int[]{l, t, r, bo};
    }

    /** The inner streets are paved city street, wherever the settlement would have left a lane. */
    private static void streets(GenContext c, QuarterClaim.Quarter q, boolean[][] road) {
        for (QuarterClaim.Street s : q.streets) {
            for (int y = s.top(); y <= s.bottom(); y++) {
                for (int x = s.left(); x <= s.right(); x++) {
                    if (!road[x][y]) continue;
                    c.topology.setGroundKind(x, y, GroundKind.STREET);
                    c.topology.setSurface(x, y, 0);
                    c.topology.setNatureOverlayIndex(x, y, -1);
                }
            }
        }
    }

    /**
     * The wall: the quarter's outer rim and each gatehouse's blocks either side of its lane, every lane left
     * open. Faced outward, dressed as the precinct wall, slit and turreted at its corners. Returns the
     * gatehouses.
     */
    private static List<CordonedFacility.Gatehouse> wall(GenContext c, QuarterClaim.Quarter q,
                                                         List<CordonedFacilityProgram.Gate> gates,
                                                         boolean[][] road, int hp) {
        int[] b = q.bounds();
        boolean[][] wall = new boolean[c.width][c.height];
        boolean[][] inside = new boolean[c.width][c.height];
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                boolean rim = x == b[0] || x == b[2] || y == b[1] || y == b[3];
                if (rim && !road[x][y]) wall[x][y] = true;
                else if (!rim) inside[x][y] = true;
            }
        }
        List<CordonedFacility.Gatehouse> out = new ArrayList<>();
        List<int[]> posts = new ArrayList<>();
        for (CordonedFacilityProgram.Gate g : gates) {
            // The gatehouse: the street either side of the lane and the pavement cell beyond each kerb, as deep
            // as the gatehouse; the lane through it striped as a checkpoint.
            for (int d = 0; d < CordonedFacilityProgram.GATEHOUSE_DEPTH; d++) {
                int a = g.wall() + g.inward() * d;
                for (int cc = g.lo() - 1; cc <= g.hi() + 1; cc++) {
                    int x = g.x(a, cc), y = g.y(a, cc);
                    if (cc >= g.laneLo() && cc <= g.laneHi()) {
                        c.topology.setGroundKind(x, y, GroundKind.STRIPED);
                        c.markMadeGround(x, y);
                        continue;
                    }
                    wall[x][y] = true;
                    inside[x][y] = false;
                }
            }
            posts.add(new int[]{g.x(g.wall(), g.laneLo() - 1), g.y(g.wall(), g.laneLo() - 1)});
            posts.add(new int[]{g.x(g.wall(), g.laneHi() + 1), g.y(g.wall(), g.laneHi() + 1)});
            int mid = (g.laneLo() + g.laneHi()) / 2;
            int outA = g.wall() - g.inward(), inA = g.wall() + g.inward() * CordonedFacilityProgram.GATEHOUSE_DEPTH;
            out.add(new CordonedFacility.Gatehouse(g.x(g.wall(), g.laneLo()), g.y(g.wall(), g.laneLo()),
                    g.x(g.wall(), g.laneHi()), g.y(g.wall(), g.laneHi()), g.x(outA, mid), g.y(outA, mid),
                    g.x(inA, mid), g.y(inA, mid)));
        }
        CellTopology topology = c.topology;
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (!wall[x][y]) continue;
                c.grid.setWalkable(x, y, false);
                c.grid.setWallHp(x, y, hp);
                topology.setWall(x, y, true);
                topology.setFixture(x, y, false);
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (!wall[x][y]) continue;
                int mask = 0;
                if (open(wall, b, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
                if (open(wall, b, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
                if (open(wall, b, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
                if (open(wall, b, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
                topology.setWallDirMask(x, y, mask);
            }
        }
        InstallationWalls.perimeter(c, wall);
        CompoundWallApertures.stamp(inside, c.grid, topology);
        posts.addAll(List.of(new int[]{b[0], b[1]}, new int[]{b[2], b[1]}, new int[]{b[0], b[3]}, new int[]{b[2], b[3]}));
        for (int[] post : posts) {
            if (wall[post[0]][post[1]]) topology.setVehicle(post[0], post[1], true);
        }
        return out;
    }

    /** Whether a wall cell's neighbour is a face to draw: outside the quarter, or open ground in it. */
    private static boolean open(boolean[][] wall, int[] b, int x, int y) {
        if (x < b[0] || x > b[2] || y < b[1] || y > b[3]) return true;
        return !wall[x][y];
    }

    /**
     * A gatehouse's checkpoint: a boom gate on the first row inside, its post on open ground beside the lane (street
     * or pavement) on the first side off the road reservation and its arm across the lane, and a bollard before each
     * jamb on the street outside wherever that is off the reservation.
     */
    private static void checkpoint(GenContext c, CordonedFacilityProgram.Gate g, boolean[][] road,
                                   boolean[][] reservation) {
        int in = g.wall() + g.inward() * CordonedFacilityProgram.GATEHOUSE_DEPTH, out = g.wall() - g.inward();
        for (int side : new int[]{-1, 1}) {
            int post = side < 0 ? g.laneLo() - 1 : g.laneHi() + 1;
            if (!freeGround(c, reservation, g.x(in, post), g.y(in, post))) continue;
            int dx = g.x(in, post - side) - g.x(in, post), dy = g.y(in, post - side) - g.y(in, post);
            StreetDressing.boomGate(c.grid, c.topology, c.doodads, g.x(in, post), g.y(in, post), dx, dy);
            break;
        }
        for (int cc : new int[]{g.laneLo() - 1, g.laneHi() + 1}) {
            int x = g.x(out, cc), y = g.y(out, cc);
            if (road[x][y] && freeGround(c, reservation, x, y)) StreetDressing.bollard(c.grid, c.topology, c.doodads, x, y);
        }
    }

    /** Whether a cell is open ground on the map, off the road reservation, with nothing standing on it. */
    private static boolean freeGround(GenContext c, boolean[][] reservation, int x, int y) {
        if (x < 0 || y < 0 || x >= c.width || y >= c.height) return false;
        return (reservation == null || !reservation[x][y]) && c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y);
    }

    /**
     * A sandbag course behind a gatehouse, facing out: across the street's width beside the lane, the first
     * row inside the gatehouse, wherever the street there is off the road reservation and no boom gate stands.
     */
    private static void sandbags(GenContext c, CordonedFacilityProgram.Gate g, boolean[][] road,
                                 boolean[][] reservation, TileRegistry registry) {
        String facing = g.alongX() ? (g.inward() > 0 ? "w" : "e") : (g.inward() > 0 ? "n" : "s");
        DoodadDef bag = registry.doodad(SANDBAG_PREFIX + facing);
        int a = g.wall() + g.inward() * CordonedFacilityProgram.GATEHOUSE_DEPTH;
        for (int cc = g.lo(); cc <= g.hi(); cc++) {
            if (cc >= g.laneLo() && cc <= g.laneHi()) continue;
            int x = g.x(a, cc), y = g.y(a, cc);
            if (!road[x][y] || (reservation != null && reservation[x][y]) || c.topology.isFixture(x, y)) continue;
            solid(c, x, y, bag);
        }
    }

    /**
     * Barriers at the inner streets' kerbs: a hedgehog and a street lamp by turns every {@link #BARRIER_RHYTHM}
     * cells along each kerb row the reservation leaves, clear of the gatehouses and the crossings, each lamp's arm
     * over the street.
     */
    private static void barriers(GenContext c, QuarterClaim.Quarter q, boolean[][] road, boolean[][] reservation,
                                 List<CordonedFacilityProgram.Gate> gates, TileRegistry registry) {
        DoodadDef hedgehog = registry.doodad(HEDGEHOG);
        if (hedgehog == null) return;
        int main = q.rows.size() - 1;
        for (int i = 0; i < q.streets.size(); i++) {
            QuarterClaim.Street s = q.streets.get(i);
            boolean alongX = (i < main) == q.horizontal;
            int first = alongX ? s.left() : s.top(), last = alongX ? s.right() : s.bottom();
            int lo = alongX ? s.top() : s.left(), hi = alongX ? s.bottom() : s.right();
            if (hi - lo + 1 <= CordonedFacilityProgram.LANE) continue;
            int clear = CordonedFacilityProgram.GATEHOUSE_DEPTH + 2;
            for (int kerb : new int[]{lo, hi}) {
                int out = kerb == lo ? 1 : -1, beat = 0;
                for (int a = first + clear; a <= last - clear; a += BARRIER_RHYTHM) {
                    int x = alongX ? a : kerb, y = alongX ? kerb : a;
                    if (!road[x][y] || (reservation != null && reservation[x][y]) || crossing(q, x, y, s)) continue;
                    if (beat++ % 2 == 0) {
                        solid(c, x, y, hedgehog);
                    } else {
                        StreetDressing.lamp(c.grid, c.topology, c.doodads, StreetDressing.LAMP_INDUSTRIAL, x, y,
                                alongX ? 0 : out, alongX ? out : 0);
                    }
                }
            }
        }
    }

    /** Whether {@code (x, y)} is within a cell of another inner street: a crossing, kept clear. */
    private static boolean crossing(QuarterClaim.Quarter q, int x, int y, QuarterClaim.Street own) {
        for (QuarterClaim.Street s : q.streets) {
            if (s == own) continue;
            if (x >= s.left() - 1 && x <= s.right() + 1 && y >= s.top() - 1 && y <= s.bottom() + 1) return true;
        }
        return false;
    }

    private static void ground(GenContext c, BlockLeaf leaf, GroundKind kind) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                c.grid.setWalkableFloor(x, y);
                c.grid.openAllEdges(x, y);
                c.topology.setGroundKind(x, y, kind);
                c.markMadeGround(x, y);
            }
        }
    }

    private static void prop(GenContext c, int x0, int y0, DoodadDef def, int rotation) {
        Doodad prop = new Doodad(x0, y0, def, rotation);
        for (int y = y0; y < y0 + prop.footprintCellsY; y++) {
            for (int x = x0; x < x0 + prop.footprintCellsX; x++) fixture(c, x, y);
        }
        c.doodads.add(prop);
    }

    private static void fixture(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.topology.setFixture(x, y, true);
    }

    private static void solid(GenContext c, int x, int y, DoodadDef def) {
        if (def == null) return;
        fixture(c, x, y);
        c.doodads.add(new Doodad(x, y, def));
    }
}
