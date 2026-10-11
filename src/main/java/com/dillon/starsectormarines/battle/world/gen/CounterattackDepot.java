package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts.Place;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/** The second Wayline chapter buys space for approach, yard and depot fighting. */
public final class CounterattackDepot {
    public static final int WIDTH = 160, HEIGHT = 96;
    public record Layout(MapResult map, PointOfInterest depot, List<Place> stages) {}
    private CounterattackDepot() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Wayline freight depot", List.of(CounterattackDepot::grounds,
                CounterattackDepot::buildings, CounterattackDepot::perimeter,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                CounterattackDepot::yardSurfaces, CounterattackDepot::cargo, CounterattackDepot::terrain,
                new FinalizeStage(), new OutdoorLandscapeStage(18, 46, 73, 46))).run(c);
        PointOfInterest depot = c.pois.get(0);
        List<Place> stages = List.of(new Place("Freight gate", 73, 46),
                new Place("Loading yard", 95, 46),
                new Place("Dispatch warehouse", depot.interiorAnchorX, depot.interiorAnchorY));
        MapResult map = new MapResult(c.grid, c.topology, 18, 46, 73, 46,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS),
                List.of(), roads(), c.landingPads);
        for (LandingPad pad : map.landingPads)
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Depot berth obstructed");
        for (Place place : stages) requireRoute(map, place.x(), place.y());
        for (PointOfInterest place : c.pois) requireRoute(map, place.interiorAnchorX, place.interiorAnchorY);
        for (LandingPad pad : map.landingPads) requireRoute(map, pad.centerX, pad.centerY);
        for (RoadGraph.Edge edge : map.roadGraph.edges()) for (int i = 0; i < edge.cellsX.length; i++)
            if (!map.grid.isWalkable(edge.cellsX[i], edge.cellsY[i])) throw new IllegalStateException("Depot freight route obstructed");
        return new Layout(map, depot, stages);
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, 18, 46, x, y)))
            throw new IllegalStateException("Depot circulation disconnected at " + x + "," + y);
    }

    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case ARID -> GroundKind.SAND;
            case FROZEN -> GroundKind.SNOW;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, WIDTH - 1, HEIGHT - 1, wild);
        if (wild == GroundKind.GRASS) {
            PatchField field = new PatchField(c.seed, 7);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
                if (field.sample(x, y) < .15f) c.topology.setGroundKind(x, y, GroundKind.DIRT);
        }
        pave(c, 66, 16, 149, 80, GroundKind.SIDEWALK);
        pave(c, 68, 18, 147, 78, GroundKind.COURTYARD);
        pave(c, 0, 41, 82, 51, GroundKind.SIDEWALK);

        pave(c, 74, 0, 82, HEIGHT - 1, GroundKind.SIDEWALK);
        // Freight circulation doglegs around the weigh office before dispatch.
        var roads = RoadCatalog.plan(RoadCatalog.COUNTERATTACK);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        for (int[] stand : new int[][]{{20,40},{22,52},{21,28}})
            c.landingPads.add(new LandingPad(stand[0], stand[1], 8, 3,
                    LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }

    private static void yardSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for (int age = 0; age < 3; age++) concrete[age] = c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
            if (c.topology.getGroundKind(x, y) == GroundKind.COURTYARD && !c.topology.isWall(x, y))
                c.topology.setSurface(x, y, concrete[RoadRasterizer.age(.45, 8107, x, y)]);
    }

    private static void terrain(GenContext c) {
        boolean[] reserved = new boolean[c.width * c.height];
        for (LandingPad pad : c.landingPads)
            for (int y = Math.max(0, pad.bottom() - 2); y <= Math.min(c.height - 1, pad.top() + 2); y++)
                for (int x = 0; x <= pad.right() + 2; x++) reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.COUNTERATTACK, c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }

    private static void buildings(GenContext c) {
        shell(c, 111, 37, 138, 58, Frontage.LEFT); // Dispatch and bonded freight: final destination.
        BuildingStamp.OFFSET_DEPOT.place(c, 88, 20, 0);
        shell(c, 84, 41, 91, 52, Frontage.RIGHT); // Weigh office and covered freight check.
        BuildingStamp.FREIGHT_L.place(c, 84, 59, 0);
        shell(c, 70, 30, 76, 39, Frontage.BOTTOM); // Gate office, behind the perimeter.
        BuildingStamp.FREIGHT_T.place(c, 39, 21, 0); // Approach sheds screen the boat apron.
        BuildingStamp.T_WORKSHOP.place(c, 42, 56, 2);
        BuildingStamp.OFFSET_DEPOT.place(c, 117, 66, 1);
    }

    private static void shell(GenContext c, int l, int t, int r, int b, Frontage facing) {
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.DEPOT,
                new BlockLeaf(l, t, r, b, false), c, facing);
    }

    private static void perimeter(GenContext c) {
        for (int y = 18; y <= 78; y++) if (y < 42 || y > 50) {
            wall(c, 68, y); wall(c, 147, y);
        }
        for (int x = 68; x <= 147; x++) if (x < 75 || x > 81) {
            wall(c, x, 18); wall(c, x, 78);
        }
        for (int x : new int[]{72, 96}) for (int y : new int[]{42, 43, 49, 50})
            c.grid.placeEdgeBarrier(x, y, Direction.W, SharedEdgeBarrier.Kind.REVETMENT);
    }

    private static void wall(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false); c.topology.setWall(x, y, true);
    }
    private static void pave(GenContext c, int l, int t, int r, int b, GroundKind kind) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, kind);
        }
    }
    private static void cargo(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // Inbound trailer court, outside the gate's line of advance.
        yard.gravel(39, 8, 59, 17);
        yard.fence(39, 8, 59, 17);
        yard.bay(42, 10, 47, 15);
        yard.prop(43, 12, "parked-cargo-truck", 1);
        yard.cargo(51, 11);
        // Upper approach workshop service apron.
        yard.gravel(41, 72, 60, 78);
        yard.prop(44, 74, "industrial-turbine-set", 0);
        yard.prop(51, 74, "industrial-cable-reel", 0);
        yard.cargo(54, 74);
        // Separate loading courts flank dispatch, away from the live stage positions.
        yard.bay(94, 68, 101, 74);
        yard.cargo(95, 70);
        yard.bay(130, 65, 142, 74);
        yard.prop(132, 68, "parked-flatbed-truck", 1);
        yard.cargo(137, 70);
        yard.prop(131, 73, "stores-cargo-loader", 0);
        yard.gravel(112, 20, 140, 28);
        for (int x : new int[]{115, 124, 133}) {
            yard.bay(x, 21, x + 5, 27);
            yard.prop(x + 1, 24, "parked-cargo-truck", 1);
        }
    }

    private static RoadGraph roads() {
        int[][] points = {{0,46},{78,46},{78,35},{106,35},{106,46},{110,46}};
        List<RoadGraph.Node> nodes = new ArrayList<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            RoadGraph.Node node = new RoadGraph.Node(i, points[i][0], points[i][1], i == 0);
            if (i > 0) {
                RoadGraph.Node previous = nodes.get(i - 1);
                int length = Math.abs(node.cellX - previous.cellX) + Math.abs(node.cellY - previous.cellY) + 1;
                int[] xs = new int[length], ys = new int[length];
                for (int j = 0; j < length; j++) {
                    xs[j] = previous.cellX + j * Integer.signum(node.cellX - previous.cellX);
                    ys[j] = previous.cellY + j * Integer.signum(node.cellY - previous.cellY);
                }
                edges.add(new RoadGraph.Edge(edges.size(), previous, node, xs, ys));
            }
            nodes.add(node);
        }
        return new RoadGraph(nodes, edges);
    }
}
