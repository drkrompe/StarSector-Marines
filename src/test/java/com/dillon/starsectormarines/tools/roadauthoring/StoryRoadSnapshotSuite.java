package com.dillon.starsectormarines.tools.roadauthoring;

import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.LastLiftCompound;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opt-in production-map checks and renders, never a parallel map builder. */
public final class StoryRoadSnapshotSuite implements SnapshotSuite {
    public String id() { return "story-roads"; }
    public String label() { return "Story mission pavement"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),context.modRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        TargetProfile profile = new TargetProfile(0,0,0,0,"",Set.of(),SurfacePalette.VERDANT,SettlementLink.ROAD);
        String[] names = {"relief","counterattack","junction","last-lift","relay-nine"};
        int[][] close = {{36,19},{62,24},{63,25},{57,30},{52,25}};
        for (int seed = 8101; seed <= 8103; seed++) {
            List<MapResult> maps = List.of(ReliefCheckpoint.generate(112,64,seed,profile).map(),
                    CounterattackDepot.generate(seed,profile).map(),JunctionCompound.generate(seed,profile).map(),
                    LastLiftCompound.generate(seed,profile,12).map(),RelayNineCompound.generate(seed,profile).map());
            for (int index=0;index<maps.size();index++) {
                MapResult map=maps.get(index);
                verify(names[index],map);
                if(seed!=8101) continue;
                try(var scene=new ShipDeckBattleScene(map,0)) {
                    int w=map.grid.getWidth(),h=map.grid.getHeight();
                    frames.add(new SnapshotArtifact(names[index]+".png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.fitting(0,0,w,h,1920,1080)),1920,1080)));
                    if(names[index].equals("junction")) frames.add(new SnapshotArtifact("junction-east.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(135,26,60,68,16)),1920,1080)));
                    if(names[index].equals("relay-nine")) frames.add(new SnapshotArtifact("relay-nine-east.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(155,52,60,68,16)),1920,1080)));
                    frames.add(new SnapshotArtifact(names[index]+"-detail.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(close[index][0],close[index][1],60,34,32)),1920,1080)));
                }
                if(names[index].equals("relay-nine")) {
                    for(int y=0;y<map.grid.getHeight();y++) for(int x=0;x<map.grid.getWidth();x++) map.topology.setTag(x,y,Tag.ROAD_EDGE_BLEND,false);
                    try(var scene=new ShipDeckBattleScene(map,0)) {
                        frames.add(new SnapshotArtifact("relay-nine-east-hard.png",renderer.renderHostPass(
                                scene.pass(ShipDeckBattleScene.DeckView.over(155,52,60,68,16)),1920,1080)));
                    }
                }
            }
        }
        verify("relief-expanded",ReliefCheckpoint.generate(144,80,8101,profile).map());
        System.out.println("[story-roads] 15 production maps and expanded Relief: paved streets, clean markings/floors, clear berths and connected mission routes.");
        return frames;
    }
    private static void verify(String name,MapResult map) {
        if(name.equals("junction") || name.equals("relay-nine")) for(var edge:map.roadGraph.edges()) for(int i=0;i<edge.cellsX.length;i++) {
            int x=edge.cellsX[i],y=edge.cellsY[i];
            var kind=map.topology.getGroundKind(x,y);
            if(!map.grid.isWalkable(x,y) || (kind!=GroundKind.STREET && kind!=GroundKind.STRIPED))
                throw new IllegalStateException(name+" graph leaves the road at "+x+","+y);
        }
        int streets=0,stripes=0;
        for(int y=0;y<map.grid.getHeight();y++) for(int x=0;x<map.grid.getWidth();x++) {
            var kind=map.topology.getGroundKind(x,y);
            String surface=map.topology.surfaceId(map.topology.getSurface(x,y));
            boolean paved=surface!=null && surface.startsWith("pavement.");
            if(kind==GroundKind.STREET && !map.topology.isWall(x,y)) {
                streets++;
                if(!paved) throw new IllegalStateException(name+" has an old road tile at "+x+","+y);
            } else if((name.equals("junction") || name.equals("relay-nine")) && kind==GroundKind.COURTYARD && !map.topology.isWall(x,y)) {
                if(surface==null || !surface.startsWith("pavement.concrete."))
                    throw new IllegalStateException("Junction yard lost its concrete surface");
            } else if(paved) throw new IllegalStateException(name+" pavement covers a room/marking at "+x+","+y);
            if(kind==GroundKind.STRIPED) stripes++;
        }
        if(streets==0 || stripes==0) throw new IllegalStateException(name+" lost its roads or landing markings");
        for(var pad:map.landingPads) if(!pad.isClear(map.grid,map.topology))
            throw new IllegalStateException(name+" has a blocked landing pad");
    }
}
