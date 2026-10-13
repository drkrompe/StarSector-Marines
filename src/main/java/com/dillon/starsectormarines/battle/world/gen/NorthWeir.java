package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.List;
import java.util.Random;

/** Airless Agreus service yard: enclosed switches flank a covered coolant conduit. */
public final class NorthWeir {
    public static final int WIDTH = 176, HEIGHT = 112;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    public static final int ISOLATION_X = 64, ISOLATION_Y = 59;
    public static final int NORTH_X = 81, NORTH_Y = 75, SOUTH_X = 81, SOUTH_Y = 42;
    public static final int BUILD_X = 108, BUILD_Y = 88;
    public record Place(String id, String label, int x, int y) {}
    public static final List<Place> PLACES = List.of(new Place("apron", "Western service apron", LANDING_X, LANDING_Y),
            new Place("isolation", "Isolation switch / repair load", ISOLATION_X, ISOLATION_Y),
            new Place("north-switch", "North switching room", NORTH_X, NORTH_Y),
            new Place("south-switch", "South switching room", SOUTH_X, SOUTH_Y),
            new Place("road-gun", "Maintenance road defensive position", BUILD_X, BUILD_Y));
    private NorthWeir() {}
    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("North Weir sealed reclamation works", List.of(NorthWeir::grounds, NorthWeir::facilities,
                new FinalizeStage(), new OutdoorLandscapeStage(24,59,64,59,81,42,81,75,108,88,170,88,170,94))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, 164, 88, c.pois, c.doodads,
                new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var place : PLACES) if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, place.x(), place.y()).length == 0)
            throw new IllegalStateException("North Weir destination disconnected: " + place.label());
        return map;
    }
    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            if (x < 46 || y >= 84 && y <= 96 || x >= 48 && x <= 125 && y >= 34 && y <= 83)
            {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.65, 18533, x, y))));
                c.markMadeGround(x, y);
            }
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }
    private static void facilities(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 74, 35, 0);
        BuildingStamp.T_WORKSHOP.place(c, 74, 68, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 126, 48, 0);
        var yard = new StoryServiceYard(c);
        // Covered pipework has a continuous working aisle on both sides; no exposed water.
        for (int x = 70; x < 120; x += 8) yard.prop(x, 57, "industrial-pipe-bundle", 0);
        yard.prop(94, 37, "industrial-pump-set", 0);
        yard.prop(98, 70, "industrial-fluid-tank", 0);
        yard.cargo(51, 38); yard.cargo(48, 75);
        yard.bay(103, 84, 114, 94);
    }
    public static boolean insideRoom(CellTopology topology, int x, int y, boolean north) {
        return x >= 74 && x <= 86 && y >= (north ? 68 : 35) && y <= (north ? 78 : 45)
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }
}
