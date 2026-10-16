package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;
import java.util.Random;

/**
 * Black Sluice, the outer Silt Basin's fuel and dispatch apron on airless Agreus. One causeway
 * transfer road runs east between walled settling beds, so the road itself is the exposed
 * approach; sealed service galleries north and south of the beds are the infantry flanks.
 * Customs stands at the causeway's landward end, the sheltered fuel-control house at its
 * seaward end beside the marked fuel tanks, and dispatch on the pier beyond. A separate
 * service road runs east to a vehicle pressure lock under the dock wall: Greyhook's declared
 * way out.
 *
 * <p>Beyond the dispatch apron is the walled docking compound where Greyhook's ships sit at true
 * landed scale ({@code landed-ships-true-scale.md}): a sunken cradle for the Venture with a Hound
 * berth either side of it, gantry arms reaching the hulls, fuel and cargo lines along the aisles,
 * sealed service galleries along the north and south walls, and pressure vestibules through the
 * west wall. The wells are open to sight and closed to feet.
 */
public final class BlackSluice {
    public static final int WIDTH = 268, HEIGHT = 128;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    /** The marked tender-build rear position south of customs. */
    public static final int BUILD_X = 54, BUILD_Y = 48;
    /** One visible support gun on the causeway shoulder in front of fuel control. */
    public static final int GUN_X = 121, GUN_Y = 60;
    public static final String TANK_ID = "doodad.industrial-fluid-tank";
    /** The marked preservation fuel infrastructure: the four-tank farm behind fuel control. */
    public static final List<int[]> TANKS = List.of(new int[]{145, 84}, new int[]{147, 84}, new int[]{145, 86}, new int[]{147, 86});
    /** Service-road gate: the mouth of the vehicle pressure lock; physical departures leave the battle here. */
    public static final int EXIT_X = 196, EXIT_Y = 23;
    /** Named ingress points for the finite reserves. */
    public static final int SERVICE_INGRESS_X = 186, SERVICE_INGRESS_Y = 23;
    public static final int PIER_INGRESS_X = 188, PIER_INGRESS_Y = 52;
    /** Inner ends of the two service galleries, where flanking infantry come out. */
    public static final int NORTH_GALLERY_EXIT_X = 122, NORTH_GALLERY_EXIT_Y = 84;
    public static final int SOUTH_GALLERY_EXIT_X = 112, SOUTH_GALLERY_EXIT_Y = 35;
    public record Site(String id, String name, int x, int y, int left, int bottom, int right, int top) { }
    public static final List<Site> SITES = List.of(
            new Site("customs", "Causeway customs block", 58, 69, 52, 64, 64, 74),
            new Site("fuel-control", "Fuel-control house", 123, 70, 118, 66, 128, 75),
            new Site("dispatch", "Dispatch pier", 166, 56, 160, 48, 179, 64));
    public record Place(String id, String label, int x, int y) { }
    public static final List<Place> PLACES = List.of(
            new Place("apron", "Western landing apron", LANDING_X, LANDING_Y),
            new Place("rear", "Rear position / tender build bay", BUILD_X, BUILD_Y),
            new Place("north-gallery", "North service gallery", NORTH_GALLERY_EXIT_X, NORTH_GALLERY_EXIT_Y),
            new Place("south-gallery", "South service gallery", SOUTH_GALLERY_EXIT_X, SOUTH_GALLERY_EXIT_Y),
            new Place("gun", "Support-gun position", GUN_X - 2, GUN_Y),
            new Place("service-road", "Service road gate", SERVICE_INGRESS_X, SERVICE_INGRESS_Y),
            new Place("pier", "Pier berth", PIER_INGRESS_X, PIER_INGRESS_Y));
    /** The walled docking compound east of the dispatch apron, walls included. */
    public static final int DOCK_LEFT = 202, DOCK_BOTTOM = 2, DOCK_RIGHT = 265, DOCK_TOP = 125;
    /**
     * A landed ship's berth: a sunken, see-through, unwalkable well ({@code left..right, bottom..top}) and where the
     * hull's drawn centre sits in it, nose east toward its climb-out ({@code facing}, in warship degrees).
     */
    public record Berth(String id, String label, float x, float y, float facing, int left, int bottom, int right, int top) {
        public boolean contains(int cx, int cy) { return cx >= left && cx <= right && cy >= bottom && cy <= top; }
    }
    /** The Venture's cradle: fifty by fifty-one cells of hull at true scale, with a cell or two of clearance. */
    public static final Berth VENTURE_CRADLE = new Berth("venture-cradle", "Venture cradle", 236.5f, 64.5f, 270f, 210, 37, 262, 91);
    /** The two Hound berths flanking the cradle, each nineteen by sixteen cells of hull. */
    public static final List<Berth> HOUND_BERTHS = List.of(
            new Berth("hound-north", "North Hound berth", 237.5f, 108f, 270f, 226, 99, 248, 116),
            new Berth("hound-south", "South Hound berth", 237.5f, 21f, 270f, 226, 12, 248, 29));
    /** Gantry arms: walkable boarding catwalks reaching from the aisles over each well to its hull. */
    private static final int[][] GANTRY_ARMS = {
            {210, 61, 212, 67}, {231, 88, 234, 91}, {240, 88, 243, 91}, {231, 37, 234, 40}, {240, 37, 243, 40},
            {226, 106, 228, 110}, {226, 19, 228, 23}};
    private BlackSluice() { }

    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Black Sluice fuel and dispatch apron", List.of(BlackSluice::grounds, BlackSluice::basin,
                BlackSluice::dock, BlackSluice::facilities, new FinalizeStage(),
                new OutdoorLandscapeStage(LANDING_X, LANDING_Y, BUILD_X, BUILD_Y, 58, 69, 123, 70, 166, 56,
                        SERVICE_INGRESS_X, SERVICE_INGRESS_Y, PIER_INGRESS_X, PIER_INGRESS_Y))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, PIER_INGRESS_X, PIER_INGRESS_Y,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var site : SITES)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, site.x(), site.y()).length == 0)
                throw new IllegalStateException("Disconnected Black Sluice objective: " + site.name());
        for (var place : PLACES)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, place.x(), place.y()).length == 0)
                throw new IllegalStateException("Disconnected Black Sluice place: " + place.label());
        if (GridPathfinder.findPath(map.grid, SITES.get(1).x(), SITES.get(1).y(), EXIT_X, EXIT_Y).length == 0)
            throw new IllegalStateException("Black Sluice service road does not reach its gate");
        // Every berth is reached on foot: its gantry arm's root is on the compound's own floor.
        for (int[] arm : GANTRY_ARMS)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, arm[0], arm[1]).length == 0)
                throw new IllegalStateException("Black Sluice gantry arm cut off at " + arm[0] + "," + arm[1]);
        return map;
    }

    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            boolean apron = x < 44;
            boolean causeway = x >= 44 && x < 160 && y >= 56 && y <= 63;
            boolean pier = x >= 158 && x < DOCK_LEFT && y >= 40 && y <= 72;
            boolean serviceRoad = x >= 120 && x <= 196 && y >= 20 && y <= 26 || x >= 131 && x <= 136 && y >= 20 && y <= 63;
            boolean dock = x > DOCK_LEFT && x < DOCK_RIGHT && y > DOCK_BOTTOM && y < DOCK_TOP;
            boolean rear = x >= 44 && x <= 64 && y >= 42 && y <= 55;
            if (apron || causeway || pier || serviceRoad || rear || dock) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.75, 18535, x, y))));
                c.markMadeGround(x, y);
            }
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }

    /**
     * Walled settling beds flank the causeway so it is a corridor with no cover of its own; the
     * sealed galleries outside them are the covered alternative. Gallery ends are pressure
     * vestibules: a doorway in each end wall.
     */
    private static void basin(GenContext c) {
        bed(c, 68, 65, 106, 80);
        bed(c, 68, 39, 106, 54);
        bed(c, 68, 88, 106, HEIGHT - 1);
        bed(c, 68, 0, 106, 31);
        // The north gallery runs on past the beds to come out behind fuel control, out of the gun's sight.
        hall(c, 64, 81, 120, 87, new int[][]{{64, 83}, {64, 84}, {64, 85}, {120, 83}, {120, 84}, {120, 85}});
        hall(c, 64, 32, 110, 38, new int[][]{{64, 34}, {64, 35}, {64, 36}, {110, 34}, {110, 35}, {110, 36}});
    }

    /**
     * The docking compound: its walls, the vehicle lock the service road ends in, pressure vestibules through the
     * west wall, sealed galleries along the north and south walls, the Venture cradle and Hound berths with their
     * gantries, and the fuel and cargo lines that serve them.
     */
    private static void dock(GenContext c) {
        int masonry = c.topology.surfaceIndex("colony.masonry-wall");
        for (int y = DOCK_BOTTOM; y <= DOCK_TOP; y++) for (int x = DOCK_LEFT; x <= DOCK_RIGHT; x++)
            if (x == DOCK_LEFT || x == DOCK_RIGHT || y == DOCK_BOTTOM || y == DOCK_TOP) solid(c, x, y, masonry);
        // The service road ends at a vehicle pressure lock under the compound: its frame and its closed inner door.
        for (int x = 190; x < DOCK_LEFT; x++) { solid(c, x, 17, masonry); solid(c, x, 29, masonry); }
        for (int y = 18; y <= 28; y++) for (int x = 197; x < DOCK_LEFT; x++) solid(c, x, y, masonry);
        faceWalls(c, 189, 16, DOCK_LEFT, 30);
        faceWalls(c, DOCK_LEFT, DOCK_BOTTOM, DOCK_RIGHT, DOCK_TOP);
        // Sealed galleries along the north and south walls: the covered way between the berths.
        hall(c, DOCK_LEFT, 119, DOCK_RIGHT, DOCK_TOP, new int[][]{{DOCK_LEFT, 121}, {DOCK_LEFT, 122}, {DOCK_LEFT, 123},
                {214, 119}, {215, 119}, {216, 119}, {252, 119}, {253, 119}, {254, 119}});
        hall(c, DOCK_LEFT, DOCK_BOTTOM, DOCK_RIGHT, 8, new int[][]{{DOCK_LEFT, 4}, {DOCK_LEFT, 5}, {DOCK_LEFT, 6},
                {214, 8}, {215, 8}, {216, 8}, {252, 8}, {253, 8}, {254, 8}});
        // Pressure vestibules through the west wall: the main lock off the dispatch apron and two flank locks.
        hall(c, 198, 50, 206, 62, new int[][]{{198, 55}, {198, 56}, {198, 57}, {206, 55}, {206, 56}, {206, 57}});
        hall(c, 198, 98, 206, 106, new int[][]{{198, 101}, {198, 102}, {198, 103}, {206, 101}, {206, 102}, {206, 103}});
        hall(c, 198, 33, 206, 41, new int[][]{{198, 36}, {198, 37}, {198, 38}, {206, 36}, {206, 37}, {206, 38}});
        well(c, VENTURE_CRADLE);
        for (Berth berth : HOUND_BERTHS) well(c, berth);
        int deck = c.topology.surfaceIndex("colony.concrete");
        for (int[] arm : GANTRY_ARMS)
            for (int y = arm[1]; y <= arm[3]; y++) for (int x = arm[0]; x <= arm[2]; x++) {
                c.grid.setWalkable(x, y, true);
                c.grid.setSeeThrough(x, y, true);
                c.topology.setGroundKind(x, y, GroundKind.STRIPED);
                c.topology.setSurface(x, y, deck);
            }
        // Gantry tower bases on the rims beside each arm's root: the only solid cover on the open aisles.
        int[][] towers = {{207, 58}, {207, 69}, {228, 94}, {245, 94}, {228, 33}, {245, 33}, {222, 103}, {222, 112},
                {222, 16}, {222, 25}};
        for (int[] t : towers) {
            for (int y = t[1]; y < t[1] + 2; y++) for (int x = t[0]; x < t[0] + 2; x++) solid(c, x, y, masonry);
            faceWalls(c, t[0] - 1, t[1] - 1, t[0] + 2, t[1] + 2);
        }
        var yard = new StoryServiceYard(c);
        // Fuel lines: pipe runs along the aisles from the pump sets by the main lock to every berth.
        yard.prop(207, 47, "industrial-pump-set", 0);
        yard.prop(207, 75, "industrial-pump-set", 0);
        for (int x = 212; x <= 226; x++) { yard.prop(x, 34, "industrial-pipe-bundle", 0); yard.prop(x, 95, "industrial-pipe-bundle", 0); }
        for (int x = 248; x <= 260; x++) { yard.prop(x, 34, "industrial-pipe-bundle", 0); yard.prop(x, 95, "industrial-pipe-bundle", 0); }
        yard.prop(250, 22, "industrial-pump-set", 0);
        yard.prop(250, 107, "industrial-pump-set", 0);
        // Cargo lines: stacks, pallets and loaders on the aprons beside each berth's stern.
        yard.cargo(209, 112); yard.cargo(209, 15); yard.cargo(203, 80); yard.cargo(203, 44);
        yard.prop(216, 104, "stores-cargo-loader", 0);
        yard.prop(216, 23, "stores-cargo-loader", 0);
        yard.prop(203, 92, "parked-cargo-truck", 0);
        yard.prop(254, 114, "industrial-drum-cluster", 0);
        yard.prop(254, 14, "industrial-drum-cluster", 0);
        yard.prop(208, 88, "industrial-cable-reel", 0);
        yard.prop(208, 42, "industrial-cable-reel", 0);
    }

    /** A berth well: hazard-striped walkable rim around a sunken, unwalkable, see-through cradle floor. */
    private static void well(GenContext c, Berth berth) {
        int vent = c.topology.surfaceIndex("road.vent"), striped = c.topology.surfaceIndex("road.striped");
        for (int y = berth.bottom() - 1; y <= berth.top() + 1; y++) for (int x = berth.left() - 1; x <= berth.right() + 1; x++) {
            boolean floor = berth.contains(x, y);
            if (!floor && (c.topology.isWall(x, y) || !c.grid.isWalkable(x, y))) continue;
            c.grid.setWalkable(x, y, !floor);
            c.grid.setSeeThrough(x, y, true);
            c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            c.topology.setSurface(x, y, floor ? vent : striped);
            c.markMadeGround(x, y);
        }
    }

    private static void solid(GenContext c, int x, int y, int surface) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, false);
        c.grid.setDoorway(x, y, false);
        c.topology.setWall(x, y, true);
        c.topology.setGroundKind(x, y, GroundKind.STONE);
        c.topology.setSurface(x, y, surface);
        c.markMadeGround(x, y);
    }

    private static void facilities(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 52, 64, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 118, 66, 0);
        BuildingStamp.FREIGHT_L.place(c, 160, 48, 0);
        var yard = new StoryServiceYard(c);
        yard.bay(48, 44, 60, 53);
        yard.bay(GUN_X - 2, GUN_Y - 2, GUN_X + 2, GUN_Y + 2);
        yard.gravel(141, 79, 156, 90);
        for (int[] tank : TANKS) {
            var prop = new Doodad(tank[0], tank[1], TileRegistry.installed().doodad(TANK_ID));
            for (int y = tank[1]; y < tank[1] + prop.footprintCellsY; y++)
                for (int x = tank[0]; x < tank[0] + prop.footprintCellsX; x++) {
                    if (!c.grid.isWalkable(x, y) || c.topology.isFixture(x, y))
                        throw new IllegalStateException("Black Sluice fuel tank overlaps circulation");
                    c.grid.setWalkable(x, y, false); c.grid.setSeeThrough(x, y, true); c.topology.setFixture(x, y, true);
                }
            c.doodads.add(prop);
        }
        yard.prop(146, 84, "industrial-pipe-bundle", 0);
        yard.prop(146, 86, "industrial-pipe-bundle", 0);
        yard.prop(149, 85, "industrial-pump-set", 0);
        yard.bay(143, 82, 150, 88);
        yard.prop(152, 81, "parked-tanker-truck", 0);
        yard.prop(152, 87, "parked-tanker-truck", 0);
        yard.perimeter(new int[]{141, 84}, 140, 78, 157, 78, 157, 91, 140, 91);
        yard.cargo(44, 78); yard.cargo(150, 66); yard.cargo(182, 66);
        yard.bay(182, 44, 196, 60);
    }

    /** A walled settling bed: perimeter wall around inaccessible open silt. */
    private static void bed(GenContext c, int l, int b, int r, int t) {
        int wall = c.topology.surfaceIndex("colony.masonry-wall"), silt = c.topology.surfaceIndex("floors.dirt");
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            boolean edge = x == l || x == r || y == b || y == t;
            // The silt inside is ground nobody can reach: the sealed wall is what keeps people out.
            c.grid.setWalkable(x, y, !edge);
            c.grid.setSeeThrough(x, y, !edge);
            c.topology.setWall(x, y, edge);
            c.topology.setGroundKind(x, y, edge ? GroundKind.STONE : GroundKind.DIRT);
            c.topology.setSurface(x, y, edge ? wall : silt);
            c.markMadeGround(x, y);
        }
        faceWalls(c, l, b, r, t);
    }

    /** A sealed service gallery: walled corridor with doorway vestibules at the named cells. */
    private static void hall(GenContext c, int l, int b, int r, int t, int[][] doors) {
        int wall = c.topology.surfaceIndex("colony.olive-wall"), floor = c.topology.surfaceIndex("colony.tread");
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            boolean edge = x == l || x == r || y == b || y == t;
            boolean door = false;
            for (int[] d : doors) door |= d[0] == x && d[1] == y;
            boolean solid = edge && !door;
            c.grid.setWalkable(x, y, !solid);
            c.grid.setSeeThrough(x, y, !solid);
            c.grid.setDoorway(x, y, door);
            c.topology.setWall(x, y, solid);
            c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            c.topology.setBuildingKindHint(x, y, BuildingKind.INDUSTRIAL);
            c.topology.setSurface(x, y, solid ? wall : floor);
            c.markMadeGround(x, y);
            if (door) c.topology.setWallDirMask(x, y, x == l ? CellTopology.WALL_DIR_W : x == r ? CellTopology.WALL_DIR_E
                    : y == b ? CellTopology.WALL_DIR_S : CellTopology.WALL_DIR_N);
        }
        faceWalls(c, l, b, r, t);
    }

    private static void faceWalls(GenContext c, int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if (!c.topology.isWall(x, y)) continue;
            int mask = 0;
            if (!wallAt(c, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
            if (!wallAt(c, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
            if (!wallAt(c, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
            if (!wallAt(c, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
            c.topology.setWallDirMask(x, y, mask);
        }
    }

    private static boolean wallAt(GenContext c, int x, int y) {
        return c.grid.inBounds(x, y) && c.topology.isWall(x, y);
    }

    /** Interior of one named site; walls do not count as being inside. */
    public static boolean inside(CellTopology topology, int x, int y, int index) {
        var site = SITES.get(index);
        return x >= site.left() && x <= site.right() && y >= site.bottom() && y <= site.top()
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }

    /** The declared service-road departure gate, reached by ordinary infantry travel. */
    public static boolean atExit(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= 193 && x < 197 && y >= 18 && y <= 28;
    }
}
