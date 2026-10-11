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
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import java.util.ArrayDeque;
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
        int[][] close = {{36,19},{62,24},{58,25},{57,30},{52,25}};
        for (int seed = 8101; seed <= 8103; seed++) {
            List<MapResult> maps = List.of(ReliefCheckpoint.generate(112,64,seed,profile).map(),
                    CounterattackDepot.generate(seed,profile).map(),JunctionCompound.generate(seed,profile).map(),
                    LastLiftCompound.generate(seed,profile,12).map(),RelayNineCompound.generate(seed,profile).map());
            for (int index=0;index<maps.size();index++) {
                MapResult map=maps.get(index);
                verify(names[index],map);
                if(index==2 || index==4) verifyTerrain(names[index],map);
                if(seed!=8101) continue;
                try(var scene=new ShipDeckBattleScene(map,0)) {
                    int w=map.grid.getWidth(),h=map.grid.getHeight();
                    var overview=renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.fitting(0,0,w,h,1920,1080)),1920,1080);
                    // This empty north-west meadow exposed periodic one-pixel black seams
                    // at the overview's 8.4375 px/cell scale. Keep the production raster honest.
                    if(names[index].equals("relay-nine"))for(int py=10;py<100;py++)for(int px=100;px<300;px++)
                        if((overview.getRGB(px,py)&0xffffff)==0)
                            throw new IllegalStateException("Black preview seam in Relay Nine meadow at "+px+","+py);
                    frames.add(new SnapshotArtifact(names[index]+".png",overview));
                    if(index==2 || index==4) frames.add(new SnapshotArtifact(names[index]+"-terrain-approach.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(0,50,120,68,16)),1920,1080)));
                    if(names[index].equals("junction") || names[index].equals("relay-nine"))
                        frames.add(new SnapshotArtifact(names[index]+"-apron.png",renderer.renderHostPass(
                                scene.pass(ShipDeckBattleScene.DeckView.over(0,0,72,72,15)),1920,1080)));
                    if(index==2 || index==4) frames.add(new SnapshotArtifact(names[index]+"-loading-court.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.fitting(index==2?92:103,index==2?61:76,80,45,1920,1080)),1920,1080)));
                    if(names[index].equals("junction")) frames.add(new SnapshotArtifact("junction-east.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(135,26,60,68,16)),1920,1080)));
                    if(names[index].equals("relay-nine")) frames.add(new SnapshotArtifact("relay-nine-east.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(155,52,60,68,16)),1920,1080)));
                    frames.add(new SnapshotArtifact(names[index]+"-detail.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(close[index][0],close[index][1],60,34,32)),1920,1080)));
                }
                if(names[index].equals("relay-nine")) {
                    map.topology.setRoadSurface(null);
                    for(int y=0;y<map.grid.getHeight();y++) for(int x=0;x<map.grid.getWidth();x++) map.topology.setTag(x,y,Tag.ROAD_EDGE_BLEND,false);
                    try(var scene=new ShipDeckBattleScene(map,0)) {
                        frames.add(new SnapshotArtifact("relay-nine-east-hard.png",renderer.renderHostPass(
                                scene.pass(ShipDeckBattleScene.DeckView.over(155,52,60,68,16)),1920,1080)));
                    }
                }
            }
        }
        for (SurfacePalette surface : SurfacePalette.values()) if(surface!=SurfacePalette.VERDANT) {
            TargetProfile world = new TargetProfile(0,0,0,0,"",Set.of(),surface,SettlementLink.ROAD);
            verifyTerrain("junction",JunctionCompound.generate(8101,world).map());
            verifyTerrain("relay-nine",RelayNineCompound.generate(8101,world).map());
        }
        verify("relief-expanded",ReliefCheckpoint.generate(144,80,8101,profile).map());
        System.out.println("[story-roads] 15 production maps and expanded Relief: paved streets, clean markings/floors, clear berths and connected mission routes.");
        return frames;
    }
    /** Full-world connectivity and finalized terrain identity belong in opt-in evidence. */
    private static void verifyTerrain(String name, MapResult map) {
        var grid=map.grid; int w=grid.getWidth(),h=grid.getHeight();
        String path=name.equals("junction") ? TerrainCatalog.JUNCTION : TerrainCatalog.RELAY_NINE;
        TerrainField field=TerrainField.build(TerrainCatalog.plan(path,SurfacePalette.VERDANT));
        if(map.topology.terrainSurface()==null)throw new IllegalStateException(name+" has no terrain surface");
        boolean[] reached=new boolean[w*h];ArrayDeque<Integer> queue=new ArrayDeque<>();
        int start=grid.index(map.marineSpawnX,map.marineSpawnY);reached[start]=true;queue.add(start);
        while(!queue.isEmpty()) {
            int at=queue.remove(),x=at%w,y=at/w;
            for(Direction dir:Direction.ALL) if(grid.canTraverseCellStep(x,y,dir)) {
                int to=grid.index(x+dir.dx,y+dir.dy);
                if(!reached[to]){reached[to]=true;queue.add(to);}
            }
        }
        int solid=0,openNatural=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            boolean natural=grid.hasTag(x,y,CellTag.NATURAL_SOLID);
            if(natural!=field.blocked(x,y))throw new IllegalStateException(name+" terrain contour/grid mismatch");
            if(natural) {
                solid++;
                if(grid.isWalkable(x,y)||grid.isSeeThrough(x,y)||grid.getWallHp(x,y)!=0
                        ||map.topology.isWall(x,y)||map.topology.getBuildingId(x,y)!=0||grid.damageCell(x,y,100000))
                    throw new IllegalStateException(name+" terrain became destructible structure");
            } else if(grid.isWalkable(x,y)&&map.topology.getBuildingId(x,y)==0) {
                if(!reached[grid.index(x,y)])throw new IllegalStateException(name+" isolated outdoor ground at "+x+","+y);
                switch(map.topology.getGroundKind(x,y)) {case GRASS,STONE,SAND,SNOW -> openNatural++;default -> {}}
            }
        }
        if(solid==0)throw new IllegalStateException(name+" terrain plan was empty");
        for(var pad:map.landingPads)for(int y=pad.bottom()-2;y<=pad.top()+2;y++)for(int x=0;x<=pad.right()+2;x++)
            if(field.maximum(x,y)>=-.65)throw new IllegalStateException(name+" terrain intrudes on western boat approach");
        System.out.println("[story-terrain] "+name+": "+solid+" solid / "+openNatural+" open natural cells; outdoor ground connected, western approaches clear.");
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
