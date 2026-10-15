package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.DoorwayClearanceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FrontDepthStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InteriorAnchorFitStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctDefenceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Remote mission host for complete compound templates; story approaches and objectives remain with their story. */
public final class CompoundMissionMap {
    private CompoundMissionMap() {}

    /** Enough ground for every cardinal pose, both gate mouths and a separate four-berth approach. */
    public static boolean fits(int width, int height) { return width >= 160 && height >= 160; }

    /** The two authored gate mouths and the whole site must not silently replace other declared installations. */
    public static boolean supports(TargetProfile profile, Fortification fortification) {
        return fortification.gates() >= 2 && (profile == null || profile.declaredDefences().isNone());
    }

    public record Site(CompoundLayout.Placement placement, int marineX, int marineY,
                       int defenderX, int defenderY, MapPlacement approach, LandingPad.Approach landingApproach) {}

    /** Seeded assembly, with no dependency on the host's furnishing/force random stream. */
    public static Site select(int width, int height, long seed) {
        Random random = new Random(seed ^ 0x434f4d504f554e44L);
        return site(width, height, random.nextInt(4), random.nextBoolean(), random.nextInt(9)-4);
    }

    /** Explicit cardinal arrangement for authored scenarios and the registered visual review. */
    public static Site site(int width, int height, int turn, boolean magazine, int offset) {
        if (!fits(width,height)) throw new IllegalArgumentException("Whole outpost needs separate approach ground");
        var layout = MaintenanceOutpost.layout(magazine);
        var pose = layout.at(0,0,turn).pose();
        int w=pose.posedWidth(layout.footprint().width(),layout.footprint().height());
        int h=pose.posedHeight(layout.footprint().width(),layout.footprint().height());
        int cx=width/2, cy=height/2;
        if (turn==0) cx=width/3;
        if (turn==2) cx=2*width/3;
        if (turn==1) cy=height/3;
        if (turn==3) cy=2*height/3;
        int x=Math.max(12,Math.min(width-w-12,cx-w/2+(turn%2==0 ? 0 : offset)));
        int y=Math.max(12,Math.min(height-h-12,cy-h/2+(turn%2==0 ? offset : 0)));
        var placement=layout.at(x,y,turn);
        int[] gate=placement.cell(78,35);
        return switch (turn) {
            case 0 -> new Site(placement,width-18,gate[1],gate[0],gate[1],MapPlacement.EAST,LandingPad.Approach.EAST);
            case 1 -> new Site(placement,gate[0],height-18,gate[0],gate[1],MapPlacement.NORTH,LandingPad.Approach.NORTH);
            case 2 -> new Site(placement,18,gate[1],gate[0],gate[1],MapPlacement.WEST,LandingPad.Approach.WEST);
            case 3 -> new Site(placement,gate[0],18,gate[0],gate[1],MapPlacement.SOUTH,LandingPad.Approach.SOUTH);
            default -> throw new IllegalArgumentException("Cardinal turn required");
        };
    }

    public static MapResult generate(int width, int height, long seed, TargetProfile profile,
                                     Fortification fortification) {
        return generate(width,height,seed,profile,fortification,select(width,height,seed));
    }

    public static MapResult generate(int width, int height, long seed, TargetProfile profile,
                                     Fortification fortification, Site site) {
        if (!supports(profile,fortification)) throw new IllegalArgumentException("Compound cannot satisfy this defence declaration");
        var c=new GenContext(new NavigationGrid(width,height),new CellTopology(width,height),
                new Random(seed),width,height,seed);
        c.put(BspKeys.MARKET_PROFILE,profile==null ? TargetProfile.NEUTRAL : profile);
        c.put(BspKeys.MARINE_SPAWN,new int[]{site.marineX,site.marineY});
        c.put(BspKeys.DEFENDER_SPAWN,new int[]{site.defenderX,site.defenderY});
        new GenRecipe("Remote maintenance installation",List.of(
                ctx -> ground(ctx,site,fortification),
                ctx -> PrecinctWardStage.finishAuthored(ctx,
                        FortressInterior.author(ctx,site.placement.buildings()),0),
                new PrecinctDefenceStage(),new TacticalLinkStage(),new FinalizeStage(),
                new DoorwayClearanceStage(),new OutdoorLandscapeStage(),new FrontDepthStage(),
                new InteriorAnchorFitStage())).run(c);
        return new MapResult(c.grid,c.topology,site.marineX,site.marineY,site.defenderX,site.defenderY,
                c.pois,c.doodads,c.get(BspKeys.TACTICAL_MAP),c.get(BspKeys.BUILDINGS),c.defensePosts,
                c.get(BspKeys.ROAD_GRAPH),c.landingPads,List.of(),null,c.gantries,c.fixtureTasks,List.of(),List.of(),null,c.get(BspKeys.FRONT_DEPTH));
    }

    private static void ground(GenContext c, Site site, Fortification fortification) {
        CompoundGround.reserve(c,"Maintenance installation",site.placement,fortification,site.approach);
        var routes=new ArrayList<>(site.placement.roads(c.seed));
        int[] gate=site.placement.cell(79,35);
        int turn=site.placement.pose().quarterTurns();
        Point edge=switch (turn) {
            case 0 -> new Point(c.width-1,gate[1]);
            case 1 -> new Point(gate[0],c.height-1);
            case 2 -> new Point(0,gate[1]);
            default -> new Point(gate[0],0);
        };
        routes.add(new Route("Installation access",List.of(new Point(gate[0],gate[1]),edge),
                5,1,.4,c.seed,RoadPlan.Material.ASPHALT));
        CompoundGround.roads(c,routes);
        for (int offset : new int[]{-21,-7,7,21}) {
            int px=site.marineX+(turn%2==1 ? offset : 0);
            int py=site.marineY+(turn%2==0 ? offset : 0);
            var pad=new LandingPad(px,py,5,3,site.landingApproach,LandingPad.Purpose.FALLBACK);
            c.landingPads.add(pad);
            for (int y=pad.bottom()-1;y<=pad.top()+1;y++) for (int x=pad.left()-1;x<=pad.right()+1;x++) {
                c.topology.setGroundKind(x,y,CellTopology.GroundKind.COURTYARD);
                c.topology.setSurface(x,y,c.topology.surfaceIndex(RoadPlan.Material.CONCRETE.block(1)));
                c.markMadeGround(x,y);
            }
        }
    }
}
