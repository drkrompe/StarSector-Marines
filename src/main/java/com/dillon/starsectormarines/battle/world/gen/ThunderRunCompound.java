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
                ThunderRunCompound::buildings, ThunderRunCompound::perimeter, ThunderRunCompound::cargo, new FinalizeStage())).run(c);
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
        pave(c, 0, 68, WIDTH - 1, 82, GroundKind.STREET);
        pave(c, 10, 12, 36, 132, GroundKind.STREET);
        pave(c, 57, 35, 111, 64, GroundKind.COURTYARD);
        pave(c, 143, 37, 195, 112, GroundKind.COURTYARD);
        pave(c, 235, 30, 294, 115, GroundKind.COURTYARD);
        // Crossing and road ambush approaches remain vehicle-wide.
        pave(c, 159, 0, 171, 143, GroundKind.STREET);
        pave(c, 269, 0, 281, 143, GroundKind.STREET);
        pave(c, 0, 68, WIDTH - 1, 82, GroundKind.STREET);
        for (int y : new int[]{22, 40, 58, 94, 112, 130}) {
            LandingPad pad = new LandingPad(21, y, 3, 3, LandingPad.Approach.WEST);
            c.landingPads.add(pad);
            for (int py = pad.bottom(); py <= pad.top(); py++) for (int px = pad.left(); px <= pad.right(); px++)
                if (px == pad.left() || px == pad.right() || py == pad.bottom() || py == pad.top())
                    c.topology.setGroundKind(px, py, GroundKind.STRIPED);
        }
        for (int x = 4; x < WIDTH; x += 10) pave(c, x, 75, x + 3, 75, GroundKind.STRIPED);
    }
    private static void buildings(GenContext c) {
        // Each destination overlooks its own road beat; none sits in the carriageway.
        shell(c, 72, 46, 91, 62, Frontage.BOTTOM);
        shell(c, 176, 87, 194, 105, Frontage.TOP);
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.COMMS,
                new BlockLeaf(247, 46, 270, 62, false), c, Frontage.BOTTOM, true);
        shell(c, 51, 23, 68, 43, Frontage.BOTTOM);
        shell(c, 103, 89, 127, 109, Frontage.TOP);
        shell(c, 129, 39, 153, 60, Frontage.BOTTOM);
        shell(c, 199, 32, 225, 55, Frontage.BOTTOM);
        shell(c, 220, 91, 245, 111, Frontage.TOP);
        shell(c, 286, 38, 309, 59, Frontage.BOTTOM);
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
        // Raised freight-yard boundaries frame the road while leaving broad flanks.
        for (int[] run : new int[][]{{72,112,33},{125,151,32},{199,231,28},{236,263,117},{284,312,116}})
            for (int x = run[0]; x <= run[1]; x++) {
                c.grid.setWalkable(x, run[2], false); c.topology.setWall(x, run[2], true);
            }
    }
    private static void cargo(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        var prop = tiles.doodad("doodad.industrial-crate-stack");
        if (prop != null) for (int[] lot : new int[][]{{75,37},{88,37},{102,37},{203,62},{218,62},{249,110},{260,110},{291,91},{301,91}})
            c.doodads.add(new Doodad(lot[0], lot[1], prop));
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
