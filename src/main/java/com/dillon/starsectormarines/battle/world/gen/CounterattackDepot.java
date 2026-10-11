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
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

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
                CounterattackDepot::cargo,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
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
        for (int y : new int[]{40, 52, 28}) {
            LandingPad pad = new LandingPad(18, y, 3, 3, LandingPad.Approach.WEST);
            c.landingPads.add(pad);
            for (int py = pad.bottom(); py <= pad.top(); py++) for (int px = pad.left(); px <= pad.right(); px++)
                if (px == pad.left() || px == pad.right() || py == pad.bottom() || py == pad.top())
                    c.topology.setGroundKind(px, py, GroundKind.STRIPED);
        }
        // Truck bays beside the loading route, never over the route itself.
        for (int x : new int[]{88, 96, 104, 112, 120, 128, 136})
            pave(c, x, 29, x + 3, 32, GroundKind.STRIPED);
    }

    private static void buildings(GenContext c) {
        shell(c, 111, 37, 138, 58, Frontage.LEFT); // Dispatch and bonded freight: final destination.
        shell(c, 83, 20, 104, 27, Frontage.BOTTOM);
        shell(c, 84, 41, 91, 52, Frontage.RIGHT); // Weigh office and covered freight check.
        shell(c, 84, 57, 102, 74, Frontage.TOP);
        shell(c, 70, 30, 76, 39, Frontage.BOTTOM); // Gate office, behind the perimeter.
        shell(c, 40, 23, 55, 39, Frontage.BOTTOM); // Approach sheds screen the boat apron.
        shell(c, 42, 54, 57, 69, Frontage.TOP);
        shell(c, 113, 65, 138, 74, Frontage.TOP);
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
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (int x : new int[]{87, 91, 99, 103, 116, 120, 128, 132}) for (int y : new int[]{29, 30}) {
            DoodadDef prop = tiles.doodad(y == 29 ? "doodad.industrial-crate-stack" : "doodad.industrial-pallet-stack");
            if (prop != null) c.doodads.add(new Doodad(x, y, prop));
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
