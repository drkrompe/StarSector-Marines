package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;
import java.util.Random;

/** Rill motor pool, loading street and sheltered southern evacuation apron. */
public final class LastLiftCompound {
    public static final int WIDTH = 192, HEIGHT = 128, LIFT_X = 96, LIFT_Y = 20;
    public record Layout(MapResult map, CivilianEvacuationPlacement placement) {}
    private LastLiftCompound() {}

    public static Layout generate(long seed, TargetProfile profile, int count) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Last Lift", List.of(LastLiftCompound::grounds,
                LastLiftCompound::buildings, LastLiftCompound::freight, new FinalizeStage())).run(c);
        MapResult map = new MapResult(c.grid, c.topology, 96, 20, 152, 64,
                c.pois, c.doodads, new TacticalMap(List.of()), c.get(BspKeys.BUILDINGS),
                List.of(), new RoadGraph(List.of(), List.of()), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Last Lift berth obstructed");
            route(map, pad.centerX, pad.centerY);
        }
        for (int[] point : new int[][]{{2,64},{189,64},{69,88},{69,39},{96,64}}) route(map, point[0],point[1]);
        return new Layout(map, CivilianEvacuationPlacement.authored(map.grid, map.topology,
                c.pois.get(0), LIFT_X, LIFT_Y, count));
    }
    private static void route(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, LIFT_X, LIFT_Y, x, y)))
            throw new IllegalStateException("Disconnected Rill route");
    }
    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case FROZEN -> GroundKind.SNOW;
            case ARID -> GroundKind.SAND;
            default -> GroundKind.STONE;
        };
        pave(c,0,0,191,127,wild);
        pave(c,57,9,135,113,GroundKind.SIDEWALK);
        pave(c,59,11,133,111,GroundKind.COURTYARD);
        pave(c,0,60,191,68,GroundKind.STREET);
        pave(c,92,25,100,103,GroundKind.STREET);
        pave(c,65,31,72,103,GroundKind.STREET);
        pave(c,68,84,117,90,GroundKind.STREET);
        pave(c,68,31,123,38,GroundKind.STREET);
        for (int x : new int[]{68,82,110,124}) {
            c.landingPads.add(new LandingPad(x,20,3,3,LandingPad.Approach.SOUTH));
            pave(c,x-3,17,x+3,23,GroundKind.STRIPED);
        }
        pave(c,93,17,99,23,GroundKind.STRIPED);
    }
    private static void buildings(GenContext c) {
        shell(c,84,95,108,111,Frontage.TOP); // motor pool, doorway toward the apron
        shell(c,76,71,89,82,Frontage.LEFT); // stores shield the maintenance lane
        shell(c,76,41,89,55,Frontage.LEFT);
        shell(c,104,73,128,83,Frontage.BOTTOM);
        shell(c,104,42,129,54,Frontage.TOP);
        shell(c,40,78,55,102,Frontage.RIGHT);
        shell(c,141,29,165,47,Frontage.LEFT);
        shell(c,143,81,166,101,Frontage.LEFT);
    }
    private static void shell(GenContext c,int l,int t,int r,int b,Frontage facing) {
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.DEPOT,
                new BlockLeaf(l,t,r,b,false),c,facing);
    }
    private static void freight(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (int x : new int[]{111,115,119,123}) for (int y : new int[]{92,95}) {
            var prop = tiles.doodad(y == 92 ? "doodad.industrial-crate-stack" : "doodad.industrial-pallet-stack");
            if (prop != null) c.doodads.add(new Doodad(x,y,prop));
        }
        for (int x : new int[]{76,81,111,116,121}) {
            pave(c,x,27,x+2,30,GroundKind.STRIPED);
            var prop = tiles.doodad("doodad.industrial-pallet-stack");
            if (prop != null) c.doodads.add(new Doodad(x,29,prop));
        }
    }
    private static void pave(GenContext c,int l,int t,int r,int b,GroundKind kind) {
        for(int y=t;y<=b;y++) for(int x=l;x<=r;x++) {
            c.grid.setWalkableFloor(x,y); c.topology.setGroundKind(x,y,kind);
        }
    }
}
