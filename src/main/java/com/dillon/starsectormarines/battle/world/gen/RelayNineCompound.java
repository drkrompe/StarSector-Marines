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
public final class RelayNineCompound {
    public static final int WIDTH = 208, HEIGHT = 128;
    public static final List<String> SITE_NAMES = List.of("Western apron", "Power Yard", "Freight Gate", "Relay Hall");
    public record Layout(MapResult map, List<TacticalNode> sites) {
        public Layout { sites = List.copyOf(sites); }
    }
    private RelayNineCompound() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Relay Nine", List.of(RelayNineCompound::grounds,
                RelayNineCompound::buildings, RelayNineCompound::perimeter, RelayNineCompound::cargo,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                new FinalizeStage())).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 64,
                10, 14, 34, 113, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, i == 2 ? 8 : 6));
        }
        MapResult map = new MapResult(c.grid, c.topology, 21, 64, 174, 62,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS),
                List.of(), roads(), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Relay Nine berth obstructed");
            requireRoute(map, pad.centerX, pad.centerY);
        }
        for (TacticalNode site : sites) requireRoute(map, site.anchorX, site.anchorY);
        for (PointOfInterest p : c.pois) requireRoute(map, p.interiorAnchorX, p.interiorAnchorY);
        requireRoute(map, 85, 2);
        requireRoute(map, 130, 125);
        return new Layout(map, sites);
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, 21, 64, x, y)))
            throw new IllegalStateException("Relay Nine route disconnected at " + x + "," + y);
    }
    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case FROZEN -> GroundKind.SNOW;
            case ARID -> GroundKind.SAND;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, WIDTH - 1, HEIGHT - 1, wild);
        pave(c, 58, 30, 191, 111, GroundKind.SIDEWALK);
        pave(c, 60, 32, 189, 109, GroundKind.COURTYARD);
        // Inbound road branches around the power installation; the loading
        // corridor reaches the hall from its south, away from the direct road.

        var roads = RoadCatalog.plan(RoadCatalog.RELAY_NINE);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        for (int y : new int[]{22, 38, 54, 70, 86, 102}) {
            LandingPad pad = new LandingPad(21, y, 3, 3, LandingPad.Approach.WEST);
            c.landingPads.add(pad);
            for (int py = pad.bottom(); py <= pad.top(); py++) for (int px = pad.left(); px <= pad.right(); px++)
                if (px == pad.left() || px == pad.right() || py == pad.bottom() || py == pad.top())
                    c.topology.setGroundKind(px, py, GroundKind.STRIPED);
        }
        for (int x = 100; x < 176; x += 9) pave(c, x, 38, x + 3, 42, GroundKind.STRIPED);
    }
    private static void buildings(GenContext c) {
        shell(c, 68, 40, 86, 55, Frontage.BOTTOM); // Power switch house, beside its service road.
        shell(c, 107, 80, 130, 96, Frontage.BOTTOM); // Freight gate office, northern outbound road.
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.COMMS,
                new BlockLeaf(155, 47, 178, 60, false), c, Frontage.BOTTOM, true);
        shell(c, 42, 30, 53, 55, Frontage.BOTTOM); // Apron screens retain two ways inland.
        shell(c, 42, 73, 53, 96, Frontage.TOP);
        shell(c, 96, 47, 117, 57, Frontage.TOP); // Service shop interrupts the direct firing corridor.
        shell(c, 142, 80, 169, 95, Frontage.BOTTOM);
        shell(c, 103, 9, 130, 18, Frontage.BOTTOM);
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
        // Screen the finite road reserve from the main advance. Both ends
        // remain open; this is physical structure, not a one-way script gate.
        for (int x = 65; x <= 136; x++) {
            c.grid.setWalkable(x, 28, false);
            c.topology.setWall(x, 28, true);
        }
    }
    private static void cargo(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (int x = 101; x < 176; x += 9) {
            var prop = tiles.doodad("doodad.industrial-crate-stack");
            if (prop != null) c.doodads.add(new Doodad(x, 40, prop));
        }
    }
    private static RoadGraph roads() {
        int[][] points = {{0,63},{60,63},{183,63},{207,63},{60,33},{183,33},{60,101},{130,101},{207,101},{130,127},{60,23},{85,23},{85,0}};
        int[][] joins = {{0,1},{1,2},{2,3},{1,4},{4,5},{5,2},{1,6},{6,7},{7,8},{7,9},{4,10},{10,11},{11,12}};
        List<RoadGraph.Node> nodes = new ArrayList<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < points.length; i++) nodes.add(new RoadGraph.Node(i, points[i][0], points[i][1], i == 0 || i == 3 || i == 8 || i == 9 || i == 12));
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
