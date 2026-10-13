package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.List;
import java.util.Random;

/** Bonded stores on airless Agreus: separate sealed rooms around a cleared loading court. */
public final class LowQuay {
    public static final int WIDTH = 144, HEIGHT = 112;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    public static final int RECORDS_X = 70, RECORDS_Y = 75, KIT_X = 88, KIT_Y = 42;
    public static final int LOAD_X = 92, LOAD_Y = 58, RESERVE_X = 135, RESERVE_Y = 94;
    private LowQuay() { }
    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Low Quay bonded stores", List.of(LowQuay::grounds, LowQuay::stores,
                new FinalizeStage(), new OutdoorLandscapeStage(24,59,70,75,88,42,92,58,135,94))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, RESERVE_X, RESERVE_Y,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (int[] point : new int[][]{{RECORDS_X,RECORDS_Y},{KIT_X,KIT_Y},{LOAD_X,LOAD_Y},{RESERVE_X,RESERVE_Y}})
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, point[0], point[1]).length == 0)
                throw new IllegalStateException("Disconnected Low Quay objective");
        return map;
    }
    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, GroundKind.STONE);
            if (x < 42 || x < 118 && y >= 32 && y <= 86 || y >= 88 && y <= 100) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.7, 18534, x, y))));
                c.markMadeGround(x, y);
            }
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }
    private static void stores(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 63, 68, 0);
        BuildingStamp.T_WORKSHOP.place(c, 81, 35, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 112, 52, 0);
        var yard = new StoryServiceYard(c);
        yard.cargo(51, 38); yard.cargo(48, 79); yard.cargo(99, 76);
        yard.bay(86, 53, 102, 63);
    }
    public static boolean insideRoom(CellTopology topology, int x, int y, boolean records) {
        return x >= (records ? 63 : 81) && x <= (records ? 75 : 93)
                && y >= (records ? 68 : 35) && y <= (records ? 78 : 45)
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }
}
