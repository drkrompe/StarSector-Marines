package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Authored small territorial profile. Decoration never adds capture requirements. */
public final class ThunderRunCompound {
    public static final int WIDTH = 320, HEIGHT = 144;
    public record Cell(int x, int y) {}
    public static final List<Cell> SUPPORT_POSTS = List.of(new Cell(121, 64), new Cell(173, 87));
    /** Crew stands behind each gun, away from the company's roadside screens. */
    public static final List<Cell> MILITIA_POSTS = List.of(new Cell(119, 64), new Cell(175, 87));
    public static final List<List<Cell>> COMPANY_POSTS = List.of(
            List.of(new Cell(33, 64), new Cell(33, 86), new Cell(38, 64), new Cell(38, 86)),
            List.of(new Cell(133, 65), new Cell(122, 85), new Cell(145, 65), new Cell(114, 85)),
            List.of(new Cell(153, 65), new Cell(161, 88), new Cell(194, 65), new Cell(201, 85)),
            List.of(new Cell(263, 65), new Cell(263, 85), new Cell(285, 66), new Cell(285, 84)));

    public static Cell companyPost(int site, int detachment) {
        var posts = COMPANY_POSTS.get(site);
        return posts.get(Math.floorMod(detachment, posts.size()));
    }
    public static Cell militiaPost(int site, int detachment) {
        if (site == 0) return new Cell(27, detachment % 2 == 0 ? 64 : 86);
        if (site == 1 && detachment % 2 != 0) return new Cell(97, 65);
        if (site <= 2) return MILITIA_POSTS.get(site - 1);
        return new Cell(251, detachment % 2 == 0 ? 65 : 85);
    }
    public static final List<Cell> EXIT_RIFLE_POSTS = List.of(new Cell(290, 60), new Cell(290, 90));
    public static final List<Cell> ANTI_AIR_POSTS = List.of(new Cell(115, 63), new Cell(145, 64));
    public static final List<Cell> EXIT_GUNS = List.of(new Cell(304, 65), new Cell(304, 85));
    public static final List<String> SITE_NAMES = List.of("Convoy staging", "Dispatch Post", "East Crossing", "Outbound Gate");
    public record Layout(MapResult map, List<TacticalNode> sites) {
        public Layout { sites = List.copyOf(sites); }
    }
    private ThunderRunCompound() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Thunder Run", List.of(ThunderRunCompound::grounds,
                ThunderRunCompound::buildings, ThunderRunCompound::perimeter,
                ThunderRunCompound::fortifications, ThunderRunCompound::cargo,
                ThunderRunCompound::surfaces, ThunderRunCompound::terrain, new FinalizeStage(), new OutdoorLandscapeStage(21, 75, 270, 64))).run(c);
        for (Cell gun : ANTI_AIR_POSTS) {
            c.topology.setGroundKind(gun.x, gun.y, GroundKind.STRIPED);
            c.grid.setWalkable(gun.x, gun.y, false);
            c.grid.setSeeThrough(gun.x, gun.y, true);
        }
        // Gun pits flank the exit; neither narrows the reserved fifteen-cell road.
        for (Cell gun : EXIT_GUNS) {
            c.topology.setGroundKind(gun.x, gun.y, GroundKind.STRIPED);
            c.grid.setWalkable(gun.x, gun.y, false);
        }
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 75,
                10, 14, 34, 130, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, i == 2 ? 6 : 4));
        }
        MapResult map = new MapResult(c.grid, c.topology, 21, 75, 270, 64,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS),
                List.of(), roads(), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Thunder Run berth obstructed");
            requireRoute(map, pad.centerX, pad.centerY);
        }
        for (TacticalNode site : sites) requireRoute(map, site.anchorX, site.anchorY);
        for (PointOfInterest p : c.pois) requireRoute(map, p.interiorAnchorX, p.interiorAnchorY);
        requireRoute(map, 275, 2);
        requireRoute(map, 275, 141);
        for (Cell gun : EXIT_GUNS) requireRoute(map, gun.x + 2, gun.y);
        for (Cell gun : ANTI_AIR_POSTS) requireRoute(map, gun.x - 1, gun.y);
        for (Cell post : SUPPORT_POSTS) requireRoute(map, post.x, post.y);
        for (var posts : COMPANY_POSTS) for (Cell post : posts) requireRoute(map, post.x, post.y);
        for (int site = 0; site < 4; site++) for (int section = 0; section < 2; section++) {
            Cell post = militiaPost(site, section);
            requireRoute(map, post.x, post.y);
        }
        for (Cell post : EXIT_RIFLE_POSTS) requireRoute(map, post.x, post.y);
        for (int[] gate : new int[][]{{284,51},{316,51},{300,64},{284,98},{316,98},{300,86}})
            requireRoute(map, gate[0], gate[1]);
        for (int x = 0; x < WIDTH; x++) for (int y = 68; y <= 82; y++)
            if (!map.grid.isWalkable(x, y)) throw new IllegalStateException("Freight corridor obstructed");
        return new Layout(map, sites);
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, 21, 75, x, y)))
            throw new IllegalStateException("Thunder Run route disconnected at " + x + "," + y);
    }
    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case FROZEN -> GroundKind.SNOW;
            case ARID -> GroundKind.SAND;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, WIDTH - 1, HEIGHT - 1, wild);
        // Reserve the fifteen-cell carriageway before buildings or dressing.
        pave(c, 0, 65, WIDTH - 1, 85, GroundKind.SIDEWALK);
        freightApron(c);
        pave(c, 57, 35, 111, 64, GroundKind.COURTYARD);
        pave(c, 143, 37, 195, 112, GroundKind.COURTYARD);
        pave(c, 235, 30, 316, 115, GroundKind.COURTYARD);
        var roads = RoadCatalog.thunderRun();
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
    }
    /** Two working aprons share a service spine beside the through freight road. */
    private static void freightApron(GenContext c) {
        int[] concrete = new int[3];
        for (int age = 0; age < 3; age++)
            concrete[age] = c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for (int[] bank : new int[][]{{30,64},{86,120}}) {
            for (int y = bank[0]; y <= bank[1]; y++) for (int x = 6; x <= 36; x++) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, concrete[RoadRasterizer.age(.3, 9317, x, y)]);
            }
        }
        // Inner service lane meets the artery; aircraft arrive across open ground to the west.
        pave(c, 32, 30, 36, 120, GroundKind.STREET);
        int road = c.topology.surfaceIndex(Material.ASPHALT.block(1));
        for (int y = 30; y <= 120; y++) for (int x = 32; x <= 36; x++)
            c.topology.setSurface(x, y, road);
        int[][] stands = {{18,58},{23,47},{18,36},{18,92},{23,103},{18,114}};
        for (int[] stand : stands) c.landingPads.add(new LandingPad(stand[0], stand[1],
                8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }

    private static void buildings(GenContext c) {
        // Each destination overlooks its own road beat; none sits in the carriageway.
        BuildingStamp.FREIGHT_L.place(c, 72, 46, 2);
        BuildingStamp.FREIGHT_T.place(c, 176, 87, 0);
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.COMMS,
                new BlockLeaf(247, 46, 270, 62, false), c, Frontage.BOTTOM, true);
        BuildingStamp.OFFSET_DEPOT.place(c, 54, 28, 1);
        BuildingStamp.FREIGHT_L.place(c, 105, 91, 0);
        BuildingStamp.FREIGHT_T.place(c, 131, 41, 2);
        BuildingStamp.FREIGHT_L.place(c, 202, 35, 3);
        BuildingStamp.T_WORKSHOP.place(c, 226, 96, 0);
        shell(c, 286, 38, 309, 59, Frontage.BOTTOM);
        shell(c, 286, 94, 309, 111, Frontage.TOP);
        // Roadside warehouses form infantry cover; the outer district gives way
        // to hills. Keep the checkpoint's terminal blocks: their narrow service
        // alleys admit infantry but not the freight chassis around the gun line.
        for (int[] lot : new int[][]{{282,0,319,27},{40,46,68,63},
                {174,32,196,63}})
            shell(c, lot[0], lot[1], lot[2], lot[3], Frontage.BOTTOM);
        BuildingStamp.FREIGHT_T.place(c, 100, 41, 1);
        BuildingStamp.FREIGHT_L.place(c, 75, 91, 1);
        BuildingStamp.T_WORKSHOP.place(c, 201, 95, 3);
        for (int[] lot : new int[][]{{282,118,319,143},{38,87,68,112},
                {130,87,156,112}})
            shell(c, lot[0], lot[1], lot[2], lot[3], Frontage.TOP);
    }
    private static void terrain(GenContext c) {
        boolean[] reserved = new boolean[c.width * c.height];
        // Keep the whole staging apron and its westward aircraft approach open.
        // The road stamper owns the full freight and raider-entry road widths.
        for (int y = 10; y <= 135; y++) for (int x = 0; x <= 38; x++)
            reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.THUNDER_RUN,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
        // These are separate tactical approaches, not merely two points reachable
        // by falling back to the freight road. Optional woods must keep both.
        boolean[][] circulation = new boolean[c.width][c.height];
        for (int y = 10; y <= 135; y++) for (int x = 0; x <= 38; x++) circulation[x][y] = true;
        for (int[] flank : new int[][]{{39,18,263,28},{39,116,263,116}}) {
            boolean[] side = new boolean[c.width * c.height];
            for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
                side[y * c.width + x] = flank[1] < 75 ? y < 65 : y > 85;
            int[] path = GridPathfinder.findPath(c.grid, flank[0], flank[1], flank[2], flank[3], null, side);
            if (Paths.isEmpty(path)) throw new IllegalStateException("Thunder Run has no infantry flank at " + flank[1]);
            circulation[flank[0]][flank[1]] = true;
            for (int i = 0; i < Paths.cellCount(path); i++) circulation[Paths.cellX(path, i)][Paths.cellY(path, i)] = true;
        }
        c.put(BspKeys.ROAD_RESERVATION, circulation);
    }

    private static void shell(GenContext c, int l, int t, int r, int b, Frontage facing) {
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.DEPOT,
                new BlockLeaf(l, t, r, b, false), c, facing);
    }
    private static void pave(GenContext c, int l, int t, int r, int b, GroundKind kind) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, kind);
        }
    }
    private static void perimeter(GenContext c) {
        // Freight-yard boundaries preserve the pedestrian service alleys.
        for (int[] run : new int[][]{{72,112,33},{125,151,32},{199,231,28},{236,263,117}})
            for (int x = run[0]; x <= run[1]; x++) {
                c.grid.setWalkable(x, run[2], false); c.topology.setWall(x, run[2], true);
            }
    }
    /** Prepared positions use the same real cover and transparent fixtures as ordinary defense posts. */
    private static void fortifications(GenContext c) {
        // Separate AA pits overlook the first convoy leg, far outside the western insertion corridor.
        for (Cell gun : ANTI_AIR_POSTS) supportEmplacement(c, gun, false);
        // Dispatch looks east along the verge; Crossing covers its western side road.
        supportEmplacement(c, SUPPORT_POSTS.get(0), true);
        supportEmplacement(c, SUPPORT_POSTS.get(1), false);
        // Firing aprons face the road, with a wide opening aligned to each actual doorway.
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            int y = i == 1 ? p.top - 2 : p.bottom + 2;
            String face = i == 1 ? "s" : "n";
            for (int x = p.left; x <= p.right; x++) {
                if (Math.abs(x - p.anchorCellX) <= 2) continue;
                fixture(c, x, y, "doodad.sandbag-straight-" + face);
            }
        }
        // Two inspection yards frame the open freight lane. Side gates keep every yard crossable.
        fencedYard(c, 284, 30, 316, 64, 51, 64);
        fencedYard(c, 284, 86, 316, 115, 98, 86);
        for (Cell gun : EXIT_GUNS) {
            // West-facing horseshoe, open to its service yard on the east.
            for (int y = gun.y - 2; y <= gun.y + 2; y++)
                fixture(c, gun.x - 2, y, "doodad.sandbag-" + (y == gun.y - 2 ? "corner-sw"
                        : y == gun.y + 2 ? "corner-nw" : "straight-w"));
            for (int x = gun.x - 1; x <= gun.x + 2; x++) {
                fixture(c, x, gun.y - 2, "doodad.sandbag-straight-s");
                fixture(c, x, gun.y + 2, "doodad.sandbag-straight-n");
            }
        }
        // Dispersed rifle bays overlook the approach without putting obstacles in the carriageway.
        for (int y : new int[]{61, 89}) for (int x = 287; x <= 293; x++)
            fixture(c, x, y, "doodad.sandbag-straight-" + (y < 75 ? "n" : "s"));
        // Concrete gate abutments sit beyond the road shoulders; the full convoy band stays open.
        for (int x = 310; x <= 311; x++) for (int y : new int[]{66, 67, 83, 84}) {
            c.grid.setWalkable(x, y, false);
            c.topology.setWall(x, y, true);
        }
        // Paint uses a floor decal; changing ground kind alone can disappear into a road palette.
        var stripe = TileRegistry.installed().doodad("doodad.fl-striped-yellow");
        if (stripe == null) throw new IllegalStateException("Missing checkpoint road paint");
        for (int y = 68; y <= 82; y++) for (int x : new int[]{297, 310})
            c.doodads.add(new Doodad(x, y, stripe, 1));
    }
    private static void supportEmplacement(GenContext c, Cell gun, boolean eastFacing) {
        int front = gun.x + (eastFacing ? 2 : -2);
        for (int y = gun.y - 2; y <= gun.y + 2; y++)
            fixture(c, front, y, "doodad.sandbag-" + (y == gun.y - 2 ? "corner-s"
                    : y == gun.y + 2 ? "corner-n" : "straight-") + (eastFacing ? "e" : "w"));
        for (int x = gun.x - 1; x <= gun.x + 1; x++) {
            fixture(c, x, gun.y - 2, "doodad.sandbag-straight-s");
            fixture(c, x, gun.y + 2, "doodad.sandbag-straight-n");
        }
        // A small prepared gravel bed; the open back remains connected for the crew.
        int gravel = c.topology.surfaceIndex("floors.stone");
        for (int y = gun.y - 1; y <= gun.y + 1; y++)
            for (int x = gun.x - 1; x <= gun.x + 1; x++)
                c.topology.setSurface(x, y, gravel);
    }
    private static void fencedYard(GenContext c, int l, int t, int r, int b, int sideGate, int roadEdge) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) {
            if (x != l && x != r && y != t && y != b) continue;
            if ((x == l || x == r) && (y == sideGate || y == sideGate + 1)) continue;
            if (y == roadEdge && x >= 298 && x <= 309) continue;
            if (y != roadEdge && x >= 297 && x <= 300) continue;
            String suffix = x == l && y == t ? "corner-nw" : x == r && y == t ? "corner-ne"
                    : x == l && y == b ? "corner-sw" : x == r && y == b ? "corner-se"
                    : y == t || y == b ? "straight-h" : "straight-v";
            fixture(c, x, y, "doodad.industrial-fence-" + suffix);
        }
    }
    private static void fixture(GenContext c, int x, int y, String id) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null || tiles.doodad(id) == null)
            throw new IllegalStateException("Missing Thunder Run fortification: " + id);
        if (!c.grid.isWalkable(x, y))
            throw new IllegalStateException("Thunder Run fortification overlaps structure at " + x + "," + y);
        // Sandbags are knee-high cover everyone steps over, mechs and infantry
        // alike: the cell stays walkable and the doodad supplies the cover.
        // Fences and other fixtures still close their cell.
        if (!id.startsWith("doodad.sandbag-")) c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.topology.setWall(x, y, false);
        c.topology.setFixture(x, y, true);
        c.doodads.add(new Doodad(x, y, tiles.doodad(id)));
    }
    private static void cargo(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        var prop = tiles.doodad("doodad.industrial-crate-stack");
        if (prop != null) for (int[] lot : new int[][]{{75,37},{88,37},{203,62},{218,62},{249,110},{260,110},{312,60},{312,62},{312,89},{312,91}})
            c.doodads.add(new Doodad(lot[0], lot[1], prop));
    }
    /** District-specific materials use the existing atlas without changing other missions. */
    private static void surfaces(GenContext c) {
        int road = c.topology.surfaceIndex("road.road");
        int gravel = c.topology.surfaceIndex("floors.stone");
        int dirt = c.topology.surfaceIndex("floors.dirt");
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            GroundKind kind = c.topology.getGroundKind(x, y);
            if (kind == GroundKind.STREET && c.topology.getSurface(x,y) == 0)
                c.topology.setSurface(x, y, road);
            else if (kind == GroundKind.COURTYARD && c.topology.getSurface(x,y) == 0)
                c.topology.setSurface(x, y, x < 235 ? dirt : gravel);
        }
        // Real paint decals retain lane rhythm without substituting another paving material.
        var paint = TileRegistry.installed().doodad("doodad.fl-striped-yellow");
        for (int x = 4; x < WIDTH; x += 10)
            c.doodads.add(new Doodad(x, 75, paint));
    }
    private static RoadGraph roads() {
        int[][] points = {{0,75},{165,75},{275,75},{319,75},{165,0},{165,143},{275,0},{275,143}};
        int[][] joins = {{0,1},{1,2},{2,3},{1,4},{1,5},{2,6},{2,7}};
        List<RoadGraph.Node> nodes = new ArrayList<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < points.length; i++) nodes.add(new RoadGraph.Node(i, points[i][0], points[i][1], i == 0 || i >= 3));
        for (int[] join : joins) {
            var a = nodes.get(join[0]); var b = nodes.get(join[1]);
            int length = Math.abs(b.cellX - a.cellX) + Math.abs(b.cellY - a.cellY) + 1;
            int[] xs = new int[length], ys = new int[length];
            for (int j = 0; j < length; j++) {
                xs[j] = a.cellX + j * Integer.signum(b.cellX - a.cellX);
                ys[j] = a.cellY + j * Integer.signum(b.cellY - a.cellY);
            }
            edges.add(new RoadGraph.Edge(edges.size(), a, b, xs, ys));
        }
        return new RoadGraph(nodes, edges);
    }
}
