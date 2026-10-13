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
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
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
        int[][] close = {{68,19},{62,24},{58,25},{57,30},{52,25}};
        for (int seed = 8101; seed <= 8103; seed++) {
            List<MapResult> maps = List.of(ReliefCheckpoint.generate(ReliefCheckpoint.WIDTH,ReliefCheckpoint.HEIGHT,seed,profile).map(),
                    CounterattackDepot.generate(seed,profile).map(),JunctionCompound.generate(seed,profile).map(),
                    LastLiftCompound.generate(seed,profile,12).map(),RelayNineCompound.generate(seed,profile).map());
            for (int index=0;index<maps.size();index++) {
                MapResult map=maps.get(index);
                verify(names[index],map);
                if(index!=3) verifyTerrain(names[index],map);
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
                    if(names[index].equals("relief")) frames.add(new SnapshotArtifact("relief-west-yard.png",renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.fitting(8,12,80,45,1920,1080)),1920,1080)));
                    if(index<2) {
                        frames.add(new SnapshotArtifact(names[index]+"-apron.png",renderer.renderHostPass(
                                scene.pass(ShipDeckBattleScene.DeckView.fitting(0,10,64,36,1920,1080)),1920,1080)));
                        frames.add(new SnapshotArtifact(names[index]+"-yards.png",renderer.renderHostPass(
                                scene.pass(ShipDeckBattleScene.DeckView.fitting(index==0?85:80,index==0?34:52,64,36,1920,1080)),1920,1080)));
                    }
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
            verifyTerrain("relief",ReliefCheckpoint.generate(ReliefCheckpoint.WIDTH,ReliefCheckpoint.HEIGHT,8101,world).map());
            verifyTerrain("counterattack",CounterattackDepot.generate(8101,world).map());
            verifyTerrain("junction",JunctionCompound.generate(8101,world).map());
            verifyTerrain("relay-nine",RelayNineCompound.generate(8101,world).map());
        }
        MapResult expanded = ReliefCheckpoint.generate(176,80,8101,profile).map();
        verify("relief-expanded",expanded);
        verifyTerrain("relief-expanded",expanded);
        System.out.println("[story-roads] 15 production maps and expanded Relief: paved streets, clean markings/floors, clear berths and connected mission routes.");
        return frames;
    }
    /** Full-world connectivity and finalized terrain identity belong in opt-in evidence. */
    private static void verifyTerrain(String name, MapResult map) {
        var grid=map.grid; int w=grid.getWidth(),h=grid.getHeight();
        String path=switch(name) {
            case "relief", "relief-expanded" -> TerrainCatalog.RELIEF;
            case "counterattack" -> TerrainCatalog.COUNTERATTACK;
            case "junction" -> TerrainCatalog.JUNCTION;
            default -> TerrainCatalog.RELAY_NINE;
        };
        TerrainField field=TerrainField.build(path.equals(TerrainCatalog.RELIEF)
                ? TerrainCatalog.relief(w,h,SurfacePalette.VERDANT) : TerrainCatalog.plan(path,SurfacePalette.VERDANT));
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
        boolean[] crowns = new boolean[w*h];
        List<NaturalPropShape> shapes = new ArrayList<>(List.of(TreeCanopy.values()));
        shapes.addAll(List.of(MineralFormation.values()));
        for (var doodad : map.doodads) for (var canopy : shapes) {
            var def = TileRegistry.installed().doodad(canopy.id());
            if (!doodad.sheetPath.equals(def.sheetPath) || doodad.tile.col != def.col || doodad.tile.row != def.row) continue;
            for (int y = 0; y < doodad.footprintCellsY; y++) for (int x = 0; x < doodad.footprintCellsX; x++)
                if (canopy.blocks(x, y, doodad.quarterTurns)) crowns[(doodad.cellY+y)*w+doodad.cellX+x] = true;
        }
        int solid=0,openNatural=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            boolean natural=grid.hasTag(x,y,CellTag.NATURAL_SOLID);
            if(natural!=(field.blocked(x,y)||crowns[y*w+x]))throw new IllegalStateException(name+" terrain contour/grid mismatch at "+x+","+y+"; natural="+natural+", terrain="+field.blocked(x,y)+", canopy="+crowns[y*w+x]);
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
    /** Real equipment footprints must agree with the picture after all generation stages. */
    private static void verifyServiceYards(String name, MapResult map) {
        int equipment = 0;
        for(var prop:map.doodads) {
            if(!map.topology.isFixture(prop.cellX,prop.cellY)
                    || map.grid.hasTag(prop.cellX,prop.cellY,CellTag.NATURAL_SOLID)
                    || map.topology.getBuildingId(prop.cellX,prop.cellY)!=0) continue;
            equipment++;
            for(int y=prop.cellY;y<prop.cellY+prop.footprintCellsY;y++)
                for(int x=prop.cellX;x<prop.cellX+prop.footprintCellsX;x++) {
                    if(map.grid.isWalkable(x,y)||!map.topology.isFixture(x,y)
                            ||map.topology.isWall(x,y)||map.topology.getBuildingId(x,y)!=0
                            ||map.topology.getGroundKind(x,y)==GroundKind.STREET)
                        throw new IllegalStateException(name+" yard fixture footprint disagrees at "+x+","+y);
                    for(int yy=y-2;yy<=y+2;yy++)for(int xx=x-2;xx<=x+2;xx++)
                        if(map.grid.inBounds(xx,yy)&&map.grid.isDoorway(xx,yy))
                            throw new IllegalStateException(name+" yard fixture crowds a doorway");
                }
        }
        if(equipment==0)throw new IllegalStateException(name+" working yards disappeared");
    }
    private static void verify(String name,MapResult map) {
        if(name.equals("junction") || name.equals("relay-nine")) for(var edge:map.roadGraph.edges()) for(int i=0;i<edge.cellsX.length;i++) {
            int x=edge.cellsX[i],y=edge.cellsY[i];
            var kind=map.topology.getGroundKind(x,y);
            if(!map.grid.isWalkable(x,y) || (kind!=GroundKind.STREET && kind!=GroundKind.STRIPED))
                throw new IllegalStateException(name+" graph leaves the road at "+x+","+y);
        }
        if(!name.equals("last-lift")) verifyServiceYards(name,map);
        int streets=0,stripes=0;
        for(int y=0;y<map.grid.getHeight();y++) for(int x=0;x<map.grid.getWidth();x++) {
            var kind=map.topology.getGroundKind(x,y);
            String surface=map.topology.surfaceId(map.topology.getSurface(x,y));
            boolean paved=surface!=null && surface.startsWith("pavement.");
            if(kind==GroundKind.STREET && !map.topology.isWall(x,y)) {
                streets++;
                if(!paved) throw new IllegalStateException(name+" has an old road tile at "+x+","+y);
            } else if(!name.equals("last-lift") && kind==GroundKind.COURTYARD && !map.topology.isWall(x,y)) {
                if(surface==null || !surface.startsWith("pavement.concrete."))
                    throw new IllegalStateException(name+" yard lost its concrete surface");
            } else if(paved) throw new IllegalStateException(name+" pavement covers a room/marking at "+x+","+y);
            if(kind==GroundKind.STRIPED) stripes++;
        }
        if(streets==0 || stripes==0) throw new IllegalStateException(name+" lost its roads or landing markings");
        for(var pad:map.landingPads) if(!pad.isClear(map.grid,map.topology))
            throw new IllegalStateException(name+" has a blocked landing pad");
    }
}
