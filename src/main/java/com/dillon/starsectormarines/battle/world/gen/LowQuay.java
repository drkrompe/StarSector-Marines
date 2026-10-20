package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Low Quay's bonded stores on airless Agreus: one walled compound entered by a secure western
 * freight gate. A single loading lane runs from the eastern load court through that gate to the
 * company's apron. North of it stand the gatehouse, the records archive and the bonded warehouse;
 * south of it the diverted-consignment hold and the calibration shop with its cage. The occupied
 * rooms are reached through enclosed service galleries whose pressure vestibules open on the lane
 * at one place, the crossing, which the inbound technician and the outbound carrier both pass and
 * which the Drent Haulage rearguard reaches from the north freight gate through the north gallery.
 * Vestibules are structure only: nothing here counts oxygen.
 */
public final class LowQuay {
    public static final int WIDTH = 144, HEIGHT = 112;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    /** Archive interior work point, and the calibration cage's bench point. */
    public static final int RECORDS_X = 73, RECORDS_Y = 76, KIT_X = 102, KIT_Y = 40;
    /** Carrier start in the eastern load court; it drives the lane west to the apron. */
    public static final int LOAD_X = 112, LOAD_Y = 59;
    /** Where both service galleries open on the lane: the extraction route's exposed crossing. */
    public static final int CROSS_X = 64, CROSS_Y = 59;
    /** Drent Haulage vehicles stand outside the north freight gate. */
    public static final int RESERVE_X = 73, RESERVE_Y = 106;
    /** Store guards: one inside the archive, one on the calibration shop floor outside the cage. */
    public static final int[][] GUARD_POSTS = {{RECORDS_X - 2, RECORDS_Y}, {92, 40}};
    /** The secure gate: the only opening in the compound's western wall. */
    public static final int GATE_X = 44, GATE_BOTTOM = 55, GATE_TOP = 63;

    // Interior bounds of the two work rooms (walls excluded).
    private static final int ARCHIVE_L = 67, ARCHIVE_B = 69, ARCHIVE_R = 79, ARCHIVE_T = 83;
    private static final int SHOP_L = 87, SHOP_B = 27, SHOP_R = 107, SHOP_T = 49;

    private static final char PERIMETER = 'X', WALL = '#', DOOR = '+', VOID = ' ';
    private static final char STORE = '.', ARCHIVE = 'a', GALLERY = 'g', LOCK = 'v', SHOP = ':', OFFICE = 'h';

