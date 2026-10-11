package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Authored small territorial profile. Decoration never adds capture requirements. */
public final class JunctionCompound {
    public static final int WIDTH = 192, HEIGHT = 112;
    public static final List<String> SITE_NAMES = List.of("Western turnout", "Weigh Station", "Freight Yard", "Junction Dispatch");
    public record Layout(MapResult map, List<TacticalNode> sites, PointOfInterest reserve) {
        public Layout { sites = List.copyOf(sites); }
    }
    private JunctionCompound() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Open the Junction", List.of(JunctionCompound::grounds,
                JunctionCompound::buildings, JunctionCompound::perimeter, JunctionCompound::cargo,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                new FinalizeStage())).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 56,
                10, 24, 34, 87, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, i == 2 ? 8 : 4));
        }
        MapResult map = new MapResult(c.grid, c.topology, 21, 56, 148, 55,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS),
                List.of(), roads(), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Junction berth obstructed");
            requireRoute(map, pad.centerX, pad.centerY);
        }
        for (TacticalNode site : sites) requireRoute(map, site.anchorX, site.anchorY);
        for (PointOfInterest p : c.pois) requireRoute(map, p.interiorAnchorX, p.interiorAnchorY);
        return new Layout(map, sites, c.pois.get(7));
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, 21, 56, x, y)))
            throw new IllegalStateException("Junction route disconnected at " + x + "," + y);
    }
    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case FROZEN -> GroundKind.SNOW;
            case ARID -> GroundKind.SAND;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, WIDTH - 1, HEIGHT - 1, wild);
        pave(c, 56, 22, 164, 91, GroundKind.SIDEWALK);
        pave(c, 58, 24, 162, 89, GroundKind.COURTYARD);
        // Through-road, northern loading spur, then two distinct outbound roads.

        var roads = RoadCatalog.plan(RoadCatalog.JUNCTION);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        for (int y : new int[]{31, 43, 55, 67, 79}) {
            LandingPad pad = new LandingPad(21, y, 3, 3, LandingPad.Approach.WEST);
            c.landingPads.add(pad);
            for (int py = pad.bottom(); py <= pad.top(); py++) for (int px = pad.left(); px <= pad.right(); px++)
                if (px == pad.left() || px == pad.right() || py == pad.bottom() || py == pad.top())
                    c.topology.setGroundKind(px, py, GroundKind.STRIPED);
        }
        for (int x = 96; x < 160; x += 8) pave(c, x, 25, x + 3, 29, GroundKind.STRIPED);
    }
    private static void buildings(GenContext c) {
        shell(c, 61, 39, 77, 52, Frontage.BOTTOM); // Weigh station by the inbound gate.
        shell(c, 97, 38, 115, 51, Frontage.TOP); // Freight office off the service road.
        shell(c, 137, 41, 160, 53, Frontage.BOTTOM); // Dispatch overlooking the junction.
        shell(c, 42, 28, 53, 49, Frontage.BOTTOM); // Screens shield the apron from the gate.
        shell(c, 43, 65, 55, 84, Frontage.TOP);
        shell(c, 94, 66, 119, 85, Frontage.TOP); // Warehouse blocks a map-wide firing corridor.
        shell(c, 138, 66, 160, 87, Frontage.TOP);
        shell(c, 92, 8, 115, 17, Frontage.BOTTOM); // Maintenance staging beside the reserve road.
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
        // The maintenance lane is screened from the freight apron. Its western
        // gate lets a finite reserve reach the rear of the weigh station.
        for (int x = 64; x <= 128; x++) {
            c.grid.setWalkable(x, 28, false);
            c.topology.setWall(x, 28, true);
        }
    }
    private static void cargo(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (int x = 97; x < 160; x += 8) {
            var prop = tiles.doodad("doodad.industrial-crate-stack");
            if (prop != null) c.doodads.add(new Doodad(x, 27, prop));
        }
    }
    private static RoadGraph roads() {
        int[][] points = {{0,57},{85,57},{173,57},{173,0},{173,82},{191,82},{85,33},{173,33}};
        int[][] joins = {{0,1},{1,2},{2,7},{7,3},{2,4},{4,5},{1,6},{6,7}};
        List<RoadGraph.Node> nodes = new ArrayList<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < points.length; i++) nodes.add(new RoadGraph.Node(i, points[i][0], points[i][1], i == 0 || i == 3 || i == 5));
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
