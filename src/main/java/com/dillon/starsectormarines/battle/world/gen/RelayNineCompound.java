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
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
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
                RelayNineCompound::yardSurfaces, RelayNineCompound::terrain, new FinalizeStage())).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 64,
                10, 9, 40, 65, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, i == 2 ? 8 : 6));
        }
        MapResult map = new MapResult(c.grid, c.topology, 21, 64, 174, 62,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS),
                List.of(), AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.RELAY_NINE), "western-boat-apron"), c.landingPads);
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
        for(int y=30;y<=114;y++) for(int x=58;x<=193;x++) {
            int cx=Math.max(72,Math.min(179,x)),cy=Math.max(44,Math.min(100,y));
            if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=196) c.topology.setGroundKind(x,y,GroundKind.COURTYARD);
        }
        // Inbound road branches around the power installation; the loading
        // corridor reaches the hall from its south, away from the direct road.

        yardSurfaces(c);
        var roads = RoadCatalog.plan(RoadCatalog.RELAY_NINE);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Staggered, elongated stands fit the player boats on one terminal apron.
        int[][] stands = {{18,50},{32,43},{18,36},{32,29},{18,22},{32,15}};
        for(int i=0;i<6;i++) c.landingPads.add(new LandingPad(stands[i][0],stands[i][1],
                8,3,LandingPad.Approach.WEST,LandingPad.Purpose.FREIGHT_APRON));
        for (int x = 100; x < 176; x += 9) pave(c, x, 38, x + 3, 42, GroundKind.STRIPED);
    }
    private static void yardSurfaces(GenContext c) {
        int[] concrete=new int[3];
        for(int age=0;age<3;age++) concrete[age]=c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for(int y=0;y<c.height;y++) for(int x=0;x<c.width;x++)
            if(c.topology.getGroundKind(x,y)==GroundKind.COURTYARD && !c.topology.isWall(x,y))
                c.topology.setSurface(x,y,concrete[RoadRasterizer.age(.55,5219,x,y)]);
    }
    private static void terrain(GenContext c) {
        // Reserve the complete western landing approach, including the open ground
        // outside the apron. Aircraft do not gain altitude collision from this mask.
        boolean[] reserved = new boolean[c.width * c.height];
        for (LandingPad pad : c.landingPads)
            for (int y = Math.max(0, pad.bottom() - 2); y <= Math.min(c.height - 1, pad.top() + 2); y++)
                for (int x = 0; x <= pad.right() + 2; x++) reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.RELAY_NINE,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }
    private static void buildings(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 71, 42, 2); // Switch-house service wing faces the road.
        BuildingStamp.FREIGHT_L.place(c, 109, 80, 2); // Gate depot and office wing shelter a loading recess.
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.COMMS,
                new BlockLeaf(155, 47, 178, 60, false), c, Frontage.BOTTOM, true);
        shell(c, 42, 30, 53, 55, Frontage.BOTTOM); // Apron screens retain two ways inland.
        BuildingStamp.OFFSET_DEPOT.place(c, 43, 77, 1); // Staggered northern service annex.
        shell(c, 96, 47, 117, 57, Frontage.TOP); // Service shop interrupts the direct firing corridor.
        BuildingStamp.T_WORKSHOP.place(c, 148, 82, 2); // Relay maintenance shop, separate from the hall.
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
}