    private LowQuay() { }

    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Low Quay bonded stores", List.of(LowQuay::grounds, LowQuay::compound, LowQuay::fittings,
                new FinalizeStage(), new OutdoorLandscapeStage(LANDING_X, LANDING_Y, RECORDS_X, RECORDS_Y,
                        KIT_X, KIT_Y, LOAD_X, LOAD_Y, CROSS_X, CROSS_Y, RESERVE_X, RESERVE_Y))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, RESERVE_X, RESERVE_Y,
                c.pois, c.doodads, new TacticalMap(HeldPlaces.fromBuildings(c.pois, poi -> Faction.DEFENDER)), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (int[] point : new int[][]{{RECORDS_X,RECORDS_Y},{KIT_X,KIT_Y},{LOAD_X,LOAD_Y},{CROSS_X,CROSS_Y},{RESERVE_X,RESERVE_Y}})
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, point[0], point[1]).length == 0)
                throw new IllegalStateException("Disconnected Low Quay objective at " + point[0] + "," + point[1]);
        return map;
    }

    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, GroundKind.STONE);
            boolean apron = x < GATE_X, yard = x > GATE_X && x < 128 && y > 14 && y < 100
                    || x == GATE_X && y >= GATE_BOTTOM && y <= GATE_TOP;
            boolean haulStand = x >= 66 && x <= 82 && y >= 100 && y <= 108;
            if (!apron && !yard && !haulStand) continue;
            boolean lane = yard && y >= GATE_BOTTOM && y <= GATE_TOP;
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            Material material = lane ? Material.ASPHALT : Material.CONCRETE;
            c.topology.setSurface(x, y, c.topology.surfaceIndex(material.block(RoadRasterizer.age(.7, 18534, x, y))));
            c.markMadeGround(x, y);
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }

    /** Lays the compound out on a character plan, then stamps it once; see the class comment for the plan. */
    private static void compound(GenContext c) {
        char[][] plan = new char[WIDTH][HEIGHT];
        for (char[] column : plan) Arrays.fill(column, VOID);
        // Compound wall: secure western freight gate on the lane, north freight gate for Drent's haulers.
        box(plan, 44, 14, 128, 100, PERIMETER, VOID);
        fill(plan, GATE_X, GATE_BOTTOM, GATE_X, GATE_TOP, VOID);
        fill(plan, 70, 100, 76, 100, VOID);

        // North gallery: vestibule on the lane at the crossing, vestibule on the north yard road.
        box(plan, 61, 64, 66, 89, WALL, GALLERY);
        vestibuleRow(plan, 61, 66, 64, 67);
        vestibuleRow(plan, 61, 66, 86, 89);
        // Gatehouse overlooking the secure gate; entered from the gallery or its own yard lock.
        box(plan, 44, 64, 61, 74, WALL, OFFICE);
        fill(plan, 61, 70, 61, 71, DOOR);
        box(plan, 50, 74, 55, 77, WALL, LOCK);
        fill(plan, 52, 74, 53, 74, DOOR); fill(plan, 52, 77, 53, 77, DOOR);
        // Records archive, entered from the gallery and opening into the bonded warehouse.
        box(plan, 66, 68, 80, 84, WALL, ARCHIVE);
        fill(plan, 66, 75, 66, 76, DOOR);
        fill(plan, 80, 79, 80, 80, DOOR);
        // Bonded warehouse with a freight lock onto the lane.
        box(plan, 80, 68, 104, 88, WALL, STORE);
        box(plan, 94, 64, 100, 68, WALL, LOCK);
        fill(plan, 96, 64, 98, 64, DOOR); fill(plan, 96, 68, 98, 68, DOOR);

        // South gallery: vestibule on the lane at the crossing, then east to the calibration shop.
        box(plan, 61, 30, 66, 54, WALL, GALLERY);
        box(plan, 61, 30, 86, 35, WALL, GALLERY);
        fill(plan, 62, 31, 65, 35, GALLERY);
        vestibuleRow(plan, 61, 66, 54, 51);
        // Diverted-consignment hold beside the gate: sealed from the gallery, its only way in is its freight lock.
        box(plan, 44, 36, 61, 50, WALL, STORE);
        box(plan, 49, 50, 55, 54, WALL, LOCK);
        fill(plan, 51, 50, 53, 50, DOOR); fill(plan, 51, 54, 53, 54, DOOR);
        // Calibration shop: gallery door west, a pressure lock to the back yard south, no lane door.
        box(plan, 86, 26, 108, 50, WALL, SHOP);
        fill(plan, 86, 32, 86, 33, DOOR);
        box(plan, 94, 22, 99, 26, WALL, LOCK);
        fill(plan, 96, 22, 97, 22, DOOR); fill(plan, 96, 26, 97, 26, DOOR);

        // Firing recesses: windows over the gate, the dock aprons and the north yard.
        for (int x : new int[]{47, 48, 52, 53, 57, 58}) plan[x][64] = 's';
        for (int x : new int[]{69, 70, 75, 76}) { plan[x][68] = 's'; plan[x][84] = 'n'; }
        for (int x : new int[]{84, 85, 89, 90, 102, 103}) plan[x][68] = 's';
        for (int x : new int[]{47, 48, 57, 58}) plan[x][50] = 'n';
        for (int x : new int[]{90, 91, 102, 103}) plan[x][50] = 'n';
        stamp(c, plan);
    }

    /** Two-door pressure vestibule across a vertical gallery: doors in rows {@code outer} and {@code inner}. */
    private static void vestibuleRow(char[][] plan, int left, int right, int outer, int inner) {
        fill(plan, left + 1, outer, right - 1, outer, WALL);
        fill(plan, left + 1, inner, right - 1, inner, WALL);
        int bottom = Math.min(outer, inner), top = Math.max(outer, inner);
        fill(plan, left + 1, bottom + 1, right - 1, top - 1, LOCK);
        int mid = (left + right) / 2;
        fill(plan, mid, outer, mid + 1, outer, DOOR);
        fill(plan, mid, inner, mid + 1, inner, DOOR);
    }

    private static void box(char[][] plan, int l, int b, int r, int t, char wall, char floor) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++)
            plan[x][y] = x == l || x == r || y == b || y == t ? wall : floor;
    }

    private static void fill(char[][] plan, int l, int b, int r, int t, char value) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) plan[x][y] = value;
    }

    private static boolean wall(char[][] plan, int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && (plan[x][y] == WALL || plan[x][y] == PERIMETER);
    }

    private static char at(char[][] plan, int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT ? plan[x][y] : VOID;
    }

    private static void stamp(GenContext c, char[][] plan) {
        var t = c.topology;
        int perimeter = t.surfaceIndex("installation.dark-concrete-wall"), shell = t.surfaceIndex("installation.light-concrete-wall");
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            char cell = plan[x][y];
            if (cell == VOID) continue;
            boolean solid = cell == WALL || cell == PERIMETER;
            c.grid.setWalkable(x, y, !solid);
            c.grid.setSeeThrough(x, y, !solid);
            c.grid.setDoorway(x, y, cell == DOOR);
            t.setWall(x, y, solid);
            c.markMadeGround(x, y);
            if (cell == PERIMETER) {
                t.setSurface(x, y, perimeter);
                t.setWallGroundSurface(x, y, "installation.concrete-floor");
            } else {
                t.setGroundKind(x, y, GroundKind.INDOOR);
                t.setBuildingKindHint(x, y, cell == ARCHIVE || cell == OFFICE ? BuildingKind.COMMERCIAL : BuildingKind.INDUSTRIAL);
                if (solid) { t.setSurface(x, y, shell); t.setWallGroundSurface(x, y, "installation.concrete-floor"); }
                else t.setSurface(x, y, t.surfaceIndex(floor(plan, x, y)));
            }
            int mask = 0;
            if (solid) {
                for (Direction side : Direction.CARDINALS)
                    if (!wall(plan, x + side.dx, y + side.dy)) mask |= wallBit(side);
            } else if (cell == DOOR) {
                int across = (wall(plan, x - 1, y) ? 1 : 0) + (wall(plan, x + 1, y) ? 1 : 0);
                int upright = (wall(plan, x, y - 1) ? 1 : 0) + (wall(plan, x, y + 1) ? 1 : 0);
                mask = wallBit(upright > across ? Direction.E : Direction.N);
                for (Direction side : Direction.CARDINALS)
                    if (at(plan, x + side.dx, y + side.dy) == VOID) { mask = wallBit(side); break; }
            }
            t.setWallDirMask(x, y, mask);
        }
        // Windows: a walkable recess in the wall line, its outside edge closed by glazing.
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            Direction out = switch (plan[x][y]) {
                case 'n' -> Direction.N; case 'e' -> Direction.E; case 's' -> Direction.S; case 'w' -> Direction.W;
                default -> null;
            };
            if (out != null) c.grid.placeEdgeBarrier(x, y, out, SharedEdgeBarrier.Kind.WINDOW);
        }
        building(c, 45, 65, 60, 73, 55, 70, 52, 78);
        building(c, ARCHIVE_L, ARCHIVE_B, ARCHIVE_R, ARCHIVE_T, RECORDS_X, RECORDS_Y, 63, 62);
        building(c, 81, 69, 103, 87, 97, 71, 97, 63);
        building(c, 45, 37, 60, 49, 52, 46, 52, 55);
        building(c, SHOP_L, SHOP_B, SHOP_R, SHOP_T, 92, 32, 96, 21);
    }

    private static void building(GenContext c, int l, int b, int r, int t, int ix, int iy, int ex, int ey) {
        c.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, l - 1, b - 1, r + 1, t + 1, ex, ey, ix, iy));
    }

    /** Floor surface by plan character; a window recess or threshold takes the room it belongs to. */
    private static String floor(char[][] plan, int x, int y) {
        char cell = plan[x][y];
        if (cell == DOOR || "nesw".indexOf(cell) >= 0) {
            for (Direction side : Direction.CARDINALS) {
                char next = at(plan, x + side.dx, y + side.dy);
                if (next != DOOR && next != WALL && next != PERIMETER && next != VOID && "nesw".indexOf(next) < 0) { cell = next; break; }
            }
        }
        return switch (cell) {
            case ARCHIVE -> "installation.command-floor";
            case OFFICE -> "installation.accommodation-floor";
            case GALLERY -> "colony.tread";
            case LOCK -> "colony.ochre";
            case SHOP -> "colony.concrete";
            default -> "installation.concrete-floor";
        };
    }

    private static int wallBit(Direction side) {
        return switch (side) {
            case N -> CellTopology.WALL_DIR_N;
            case E -> CellTopology.WALL_DIR_E;
            case S -> CellTopology.WALL_DIR_S;
            default -> CellTopology.WALL_DIR_W;
        };
    }

    /** Room contents, yard cargo and the identifiable clinic consignments Drent is stripping for transfer. */
    private static void fittings(GenContext c) {
        // Archive: records shelving and a server rack; the work point and both doors stay clear.
        for (int x : new int[]{68, 69, 70, 71, 75, 76, 77, 78}) { fixture(c, x, 81, "colony-furnishings.shelf", 0); fixture(c, x, 71, "colony-furnishings.shelf", 0); }
        fixture(c, 79, 83, "doodad.office-server-rack", 0);
        fixture(c, 79, 82, "doodad.office-server-rack", 0);
        fixture(c, 72, 78, "colony-furnishings.desk", 0);
        // Gatehouse: a duty desk and lockers behind the gate windows.
        fixture(c, 50, 67, "colony-furnishings.desk", 0);
        fixture(c, 55, 67, "colony-furnishings.desk", 0);
        for (int x = 45; x <= 47; x++) fixture(c, x, 73, "colony-furnishings.locker", 0);
        // Bonded warehouse: three rack columns make two aisles and a freight aisle before the lock.
        for (int x : new int[]{82, 87, 92}) for (int y : new int[]{70, 79})
            fixture(c, x, y, "main-stores.rack", 0, "main-stores.cartons", "main-stores.cartons@0,2", "main-stores.cartons@0,4", "main-stores.frame");
        for (int y : new int[]{73, 76, 80, 83}) fixture(c, 101, y, "main-stores.pallet", 0, "main-stores.cases");
        // Diverted-consignment hold: clinic-marked insulated totes on pallets, a drug locker, Drent's tally desk.
        for (int[] p : new int[][]{{45,38},{45,41},{48,38},{48,41}}) fixture(c, p[0], p[1], "main-stores.pallet", 0, "loading-bay.insulated-tote");
        fixture(c, 55, 37, "doodad.medical-drug-locker", 0);
        fixture(c, 56, 47, "main-stores.tally", 0);
        fixture(c, 46, 47, "doodad.medical-supply-trolley", 0);
        // Calibration shop: machine tools outside the cage, the calibration bench inside it.
        fixture(c, 88, 46, "machine-shop.lathe", 0);
        fixture(c, 88, 42, "machine-shop.mill", 0);
        fixture(c, 88, 28, "machine-shop.assembly-bench", 0);
        fixture(c, 101, 29, "machine-shop.assembly-bench", 0);
        fixture(c, 99, 47, "machine-shop.mill", 0);
        cage(c);
        fixture(c, 99, 43, "machine-shop.inspection-bench", 0);
        fixture(c, 103, 37, "machine-shop.inspection-bench", 0);
        // Yard: consignments staged on the south dock apron and loaded beside Drent's truck in the transfer yard.
        fixture(c, 57, 52, "main-stores.pallet", 0, "loading-bay.insulated-tote");
        fixture(c, 59, 52, "doodad.medical-supply-trolley", 0);
        fixture(c, 45, 52, "main-stores.pallet", 0, "loading-bay.insulated-tote");
        fixture(c, 112, 80, "doodad.parked-cargo-truck", 0);
        fixture(c, 116, 80, "main-stores.pallet", 0, "loading-bay.insulated-tote");
        fixture(c, 116, 77, "main-stores.pallet", 0, "loading-bay.insulated-tote");
        fixture(c, 116, 83, "doodad.medical-drug-locker", 0);
        fixture(c, 108, 92, "doodad.industrial-crate-stack", 0);
        fixture(c, 109, 92, "doodad.industrial-crate-stack", 0);
        fixture(c, 120, 40, "doodad.industrial-crate-stack", 0);
        fixture(c, 120, 41, "doodad.industrial-crate-stack", 0);
        fixture(c, 79, 103, "doodad.parked-box-van", 0);
        var yard = new StoryServiceYard(c);
        yard.bay(104, 54, 120, 64);
    }

    /** The calibration cage: mesh panels on three sides against the shop's east wall, door facing the gallery. */
    private static void cage(GenContext c) {
        for (int y : new int[]{36, 42}) fixture(c, 96, y, "doodad.stores-cage-front", 0);
        for (int x : new int[]{96, 99, 102, 105}) { fixture(c, x, 35, "doodad.stores-cage-front", 1); fixture(c, x, 45, "doodad.stores-cage-front", 1); }
    }

    /**
     * One placed fixture with optional non-blocking overlays ({@code id} or {@code id@dx,dy}).
     * Refuses rather than skips: every item here is authored, so a collision is a plan error.
     */
    private static void fixture(GenContext c, int x, int y, String id, int turns, String... overlays) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        if (def == null) throw new IllegalStateException("Missing Low Quay asset: " + id);
        Doodad prop = new Doodad(x, y, def, turns);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
            if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy))
                throw new IllegalStateException("Low Quay fixture " + id + " overlaps at " + xx + "," + yy);
            for (int ty = yy - 1; ty <= yy + 1; ty++) for (int tx = xx - 1; tx <= xx + 1; tx++)
                if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty))
                    throw new IllegalStateException("Low Quay fixture " + id + " obstructs a threshold at " + xx + "," + yy);
        }
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
            c.grid.setWalkable(xx, yy, false);
            c.grid.setSeeThrough(xx, yy, true);
            c.topology.setFixture(xx, yy, true);
        }
        c.doodads.add(prop);
        for (String overlay : overlays) {
            String[] parts = overlay.split("@");
            int dx = 0, dy = 0;
            if (parts.length == 2) { String[] offset = parts[1].split(","); dx = Integer.parseInt(offset[0]); dy = Integer.parseInt(offset[1]); }
            DoodadDef layer = TileRegistry.installed().doodad(parts[0]);
            if (layer == null) throw new IllegalStateException("Missing Low Quay asset: " + parts[0]);
            c.doodads.add(new Doodad(x + dx, y + dy, layer, turns));
        }
    }

    /** True inside the archive ({@code records}) or the calibration shop with its cage, never in a wall. */
    public static boolean insideRoom(CellTopology topology, int x, int y, boolean records) {
        return x >= (records ? ARCHIVE_L : SHOP_L) && x <= (records ? ARCHIVE_R : SHOP_R)
                && y >= (records ? ARCHIVE_B : SHOP_B) && y <= (records ? ARCHIVE_T : SHOP_T)
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }
}
