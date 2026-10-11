package com.dillon.starsectormarines.battle.world.gen;

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
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Mission-first freight compound. Circulation owns its ground before any shell is filled. */
public final class ReliefCheckpoint {
    public record Cell(int x, int y) {}
    public record Layout(MapResult map, PointOfInterest relay, Cell gate,
                         Cell openingAttack, List<Cell> mainApproach, List<Cell> serviceApproach) {}

    private static final Cell GATE = new Cell(51, 30);
    private static final Cell OPENING = new Cell(67, 30);
    private ReliefCheckpoint() {}

    public static Layout generate(int width, int height, long seed, TargetProfile profile) {
        if (width < 112 || height < 64) throw new IllegalArgumentException("Relief needs its First Contract footprint");
        GenContext ctx = new GenContext(new NavigationGrid(width, height),
                new CellTopology(width, height), new Random(seed), width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Relief freight checkpoint", List.of(
                ReliefCheckpoint::grounds, ReliefCheckpoint::buildings,
                ReliefCheckpoint::defences, ReliefCheckpoint::freight,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                new FinalizeStage())).run(ctx);
        PointOfInterest relay = ctx.pois.stream().filter(p -> p.kind == PointOfInterest.Kind.COMMS).findFirst().orElseThrow();
        List<Cell> main = List.of(new Cell(width - 2, 30), new Cell(82, 30), new Cell(59, 30));
        List<Cell> service = List.of(new Cell(82, height - 2), new Cell(82, 30), new Cell(59, 30));
        RoadGraph roads = roads(main, service);
        MapResult map = new MapResult(ctx.grid, ctx.topology, 21, 42, OPENING.x, OPENING.y,
                ctx.pois, ctx.doodads, new TacticalMap(List.of()), ctx.get(BspKeys.BUILDINGS),
                List.of(), roads, ctx.landingPads);
        validate(map, relay, main, service);
        return new Layout(map, relay, GATE, OPENING, main, service);
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
        pave(c, 12, 16, 56, 52, GroundKind.SIDEWALK);
        pave(c, 14, 18, 54, 50, GroundKind.COURTYARD);
        pave(c, 0, 26, c.width - 1, 34, GroundKind.SIDEWALK);

        pave(c, 78, 33, 86, c.height - 1, GroundKind.SIDEWALK);

        // Freight approaches and rear boat apron share the authored road document.
        var roads = RoadCatalog.relief(c.width, c.height);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        c.landingPads.add(new LandingPad(21, 42, 3, 3, LandingPad.Approach.WEST));
        c.landingPads.add(new LandingPad(31, 42, 3, 3, LandingPad.Approach.WEST));
        for (LandingPad pad : c.landingPads) {
            for (int y = pad.bottom(); y <= pad.top(); y++) for (int x = pad.left(); x <= pad.right(); x++) {
                if (x == pad.left() || x == pad.right() || y == pad.bottom() || y == pad.top())
                    c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
        // Vehicle inspection bays outside the gate, leaving the through-road clear.
        pave(c, 57, 22, 73, 26, GroundKind.COURTYARD);
        pave(c, 60, 24, 69, 24, GroundKind.STRIPED);
    }

    private static void buildings(GenContext c) {
        shell(c, PointOfInterest.Kind.COMMS, 36, 39, 48, 48, Frontage.TOP);
        shell(c, PointOfInterest.Kind.DEPOT, 18, 19, 33, 26, Frontage.BOTTOM);
        shell(c, PointOfInterest.Kind.DEPOT, 45, 20, 51, 26, Frontage.BOTTOM);
        shell(c, PointOfInterest.Kind.DEPOT, 64, 39, 75, 51, Frontage.RIGHT);
        shell(c, PointOfInterest.Kind.DEPOT, 90, 40, 105, 53, Frontage.LEFT);
        shell(c, PointOfInterest.Kind.DEPOT, 65, 10, 76, 21, Frontage.BOTTOM);
        shell(c, PointOfInterest.Kind.DEPOT, 90, 10, 104, 21, Frontage.BOTTOM);
    }

    private static void shell(GenContext c, PointOfInterest.Kind kind, int l, int t, int r, int b, Frontage frontage) {
        OpeningOperationFacilityFiller.fill(kind, new BlockLeaf(l, t, r, b, false), c, frontage);
    }

    private static void defences(GenContext c) {
        // A modest compound wall, not a military fortress. Wide gates remain on the road.
        for (int y = 18; y <= 50; y++) if (y < 27 || y > 33) {
            wall(c, 14, y);
            wall(c, 54, y);
        }
        for (int x = 14; x <= 54; x++) {
            wall(c, x, 18);
            wall(c, x, 50);
        }
        // Chest-high inspection barriers leave the road passable and provide real cover.
        for (int y : new int[]{27, 28, 32, 33})
            c.grid.placeEdgeBarrier(51, y, Direction.E, SharedEdgeBarrier.Kind.REVETMENT);
    }

    private static void wall(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.topology.setWall(x, y, true);
    }

    private static void freight(GenContext c) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        // Cargo waiting beside the store, clear of its doors and the boat apron.
        for (int x : new int[]{35, 37, 39}) for (int y : new int[]{20, 22}) {
            DoodadDef prop = registry.doodad(y == 20 ? "doodad.industrial-crate-stack" : "doodad.industrial-pallet-stack");
            if (prop != null) c.doodads.add(new Doodad(x, y, prop));
        }
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
        required.add(new Cell(relay.interiorAnchorX, relay.interiorAnchorY));
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
