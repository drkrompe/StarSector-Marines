package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;
import java.util.Random;

/**
 * Airless Agreus closed-circuit reclamation plant. The repair load docks in a screened isolation
 * bay at the head of a covered conduit; the north switching room governs the intake pump hall,
 * the south switching room the settling tanks and heat exchangers, and the conduit joins the two
 * circuits with service crossings. The maintenance road enters past a checkpoint, with a gravel
 * service track as the road gun's northern flank.
 */
public final class NorthWeir {
    public static final int WIDTH = 176, HEIGHT = 112;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    public static final int ISOLATION_X = 64, ISOLATION_Y = 59;
    public static final int NORTH_X = 81, NORTH_Y = 75, SOUTH_X = 81, SOUTH_Y = 42;
    public static final int BUILD_X = 108, BUILD_Y = 88;
    /** Repair-load bay between the isolation house and the crew shelter; open only to the west. */
    public static final int BAY_LEFT = 57, BAY_BOTTOM = 56, BAY_RIGHT = 69, BAY_TOP = 62;
    /** Intake pump hall, governed by the north switching room. */
    public static final int PUMPS_LEFT = 92, PUMPS_BOTTOM = 67, PUMPS_RIGHT = 122, PUMPS_TOP = 80;
    /** Settling tanks and heat exchangers, governed by the south switching room. */
    public static final int TANKS_LEFT = 92, TANKS_BOTTOM = 33, TANKS_RIGHT = 122, TANKS_TOP = 50;
    /** The covered conduit's pipe rows; gaps in them are the service crossings. */
    public static final int CONDUIT_Y = 58, CONDUIT_LEFT = 78, CONDUIT_RIGHT = 124;
    public static final int[][] CROSSINGS = {{78, 82}, {95, 99}, {111, 115}};
    /** Gravel service track north of the road: the road gun's flank. */
    public static final int FLANK_Y = 102, FLANK_LEFT = 116, FLANK_RIGHT = 152;
    static final String PUMP_MOTOR = "power-plant.turbine", SETTLING_TANK = "power-plant.coolant";
    private static final String PLATE = "colony.tread";
    public record Place(String id, String label, int x, int y) {}
    public static final List<Place> PLACES = List.of(new Place("apron", "Western service apron", LANDING_X, LANDING_Y),
            new Place("isolation", "Isolation switch / repair load", ISOLATION_X, ISOLATION_Y),
            new Place("north-switch", "North switching room", NORTH_X, NORTH_Y),
            new Place("south-switch", "South switching room", SOUTH_X, SOUTH_Y),
            new Place("pump-hall", "Intake pump hall", 101, 72),
            new Place("tank-farm", "Settling tanks", 102, 40),
            new Place("road-gun", "Maintenance road defensive position", BUILD_X, BUILD_Y),
            new Place("road-flank", "North service track", 134, FLANK_Y));
    private NorthWeir() {}
    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("North Weir sealed reclamation works", List.of(NorthWeir::grounds, NorthWeir::structures,
                NorthWeir::facilities, new FinalizeStage(),
                new OutdoorLandscapeStage(24,59,64,59,81,42,81,75,108,88,170,88,170,94,134,FLANK_Y))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, 164, 88, c.pois, c.doodads,
                new TacticalMap(HeldPlaces.fromBuildings(c.pois, poi -> Faction.DEFENDER)), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var place : PLACES) if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, place.x(), place.y()).length == 0)
            throw new IllegalStateException("North Weir destination disconnected: " + place.label());
        return map;
    }
    private static void grounds(GenContext c) {
        int asphalt = c.topology.surfaceIndex(Material.ASPHALT.block(1));
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            if (x < 46 || y >= 84 && y <= 96 || x >= 46 && x <= 125 && y >= 30 && y <= 83)
            {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.65, 18533, x, y))));
                c.markMadeGround(x, y);
            }
            // The maintenance road reads as a carriageway; the gun pad stays a concrete hardstand.
            boolean pad = x >= 100 && x <= 117 && y >= 83;
            if (x >= 46 && y >= 85 && y <= 95 && !pad) c.topology.setSurface(x, y, asphalt);
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }
    private static void structures(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 74, 35, 0);
        BuildingStamp.T_WORKSHOP.place(c, 74, 68, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 126, 48, 0);
        // Isolation switch house and crew shelter flank the repair-load bay; each is entered
        // through a pressure vestibule. With the conduit headworks closing the bay's east end,
        // the docked load is screened from both switching rooms.
        shell(c, 55, 63, 69, 71, 62, 63);
        partition(c, 56, 66, 68, 66, 62, 66);
        shell(c, 55, 47, 69, 55, 62, 55);
        partition(c, 56, 52, 68, 52, 62, 52);
        shell(c, 71, 52, 76, 66, 71, 59, 73, 66, 73, 52);
        // A pressure gallery carries the headworks' north door to the north switch's stem door,
        // so the repair watch reaches it out of the south room's sight; a vestibule seals the
        // south switch's west door.
        shell(c, 72, 66, 82, 70, 72, 68);
        shell(c, 70, 40, 74, 45, 70, 42, 70, 43);
        // Road checkpoint booths narrow the eastern approach to a legible gate.
        shell(c, 136, 78, 140, 82, 136, 80);
        shell(c, 136, 97, 140, 100, 136, 98);
    }
    private static void facilities(GenContext c) {
        var yard = new StoryServiceYard(c);
        yard.bay(BAY_LEFT, BAY_BOTTOM, BAY_RIGHT, BAY_TOP);
        yard.stock(60, 70, "industrial-switchboard", 0, 62, 67);
        yard.stock(57, 67, "industrial-control-console", 0, 62, 67);
        yard.stock(57, 48, "industrial-drum-cluster", 0, 62, 50);
        yard.stock(66, 48, "industrial-cable-reel", 0, 62, 50);
        yard.stock(73, 62, "industrial-engine-control-board", 0, 73, 58);
        // Covered conduit: two pipe rows on a plated gallery bed, broken by service crossings.
        plinth(c, CONDUIT_LEFT, CONDUIT_Y - 2, CONDUIT_RIGHT, CONDUIT_Y + 3, PLATE);
        for (int x = CONDUIT_LEFT; x <= CONDUIT_RIGHT; x++) {
            if (crossing(x)) continue;
            pipe(c, x, CONDUIT_Y); pipe(c, x, CONDUIT_Y + 1);
        }
        // North circuit: the north switch's east door feeds a manifold through three pump groups.
        plinth(c, PUMPS_LEFT, PUMPS_BOTTOM, PUMPS_RIGHT, PUMPS_TOP, PLATE);
        for (int x = 89; x <= 121; x++) if (x < 110 || x > 113) pipe(c, x, 75);
        for (int gx : new int[]{94, 104, 114}) {
            fit(c, gx, 77, PUMP_MOTOR);
            fit(c, gx, 69, PUMP_MOTOR);
            fit(c, gx + 4, 77, "doodad.industrial-pump-set");
            fit(c, gx, 73, "doodad.industrial-pump-set");
            fit(c, gx + 4, 73, "doodad.industrial-pump-set");
            fit(c, gx + 4, 79, "doodad.industrial-control-console");
        }
        for (int y = CONDUIT_Y + 2; y <= 74; y++) pipe(c, 102, y);
        // South circuit: the south switch's east door feeds settling tanks and heat exchangers.
        plinth(c, TANKS_LEFT, TANKS_BOTTOM, TANKS_RIGHT, TANKS_TOP, PLATE);
        for (int x = 89; x <= 121; x++) if (x < 110 || x > 113) pipe(c, x, 42);
        for (int tx : new int[]{92, 96, 105, 115, 119}) { fit(c, tx, 44, SETTLING_TANK); fit(c, tx, 48, SETTLING_TANK); }
        for (int tx : new int[]{93, 105, 116}) fit(c, tx, 37, "doodad.industrial-reactor-housing");
        for (int tx : new int[]{97, 108, 120}) fit(c, tx, 38, "doodad.industrial-pump-set");
        for (int y = 43; y < CONDUIT_Y; y++) pipe(c, 102, y);
        // Apron-edge stores.
        yard.cargo(51, 38); yard.cargo(48, 75);
        // Road gun: an open hardstand bay on the road, a checkpoint approach and a flank to watch.
        yard.bay(103, 84, 114, 94);
        int track = c.topology.surfaceIndex(Material.CONCRETE.block(2));
        plinth(c, 148, 97, 152, FLANK_Y + 2, track);
        plinth(c, FLANK_LEFT + 4, FLANK_Y - 1, 147, FLANK_Y + 2, track);
        plinth(c, FLANK_LEFT, 97, FLANK_LEFT + 3, FLANK_Y + 2, track);
        for (int x = FLANK_LEFT; x <= FLANK_RIGHT; x += 2) pipe(c, x, FLANK_Y + 4);
        for (int[] p : new int[][]{{128, 85}, {131, 94}, {146, 85}, {157, 94}, {162, 85}})
            fit(c, p[0], p[1], "doodad.industrial-cable-reel");
    }
    /** Equipment bed: a surface over open made ground; buildings and fixtures keep their own. */
    private static void plinth(GenContext c, int l, int b, int r, int t, Object surface) {
        int index = surface instanceof Integer i ? i : c.topology.surfaceIndex((String) surface);
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if (!c.grid.inBounds(x, y) || c.topology.hasBuildingKindHint(x, y) || c.topology.isFixture(x, y)) continue;
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            c.topology.setSurface(x, y, index);
            c.markMadeGround(x, y);
        }
    }
    private static boolean crossing(int x) {
        for (int[] span : CROSSINGS) if (x >= span[0] && x <= span[1]) return true;
        return false;
    }
    private static void pipe(GenContext c, int x, int y) { fit(c, x, y, "doodad.industrial-pipe-bundle"); }
    /** Solid equipment that yields where its footprint meets a door's clearance, another fixture or a building. */
    private static void fit(GenContext c, int x, int y, String id) {
        var def = TileRegistry.installed().doodad(id);
        if (def == null) throw new IllegalStateException("Missing North Weir asset: " + id);
        var prop = new Doodad(x, y, def, 0);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
            if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy)
                    || c.topology.hasBuildingKindHint(xx, yy)) return;
            for (int ty = yy - 2; ty <= yy + 2; ty++) for (int tx = xx - 2; tx <= xx + 2; tx++)
                if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return;
            for (LandingPad pad : c.landingPads)
                if (xx >= pad.left() - 2 && xx <= pad.right() + 2 && yy >= pad.bottom() - 2 && yy <= pad.top() + 2) return;
        }
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
            c.grid.setWalkable(xx, yy, false);
            c.grid.setSeeThrough(xx, yy, true);
            c.topology.setFixture(xx, yy, true);
        }
        c.doodads.add(prop);
    }
    /** Sealed olive-walled shell on {@code l..r, b..t}; {@code doors} are x,y pairs on its ring. Existing buildings are kept. */
    private static void shell(GenContext c, int l, int b, int r, int t, int... doors) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if (c.topology.hasBuildingKindHint(x, y)) continue;
            boolean ring = x == l || x == r || y == b || y == t;
            boolean door = false;
            for (int i = 0; i + 1 < doors.length; i += 2) door |= doors[i] == x && doors[i + 1] == y;
            build(c, x, y, ring && !door, door);
        }
        faces(c, l, b, r, t);
    }
    /** Interior wall across a shell with one doorway: the inner leaf of a pressure vestibule. */
    private static void partition(GenContext c, int x0, int y0, int x1, int y1, int doorX, int doorY) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) build(c, x, y, x != doorX || y != doorY, x == doorX && y == doorY);
        faces(c, x0, y0, x1, y1);
    }
    private static void build(GenContext c, int x, int y, boolean wall, boolean door) {
        c.grid.setWalkable(x, y, !wall);
        c.grid.setSeeThrough(x, y, !wall);
        c.grid.setDoorway(x, y, door);
        c.topology.setWall(x, y, wall);
        c.topology.setGroundKind(x, y, GroundKind.STRIPED);
        c.topology.setBuildingKindHint(x, y, BuildingKind.INDUSTRIAL);
        c.topology.setSurface(x, y, c.topology.surfaceIndex(wall ? "colony.olive-wall" : "colony.concrete"));
        c.markMadeGround(x, y);
    }
    /** Every wall in and beside the box faces whatever is not wall; a doorway faces out of its building. */
    private static void faces(GenContext c, int l, int b, int r, int t) {
        for (int y = b - 1; y <= t + 1; y++) for (int x = l - 1; x <= r + 1; x++) {
            if (!c.grid.inBounds(x, y)) continue;
            int mask = 0;
            if (c.topology.isWall(x, y)) {
                for (Direction side : Direction.CARDINALS)
                    if (!c.grid.inBounds(x + side.dx, y + side.dy) || !c.topology.isWall(x + side.dx, y + side.dy)) mask |= bit(side);
                c.topology.setWallDirMask(x, y, mask);
            } else if (c.grid.isDoorway(x, y) && x >= l && x <= r && y >= b && y <= t) {
                boolean across = wall(c, x - 1, y) && wall(c, x + 1, y);
                mask = bit(across ? Direction.N : Direction.E);
                for (Direction side : Direction.CARDINALS)
                    if (c.grid.inBounds(x + side.dx, y + side.dy) && !c.topology.hasBuildingKindHint(x + side.dx, y + side.dy)) { mask = bit(side); break; }
                c.topology.setWallDirMask(x, y, mask);
            }
        }
    }
    private static boolean wall(GenContext c, int x, int y) { return c.grid.inBounds(x, y) && c.topology.isWall(x, y); }
    private static int bit(Direction side) {
        return switch (side) {
            case N -> CellTopology.WALL_DIR_N; case E -> CellTopology.WALL_DIR_E;
            case S -> CellTopology.WALL_DIR_S; default -> CellTopology.WALL_DIR_W;
        };
    }
    public static boolean insideRoom(CellTopology topology, int x, int y, boolean north) {
        return x >= 74 && x <= 86 && y >= (north ? 68 : 35) && y <= (north ? 78 : 45)
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }
}
