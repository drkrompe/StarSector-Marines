package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundMissionMap;
import com.dillon.starsectormarines.battle.world.gen.fortress.MaintenanceOutpost;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finished whole-site geometry and the ordinary mission's actual map-selection seam. */
public final class WholeCompoundSnapshotSuite implements SnapshotSuite {
    public String id() { return "whole-compounds"; }
    public String label() { return "Reusable whole maintenance installations"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        var renderer=new HeadlessBattleMapRenderer(context.modRoot());
        var frames=new ArrayList<SnapshotArtifact>();
        int checked=0;
        for (var surface : new SurfacePalette[]{SurfacePalette.ROCK,SurfacePalette.VERDANT})
            for (boolean magazine : new boolean[]{false,true}) for (int turn=0;turn<4;turn++) {
                var profile=new TargetProfile(3,5,1,0,"",Set.of(),surface,null);
                var site=CompoundMissionMap.site(280,168,turn,magazine,0);
                var map=CompoundMissionMap.generate(280,168,42+turn,profile,
                        magazine ? Fortification.GARRISON : Fortification.PICKET,site);
                verify(map,site); checked++;
                if (surface==SurfacePalette.VERDANT && magazine) {
                    int[] centre=site.placement().cell(40,35);
                    BufferedImage image=renderer.renderView(map,42+turn,centre[0],centre[1],160,90,12);
                    label(image,"WHOLE MAINTENANCE OUTPOST / "+turn*90+" DEGREES / EXTRA MAGAZINE");
                    frames.add(new SnapshotArtifact("outpost-turn-"+turn+".png",image));
                    int[] staff = site.placement().cell(51,30);
                    var staffView = renderer.renderView(map,42+turn,staff[0],staff[1],80,45,24);
                    label(staffView,"OUTPOST STAFF BUILDINGS / "+turn*90+" DEGREES / GAME-SCALE DETAIL");
                    frames.add(new SnapshotArtifact("outpost-staff-turn-"+turn+".png",staffView));
                    int[] shop = site.placement().cell(26,41);
                    var shopView = renderer.renderView(map,42+turn,shop[0],shop[1],80,45,24);
                    label(shopView,"OUTPOST WORKSHOP AND VEHICLE SHED / "+turn*90+" DEGREES / GAME-SCALE DETAIL");
                    frames.add(new SnapshotArtifact("outpost-workshop-turn-"+turn+".png",shopView));
                    int[] service = site.placement().cell(31,21);
                    var serviceView = renderer.renderView(map,42+turn,service[0],service[1],80,45,24);
                    label(serviceView,"OUTPOST UTILITY AND ARMORY / "+turn*90+" DEGREES / GAME-SCALE DETAIL");
                    frames.add(new SnapshotArtifact("outpost-service-turn-"+turn+".png",serviceView));
                }
            }
        for (int turn=0;turn<4;turn++) {
            var site=CompoundMissionMap.site(280,168,turn,true,0);
            verify(CompoundMissionMap.generate(280,168,42,TargetProfile.NEUTRAL,Fortification.STRONGHOLD,site),site);
            checked++;
        }
        // Drive the same selection seam used by createPlaceholder, including a marketless standalone mission.
        for (long seed=42;seed<45;seed++) {
            var map=BattleSetup.proceduralOperationMap(MissionType.ASSAULT,OperationTier.REINFORCED,RiskLevel.LOW,
                    TargetProfile.NEUTRAL,MapScale.LARGE,seed,PrecinctPlan.Sprawl.REMOTE,null);
            var site=CompoundMissionMap.select(280,168,seed);
            verify(map,site);
            if (seed==42) {
                var image=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);
                var g=image.createGraphics(); g.setColor(new Color(0x111c22)); g.fillRect(0,0,1920,1080);
                g.drawImage(renderer.render(map,seed,6),120,36,null);g.dispose();
                label(image,"REMOTE ASSAULT / ORDINARY MISSION MAP SELECTION / SEED 42");
                frames.add(new SnapshotArtifact("remote-assault.png",image));
                int[] centre=site.placement().cell(40,35);
                var detail=renderer.renderView(map,seed,centre[0],centre[1],160,90,12);
                label(detail,"REMOTE ASSAULT / SHARED CAMPAIGN COMPOUND");
                frames.add(new SnapshotArtifact("remote-assault-detail.png",detail));
            }
        }
        var story=MaintenanceOutpost.generate(288,160,42,TargetProfile.NEUTRAL,Fortification.PICKET);
        verifyAccess(story);
        InstalledVanillaData.install();
        try (var sim=BattleSetup.createPlaceholder(42,List.of(new ShuttleAssignment(ShuttleType.HERMES,3,4)),false,
                OperationTier.REINFORCED,RiskLevel.LOW,MissionType.ASSAULT,TargetProfile.NEUTRAL,
                FlybyRoster.EMPTY,FlybyRoster.EMPTY,PrecinctPlan.Sprawl.REMOTE)) {
            if (sim.getGrid().getWidth()!=280 || sim.getCommander(Faction.MARINE)==null
                    || sim.getCommander(Faction.DEFENDER)==null || sim.getObjectives().size()!=2)
                throw new IllegalStateException("Ordinary assault did not initialize on the shared compound");
        }
        System.out.println("[whole-compounds] "+checked+" rotated/variant/surface sites, 3 ordinary mission maps and campaign host passed");
        return frames;
    }

    private static void label(BufferedImage image,String title) {
        var g=image.createGraphics(); g.setColor(new Color(0x111c22));g.fillRect(0,0,1920,36);
        g.fillRect(0,1045,1920,35);g.setColor(new Color(0xdce9e8));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,21));
        g.drawString(title,24,26);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        g.drawString("Production Java2D / 1920 x 1080 / 100% / not a live OpenGL capture",24,1069);g.dispose();
    }

    private static void verify(MapResult map,CompoundMissionMap.Site site) {
        boolean[][] reachable=verifyAccess(map);
        for (var room : site.placement().buildings()) for (var door : room.doors())
            if (!reachable[door.x()][door.y()]) throw new IllegalStateException("Unreachable "+room.purpose()+" doorway");
        for (var edge : map.roadGraph.edges()) for (int i=0;i<edge.cellsX.length;i++)
            if (!map.grid.isWalkable(edge.cellsX[i],edge.cellsY[i]))
                throw new IllegalStateException("Road graph crosses blocked cell "+edge.cellsX[i]+","+edge.cellsY[i]);
        var clearance=VehicleClearance.erode(map.grid,1);
        boolean[][] driving=flood(map,clearance);
        // Just inside the three-cell shed door: bay threshold and approach must meet the host road.
        int[] bay=site.placement().cell(39,52);
        if (!driving[bay[0]][bay[1]]) throw new IllegalStateException("Vehicle shed apron cannot reach external access");
    }

    private static boolean[][] verifyAccess(MapResult map) {
        boolean[][] reachable=flood(map,null);
        if (!reachable[map.defenderSpawnX][map.defenderSpawnY]) throw new IllegalStateException("Defender spawn stranded");
        if (map.pointsOfInterest.size()<6) throw new IllegalStateException("Whole site did not publish its facilities");
        for (var poi : map.pointsOfInterest) if (!reachable[poi.interiorAnchorX][poi.interiorAnchorY])
            throw new IllegalStateException("Unreachable facility "+poi.kind);
        if (map.landingPads.size()!=4) throw new IllegalStateException("Missing arrival berths");
        for (var pad : map.landingPads) if (!pad.isClear(map.grid,map.topology) || !reachable[pad.centerX][pad.centerY])
            throw new IllegalStateException("Blocked arrival berth");
        return reachable;
    }

    private static boolean[][] flood(MapResult map,VehicleClearance clearance) {
        int width=map.grid.getWidth(),height=map.grid.getHeight();
        boolean[][] seen=new boolean[width][height];var queue=new ArrayDeque<int[]>();
        if (!map.grid.isWalkable(map.marineSpawnX,map.marineSpawnY)
                || clearance!=null && !clearance.isPassable(map.marineSpawnX,map.marineSpawnY))
            throw new IllegalStateException("Host spawn is obstructed");
        queue.add(new int[]{map.marineSpawnX,map.marineSpawnY});seen[map.marineSpawnX][map.marineSpawnY]=true;
        while (!queue.isEmpty()) {
            int[] at=queue.removeFirst();
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=at[0]+d[0],y=at[1]+d[1];
                if (!map.grid.inBounds(x,y) || seen[x][y] || !map.grid.canTraverseCellStep(at[0],at[1],x,y)
                        || clearance!=null && !clearance.isPassable(x,y)) continue;
                seen[x][y]=true;queue.add(new int[]{x,y});
            }
        }
        return seen;
    }
}
