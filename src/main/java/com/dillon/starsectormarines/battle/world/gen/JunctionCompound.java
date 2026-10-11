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
                JunctionCompound::yardSurfaces, JunctionCompound::serviceYards, JunctionCompound::terrain, new FinalizeStage())).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 56,
                10, 9, 40, 60, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, i == 2 ? 8 : 4));
        }
        MapResult map = new MapResult(c.grid, c.topology, 21, 56, 148, 55,
                c.pois, c.doodads, new TacticalMap(sites), c.get(BspKeys.BUILDINGS),
                List.of(), AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.JUNCTION), "western-boat-apron"), c.landingPads);
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
        // A rounded working apron, not a blue superblock pasted under the facilities.
        for (int y = 22; y <= 91; y++) for (int x = 56; x <= 164; x++) {
            if (roundedYard(x, y, 0)) c.topology.setGroundKind(x, y,
                    roundedYard(x, y, 2) ? GroundKind.COURTYARD : GroundKind.SIDEWALK);
        }
        // Swept eastern fork and service access share one authored centerline source.

        yardSurfaces(c);
        var roads = RoadCatalog.plan(RoadCatalog.JUNCTION);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Staggered, elongated stands fit the player boats on one terminal apron.
        int[][] stands = {{18,50},{32,43},{18,36},{32,29},{18,22},{32,15}};
        for(int i=0;i<5;i++) c.landingPads.add(new LandingPad(stands[i][0],stands[i][1],
                8,3,LandingPad.Approach.WEST,LandingPad.Purpose.FREIGHT_APRON));
        for (int x = 96; x < 160; x += 8) pave(c, x, 25, x + 3, 29, GroundKind.STRIPED);
    }
    private static boolean roundedYard(int x, int y, int inset) {
        int left=56+inset, right=164-inset, bottom=22+inset, top=91-inset;
        if(x<left || x>right || y<bottom || y>top) return false;
        int radius=9-inset;
        int cx=Math.max(left+radius,Math.min(right-radius,x));
        int cy=Math.max(bottom+radius,Math.min(top-radius,y));
        return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius;
    }
    /** Apply after buildings and road cleanup so room floors and landing paint retain ownership. */
    private static void yardSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for(int age=0;age<3;age++) concrete[age]=c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for(int y=0;y<c.height;y++) for(int x=0;x<c.width;x++)
            if(c.topology.getGroundKind(x,y)==GroundKind.COURTYARD && !c.topology.isWall(x,y))
                c.topology.setSurface(x,y,concrete[RoadRasterizer.age(y>62 ? .35 : .55,5119,x,y)]);
    }
    private static void terrain(GenContext c) {
        // Reserve the complete western landing approach, including the open ground
        // outside the apron. Aircraft do not gain altitude collision from this mask.
        boolean[] reserved = new boolean[c.width * c.height];
        for (LandingPad pad : c.landingPads)
            for (int y = Math.max(0, pad.bottom() - 2); y <= Math.min(c.height - 1, pad.top() + 2); y++)
                for (int x = 0; x <= pad.right() + 2; x++) reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.JUNCTION,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }
    private static void buildings(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 63, 40, 2); // Weigh-house hall faces the inbound road.
        BuildingStamp.OFFSET_DEPOT.place(c, 100, 39, 1); // Offset loading rooms open onto the service road.
        shell(c, 137, 41, 160, 53, Frontage.BOTTOM); // Dispatch overlooking the junction.
        shell(c, 42, 28, 53, 49, Frontage.BOTTOM); // Screens shield the apron from the gate.
        BuildingStamp.OFFSET_DEPOT.place(c, 44, 69, 1); // Smaller service annex above the apron.
        shell(c, 94, 66, 119, 85, Frontage.TOP); // Warehouse blocks a map-wide firing corridor.
        BuildingStamp.FREIGHT_L.place(c, 139, 68, 0); // Depot wing encloses an outdoor loading court.
        shell(c, 92, 8, 115, 17, Frontage.BOTTOM); // Maintenance staging beside the reserve road.
    }
    private static void serviceYards(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // Warehouse overflow: three dispatch bays and a separate pallet-storage court.
        yard.gravel(62, 69, 88, 87);
        yard.fence(62, 69, 88, 87);
        for (int x : new int[]{65, 73, 81}) {
            yard.bay(x, 72, x + 5, 78);
            yard.prop(x + 1, 75, x == 73 ? "parked-flatbed-truck" : "parked-cargo-truck", 1);
            yard.cargo(x, 82);
        }
        // The weigh-house's inspection lay-by faces the loading street, not the main road.
        yard.bay(77, 39, 81, 50);
        yard.prop(78, 45, "parked-utility-truck", 1);
        // Freight office staging lanes leave a continuous cross-yard aisle to Dispatch.
        for (int x : new int[]{117, 125}) {
            yard.bay(x, 39, x + 5, 51);
            yard.cargo(x + 1, 47);
            yard.prop(x + 1, 41, "stores-cargo-loader", 1);
        }
        // The L depot's outdoor recess is a loading court, with the facade route kept clear.
        yard.bay(148, 78, 158, 86);
        yard.cargo(150, 82);
        yard.prop(150, 79, "stores-roller-conveyor", 0);
        yard.prop(156, 80, "stores-cargo-loader", 1);
        // Apron service annex: generator and spares stand on their own made ground.
        yard.gravel(44, 84, 54, 91);
        yard.prop(46, 87, "industrial-turbine-set", 0);
        yard.prop(52, 87, "industrial-cable-reel", 0);
        yard.prop(46, 85, "industrial-drum-cluster", 0);
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
}
