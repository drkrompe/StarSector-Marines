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
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;
import java.util.Random;

/** Latchkey inspection grounds: dispatch, tooling and operator access; homes remain outside the duty area. */
public final class LatchkeyYard {
    public static final int WIDTH = 176, HEIGHT = 128;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    public static final int TOOL_X = 98, TOOL_Y = 64;
    public static final String TOOL_ID = "doodad.industrial-machine-tool";
    public record Site(String name, int x, int y, int left, int bottom, int right, int top) { }
    public static final List<Site> SITES = List.of(
            new Site("Dispatch house / freight gate", 58, 72, 51, 65, 63, 75),
            new Site("Tooling hall", 94, 65, 85, 61, 103, 79),
            new Site("Operator entrance", 88, 42, 81, 35, 93, 45));
    private LatchkeyYard() { }
    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Latchkey Yard", List.of(LatchkeyYard::grounds, LatchkeyYard::stores,
                new FinalizeStage(), new OutdoorLandscapeStage(24,59,58,72,94,65,88,42,150,76,145,36))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, 150, 76,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var site : SITES)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, site.x(), site.y()).length == 0)
                throw new IllegalStateException("Disconnected Latchkey objective: " + site.name());
        return map;
    }
    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, GroundKind.STONE);
            if (x < 42 || x < 123 && y >= 30 && y <= 90 || y >= 88 && y <= 100) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.7, 18534, x, y))));
                c.markMadeGround(x, y);
            }
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }
    private static void stores(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 51, 65, 0);
        BuildingStamp.FREIGHT_T.place(c, 85, 61, 0);
        BuildingStamp.T_WORKSHOP.place(c, 81, 35, 0);
        BuildingStamp.CORNER_HOME.place(c, 133, 103, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 148, 103, 0);
        var tool = new Doodad(TOOL_X, TOOL_Y, TileRegistry.installed().doodad(TOOL_ID));
        for (int y = TOOL_Y; y < TOOL_Y + tool.footprintCellsY; y++)
            for (int x = TOOL_X; x < TOOL_X + tool.footprintCellsX; x++) {
                if (!c.grid.isWalkable(x,y) || c.topology.isFixture(x,y))
                    throw new IllegalStateException("Calibration tool overlaps Latchkey circulation");
                c.grid.setWalkable(x,y,false); c.topology.setFixture(x,y,true);
            }
        c.doodads.add(tool);
        var yard = new StoryServiceYard(c);
        yard.cargo(48, 38); yard.cargo(67, 85); yard.cargo(113, 55);
        yard.bay(70, 50, 80, 59);
    }
    public static boolean inside(CellTopology topology, int x, int y, int index) {
        var site = SITES.get(index);
        return x >= site.left() && x <= site.right() && y >= site.bottom() && y <= site.top()
                && topology.hasBuildingKindHint(x,y) && !topology.isWall(x,y);
    }
}
