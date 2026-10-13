package com.dillon.starsectormarines.battle.world.gen;

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

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Mission-first freight compound. Circulation owns its ground before any shell is filled. */
public final class ReliefCheckpoint {
    public record Cell(int x, int y) {}
    public record Layout(MapResult map, PointOfInterest relay, Cell gate,
                         Cell forwardPost, Cell openingAttack, List<Cell> mainApproach, List<Cell> serviceApproach) {}

    public static final int WIDTH = 144;
    public static final int HEIGHT = 64;
    private static final Cell GATE = new Cell(83, 30);
    private static final Cell FORWARD_POST = new Cell(99, 30);
    private static final Cell OPENING = new Cell(122, 30);
    private ReliefCheckpoint() {}

    public static Layout generate(int width, int height, long seed, TargetProfile profile) {
        if (width < WIDTH || height < HEIGHT) throw new IllegalArgumentException("Relief needs its First Contract footprint");
        GenContext ctx = new GenContext(new NavigationGrid(width, height),
                new CellTopology(width, height), new Random(seed), width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Relief freight checkpoint", List.of(
                ReliefCheckpoint::grounds, ReliefCheckpoint::buildings,
                ReliefCheckpoint::defences,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                ReliefCheckpoint::yardSurfaces, ReliefCheckpoint::freight, ReliefCheckpoint::terrain,
                new FinalizeStage(), new OutdoorLandscapeStage(21, 20, OPENING.x, OPENING.y))).run(ctx);
        PointOfInterest relay = ctx.pois.stream().filter(p -> p.kind == PointOfInterest.Kind.COMMS).findFirst().orElseThrow();
        List<Cell> main = List.of(new Cell(width - 2, 30), new Cell(114, 30), new Cell(91, 30));
        List<Cell> service = List.of(new Cell(114, height - 2), new Cell(114, 30), new Cell(91, 30));
        RoadGraph roads = roads(main, service);
        MapResult map = new MapResult(ctx.grid, ctx.topology, 21, 20, OPENING.x, OPENING.y,
                ctx.pois, ctx.doodads, new TacticalMap(List.of()), ctx.get(BspKeys.BUILDINGS),
                List.of(), roads, ctx.landingPads);
        validate(map, relay, main, service);
        return new Layout(map, relay, GATE, FORWARD_POST, OPENING, main, service);
    }

    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case ARID -> GroundKind.SAND;
            case FROZEN -> GroundKind.SNOW;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, c.width - 1, c.height - 1, wild);
        if (wild == GroundKind.GRASS) {
            PatchField patches = new PatchField(c.seed, 6);
            for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
                if (patches.sample(x, y) < 0.15f) c.topology.setGroundKind(x, y, GroundKind.DIRT);
        }
        // Made ground is fixed; seed variation belongs inside the freight buildings.
        pave(c, 12, 11, 88, 57, GroundKind.SIDEWALK);
        pave(c, 14, 12, 86, 55, GroundKind.COURTYARD);
        pave(c, 0, 26, c.width - 1, 34, GroundKind.SIDEWALK);

        pave(c, 110, 33, 118, c.height - 1, GroundKind.SIDEWALK);

        // Freight approaches and rear boat apron share the authored road document.
        var roads = RoadCatalog.relief(c.width, c.height);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Three west-facing stands share two banks off the through-road.
        // Employer takes the southern stand; company boats stay together behind the relay.
        for (int y : new int[]{20, 38, 48})
            c.landingPads.add(new LandingPad(25, y, 8, 3,
                    LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
        // Vehicle inspection bays outside the gate, leaving the through-road clear.
        pave(c, 89, 22, 105, 26, GroundKind.COURTYARD);
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
        TerrainBrush.stamp(TerrainCatalog.relief(c.width, c.height, c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }

    private static void buildings(GenContext c) {
        shell(c, PointOfInterest.Kind.COMMS, 68, 39, 80, 48, Frontage.TOP);
        BuildingStamp.OFFSET_DEPOT.place(c, 67, 14, 1); // Supplies beside the southern stand.
        shell(c, PointOfInterest.Kind.DEPOT, 77, 20, 83, 26, Frontage.BOTTOM);
        BuildingStamp.T_WORKSHOP.place(c, 96, 39, 1);
        BuildingStamp.OFFSET_DEPOT.place(c, 124, 41, 0);
        BuildingStamp.T_WORKSHOP.place(c, 96, 9, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 124, 10, 2);
    }

    private static void shell(GenContext c, PointOfInterest.Kind kind, int l, int t, int r, int b, Frontage frontage) {
        OpeningOperationFacilityFiller.fill(kind, new BlockLeaf(l, t, r, b, false), c, frontage);
    }

    private static void defences(GenContext c) {
        // A modest compound wall, not a military fortress. Wide gates remain on the road.
        for (int y = 12; y <= 55; y++) if (y < 27 || y > 33) {
            if (y < 14 || y > 53) wall(c, 14, y);
            wall(c, 86, y);
        }
        for (int x = 14; x <= 86; x++) {
            wall(c, x, 12);
            wall(c, x, 55);
        }
        // Chest-high inspection barriers leave the road passable and provide real cover.
        for (int y : new int[]{27, 28, 32, 33})
            c.grid.placeEdgeBarrier(GATE.x, y, Direction.E, SharedEdgeBarrier.Kind.REVETMENT);
        // Open-backed exterior screen. The freight road remains fully traversable.
        for (int y : new int[]{27, 28, 32, 33})
            c.grid.placeEdgeBarrier(FORWARD_POST.x + 1, y, Direction.E, SharedEdgeBarrier.Kind.REVETMENT);
    }

    private static void wall(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.topology.setWall(x, y, true);
    }

    private static void freight(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // Inspection lay-by beside the gate, leaving both attacker approaches open.
        yard.bay(90, 23, 95, 26);
        yard.prop(91, 24, "parked-utility-truck", 0);
        // Workshop spares and a small receiving court beyond the service-road junction.
        yard.gravel(89, 40, 92, 50);
        yard.prop(90, 43, "industrial-cable-reel", 0);
        yard.prop(90, 47, "industrial-drum-cluster", 0);
        yard.gravel(123, 55, 136, 59);
        yard.cargo(125, 56);
        yard.prop(132, 56, "stores-cargo-loader", 0);
        yard.bay(137, 39, 141, 48);
        yard.prop(138, 42, "parked-flatbed-truck", 1);
        // Relay power is outside its doors, on the apron-facing service strip.
        yard.prop(72, 52, "industrial-cable-reel", 0);
        yard.prop(76, 52, "industrial-drum-cluster", 0);
    }

    private static void pave(GenContext c, int l, int t, int r, int b, GroundKind ground) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, ground);
        }
    }

    private static RoadGraph roads(List<Cell> main, List<Cell> service) {
        Map<Cell, RoadGraph.Node> nodes = new LinkedHashMap<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for (List<Cell> route : List.of(main, service)) {
            RoadGraph.Node previous = null;
            for (Cell cell : route) {
                RoadGraph.Node node = nodes.get(cell);
                if (node == null) {
                    node = new RoadGraph.Node(nodes.size(), cell.x, cell.y, previous == null);
                    nodes.put(cell, node);
                }
                if (previous != null) {
                    int length = Math.abs(node.cellX - previous.cellX) + Math.abs(node.cellY - previous.cellY) + 1;
                    int[] xs = new int[length], ys = new int[length];
                    for (int i = 0; i < length; i++) {
                        xs[i] = previous.cellX + i * Integer.signum(node.cellX - previous.cellX);
                        ys[i] = previous.cellY + i * Integer.signum(node.cellY - previous.cellY);
                    }
                    boolean duplicate = false;
                    for (RoadGraph.Edge edge : edges) if (edge.a == previous && edge.b == node) duplicate = true;
                    if (!duplicate) edges.add(new RoadGraph.Edge(edges.size(), previous, node, xs, ys));
                }
                previous = node;
            }
        }
        return new RoadGraph(new ArrayList<>(nodes.values()), edges);
    }

    private static void validate(MapResult map, PointOfInterest relay, List<Cell> main, List<Cell> service) {
        List<Cell> required = new ArrayList<>(main);
        required.addAll(service);
        required.add(FORWARD_POST);
        required.add(OPENING);
        for (PointOfInterest place : map.pointsOfInterest)
            required.add(new Cell(place.interiorAnchorX, place.interiorAnchorY));
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Relief apron obstructed");
            required.add(new Cell(pad.centerX, pad.centerY));
        }
        for (Cell cell : required) if (Paths.isEmpty(GridPathfinder.findPath(map.grid, cell.x, cell.y, GATE.x, GATE.y)))
            throw new IllegalStateException("Relief checkpoint route disconnected: " + cell);
        for (RoadGraph.Edge edge : map.roadGraph.edges()) for (int i = 0; i < edge.cellsX.length; i++)
            if (!map.grid.isWalkable(edge.cellsX[i], edge.cellsY[i])) throw new IllegalStateException("Relief road obstructed");
    }
}
