package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/** The same Asharu recovery basin on three visits. Battle progression belongs to its future host. */
public final class ShepherdBasin {
    public static final int WIDTH = 304, HEIGHT = 176;
    public enum Visit { SALVAGE_RIGHTS, COLD_START, WORKING_PASSAGE }

    /** Stable authored destinations consumed by the map review; no implicit capture objectives. */
    public record Place(String id, String label, int x, int y) {}
    public static final Place LANDING = new Place("landing", "Receiving apron", 24, 80);
    public static final Place WORKSHOP = new Place("workshop", "Repair workshop", 82, 119);
    public static final Place CRADLE = new Place("cradle", "Morrow recovery cradle", 127, 102);
    public static final Place PUMP = new Place("pump", "Pump station", 171, 117);
    public static final Place BEND = new Place("bend", "Pump bend build ground", 209, 123);
    public static final Place NARROWS = new Place("narrows", "Narrows build ground", 252, 110);
    public static final Place LIFT = new Place("lift", "Climb-out yard", 280, 77);
    public static final List<Place> PLACES = List.of(LANDING, WORKSHOP, CRADLE, PUMP, BEND, NARROWS, LIFT);

    private ShepherdBasin() {}

    public static MapResult generate(long seed, TargetProfile profile, Visit visit) {
        Objects.requireNonNull(visit, "A basin visit is required");
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Morrow recovery basin", List.of(ShepherdBasin::grounds,
                ShepherdBasin::buildings, ShepherdBasin::yards,
                context -> recoveryYard(context, visit),
                context -> RoadRasterizer.clearReplacedSurfaces(context.topology, WIDTH, HEIGHT),
                ShepherdBasin::yardSurfaces, ShepherdBasin::terrain, new FinalizeStage(),
                new OutdoorLandscapeStage(24,80,82,119,127,102,171,117,209,123,252,110,280,77,194,2,301,145))).run(c);
        MapResult map = new MapResult(c.grid, c.topology, LANDING.x(), LANDING.y(), 290, 77,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS), List.of(),
                AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.SHEPHERD_BASIN), ""), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Basin berth obstructed");
            requireRoute(map, pad.centerX, pad.centerY);
        }
        for (Place place : PLACES) requireRoute(map, place.x(), place.y());
        for (var poi : map.pointsOfInterest) requireRoute(map, poi.interiorAnchorX, poi.interiorAnchorY);
        requireRoute(map, 194, 2);
        requireRoute(map, 301, 145);
        return map;
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, LANDING.x(), LANDING.y(), x, y)))
            throw new IllegalStateException("Basin destination disconnected at " + x + "," + y);
    }

    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case ARID -> GroundKind.SAND;
            case FROZEN -> GroundKind.SNOW;
            default -> GroundKind.STONE;
        };
        PatchField patches = new PatchField(c.seed, 12);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, wild == GroundKind.GRASS && patches.sample(x,y) < .2f
                    ? GroundKind.DIRT : wild);
        }
        // Berths belong to a terminal, not six disconnected landing squares.
        concrete(c, 6, 76, 39, 112);
        concrete(c, 7, 46, 39, 61);
        concrete(c, 30, 58, 39, 83);
        for (int[] stand : new int[][]{{19,83},{27,96},{19,107},{21,53}})
            c.landingPads.add(new LandingPad(stand[0], stand[1], 8, 3,
                    LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
        concrete(c, 77, 79, 159, 138); // Salvage cradle and clear working circulation.
        concrete(c, 159, 112, 185, 137); // Pump service court.
        concrete(c, 202, 117, 216, 131); // First deliberately open construction plot.
        concrete(c, 242, 103, 260, 117); // Second plot overlooks the narrows.
        concrete(c, 264, 58, 300, 92); // Climb-out/checkpoint working yard.
        var roads = RoadCatalog.plan(RoadCatalog.SHEPHERD_BASIN);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
    }

    private static void concrete(GenContext c, int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(
                    RoadRasterizer.age(.75, 14110, x, y))));
            c.markMadeGround(x, y);
        }
    }

    private static void buildings(GenContext c) {
        BuildingStamp.OFFSET_DEPOT.place(c, 44, 86, 0); // Receiving store, doors clear of apron.
        BuildingStamp.FREIGHT_T.place(c, 59, 116, 0); // Recovery workshop west of service road.
        BuildingStamp.T_WORKSHOP.place(c, 164, 124, 0); // Pumps, not another warehouse objective.
        BuildingStamp.OFFSET_DEPOT.place(c, 140, 46, 1); // Haul-road security post.
        BuildingStamp.FREIGHT_L.place(c, 205, 68, 2); // Machine store screens lower approach.
        BuildingStamp.T_WORKSHOP.place(c, 260, 126, 2); // Narrows watch, approachable from either side.
        BuildingStamp.OFFSET_DEPOT.place(c, 280, 59, 0); // Old dispatch office at route exit.
        BuildingStamp.FREIGHT_L.place(c, 105, 84, 2); // Dismantling stores beside the cradle.
        BuildingStamp.T_WORKSHOP.place(c, 99, 128, 0); // Bench shop shares the recovery court.
    }

    private static void yardSurfaces(GenContext c) {
        for (int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)
            if (c.topology.getGroundKind(x,y)==GroundKind.COURTYARD && !c.topology.isWall(x,y))
                c.topology.setSurface(x,y,c.topology.surfaceIndex(Material.CONCRETE.block(
                        RoadRasterizer.age(.75,14110,x,y))));
    }

    private static void yards(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        yard.gravel(43, 47, 64, 59);
        yard.bay(45, 49, 54, 57);
        yard.prop(47, 51, "parked-flatbed-truck", 0);
        yard.cargo(58, 51);
        yard.gravel(87, 84, 99, 132);
        yard.bay(89, 87, 97, 97);
            yard.prop(91, 90, "stores-cargo-loader", 0);
        yard.prop(90, 105, "industrial-machine-tool", 0);
        yard.cargo(90, 125);
        // Machinery occupies bays beside the circulation, never the route itself.
        for (int x : new int[]{160,178}) {
            yard.prop(x, 115, "industrial-pump-set", 0);
            yard.prop(x, 120, "industrial-fluid-tank", 0);
        }
        yard.gravel(204, 52, 228, 64);
        yard.fence(204, 52, 228, 64);
        yard.prop(208, 56, "parked-utility-truck", 0);
        yard.cargo(220, 57);
        yard.gravel(287, 96, 296, 107);
        yard.prop(290, 98, "industrial-pipe-bundle", 0);
        yard.prop(290, 103, "industrial-cable-reel", 0);
        yard.bay(203, 118, 215, 130);
        yard.bay(243, 104, 259, 116);
        yard.bay(118, 104, 144, 125);
        yard.fence(116, 103, 146, 127);
        yard.prop(113, 109, "industrial-machine-tool-damaged", 0);
        yard.prop(149, 122, "industrial-pallet-stack", 0);
        // Salvage inventory has working aisles, loading bays and a reason to be here.
        for (int y : new int[]{86,128}) {
            yard.bay(135,y,147,y+7);
            yard.prop(137,y+2,"industrial-turbine-set",0);
            yard.prop(144,y+2,"industrial-pipe-bundle",0);
        }
        yard.prop(127,130,"parked-flatbed-truck",1);
        yard.cargo(119,134);
    }

    private static void recoveryYard(GenContext c, Visit visit) {
        StoryServiceYard yard = new StoryServiceYard(c);
        if (visit != Visit.WORKING_PASSAGE) {
            Doodad hull = new Doodad(119, 105,
                    TileRegistry.installed().doodad("doodad.shepherd-recovery-hull"), 3);
            for (int y = hull.cellY; y < hull.cellY + hull.footprintCellsY; y++)
                for (int x = hull.cellX; x < hull.cellX + hull.footprintCellsX; x++) {
                    if (!c.grid.isWalkable(x,y)) throw new IllegalStateException("Recovery hull footprint obstructed");
                    c.grid.setWalkable(x,y,false);
                    c.grid.setSeeThrough(x,y,false);
                    c.topology.setFixture(x,y,true);
                }
            c.doodads.add(hull);
        }
        // Scuffed concrete and failed shore equipment survive the ship's departure.
        for(int y=105;y<=124;y++)for(int x=119;x<=142;x++)
            c.topology.setTag(x,y,CellTopology.Tag.GROUND_DAMAGED,true);
        if (visit != Visit.SALVAGE_RIGHTS) {
            // The second visit visibly establishes a working yard. These are static
            // assets; repair personnel and powered effects require the battle host.
            yard.prop(99, 111, "industrial-switchboard", 0);
            yard.prop(98, 117, "industrial-cable-reel", 0);
            yard.prop(154, 111, "industrial-turbine-set", 0);
            yard.prop(154, 120, "industrial-machine-tool", 0);
            yard.prop(90, 114, "parked-utility-truck", 1);
            yard.cargo(87, 81);
            yard.prop(113, 118, "industrial-switchboard", 0);
            yard.prop(149, 110, "industrial-cable-reel", 0);
        }
        // Working Passage leaves the cradle empty for a live Tender, never a
        // second static hull beneath it. Its moving body belongs to battle setup.
    }

    private static void terrain(GenContext c) {
        boolean[] reserved = new boolean[WIDTH * HEIGHT];
        for (LandingPad pad : c.landingPads)
            for (int y = pad.bottom()-3; y <= pad.top()+3; y++)
                for (int x = 0; x <= pad.right()+3; x++) reserved[y*WIDTH+x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.SHEPHERD_BASIN,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }
}
