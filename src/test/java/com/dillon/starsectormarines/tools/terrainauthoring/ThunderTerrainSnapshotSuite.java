package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** The authored map and real chassis clearance, through production rendering at 1920x1080. */
public final class ThunderTerrainSnapshotSuite implements SnapshotSuite {
    public String id() { return "thunder-terrain"; }
    public String label() { return "Thunder Run terrain and freight clearance"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer=new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),context.modRoot());
        List<SnapshotArtifact> frames=new ArrayList<>();
        for(var surface:SurfacePalette.values()) for(int seed=9301;seed<=(surface==SurfacePalette.VERDANT?9303:9301);seed++) {
            var profile=new TargetProfile(0,0,0,0,"",Set.of(),surface,SettlementLink.ROAD);
            MapResult map=ThunderRunCompound.generate(seed,profile).map();
            verify(map);
            if(surface!=SurfacePalette.VERDANT || seed!=9301)continue;
            try(var scene=new ShipDeckBattleScene(map,0)) {
                frames.add(new SnapshotArtifact("overview.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.fitting(0,0,320,144,1920,1080)),1920,1080)));
                frames.add(new SnapshotArtifact("apron.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.over(0,27,65,96,10)),1920,1080)));
                frames.add(new SnapshotArtifact("staging.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.over(0,18,160,108,10)),1920,1080)));
                frames.add(new SnapshotArtifact("crossing.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.over(85,18,180,108,10)),1920,1080)));
                frames.add(new SnapshotArtifact("dispatch-building.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.fitting(65,40,48,27,1920,1080)),1920,1080)));
                frames.add(new SnapshotArtifact("crossing-building.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.fitting(171,84,48,27,1920,1080)),1920,1080)));
                frames.add(new SnapshotArtifact("checkpoint.png",renderer.renderHostPass(scene.pass(
                        ShipDeckBattleScene.DeckView.over(205,18,115,108,10)),1920,1080)));
            }
        }
        System.out.println("[thunder-terrain] Six production maps: freight chassis fits, checkpoint cannot be bypassed by cargo, infantry can flank.");
        return frames;
    }
    private static void verify(MapResult map) {
        var grid=map.grid;
        int w=grid.getWidth(),h=grid.getHeight();
        if(map.landingPads.size()!=6)throw new IllegalStateException("Freight apron lost berths");
        for(LandingPad pad:map.landingPads) {
            if(pad.purpose!=LandingPad.Purpose.FREIGHT_APRON || pad.halfWidth!=8 || !pad.isClear(grid,map.topology))
                throw new IllegalStateException("Freight stand is not clear and elongated");
            if(pad.bottom()<=85 && pad.top()>=65)throw new IllegalStateException("Stand overlaps convoy road");
        }
        TerrainField field=TerrainField.build(TerrainCatalog.plan(TerrainCatalog.THUNDER_RUN,SurfacePalette.VERDANT));
        if(map.topology.terrainSurface()==null)throw new IllegalStateException("Thunder Run has no terrain surface");
        boolean[] reached=new boolean[w*h];ArrayDeque<Integer> queue=new ArrayDeque<>();
        int start=grid.index(21,75);reached[start]=true;queue.add(start);
        while(!queue.isEmpty()) {
            int at=queue.remove(),x=at%w,y=at/w;
            for(Direction dir:Direction.ALL)if(grid.canTraverseCellStep(x,y,dir)) {
                int to=grid.index(x+dir.dx,y+dir.dy);
                if(!reached[to]){reached[to]=true;queue.add(to);}
            }
        }
        int solid=0,openNatural=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            boolean natural=grid.hasTag(x,y,CellTag.NATURAL_SOLID);
            if(natural!=field.blocked(x,y))throw new IllegalStateException("Terrain field/grid mismatch");
            if(natural) {
                solid++;
                if(grid.isWalkable(x,y)||grid.isSeeThrough(x,y)||grid.getWallHp(x,y)!=0
                        ||map.topology.isWall(x,y)||map.topology.getBuildingId(x,y)!=0||grid.damageCell(x,y,100000))
                    throw new IllegalStateException("Terrain lost natural obstruction identity");
            } else if(grid.isWalkable(x,y)&&map.topology.getBuildingId(x,y)==0) {
                if(!reached[grid.index(x,y)])throw new IllegalStateException("Isolated outdoor ground at "+x+","+y);
                switch(map.topology.getGroundKind(x,y)){case GRASS,STONE,SAND,SNOW -> openNatural++;default -> {}}
            }
        }
        if(solid==0)throw new IllegalStateException("Empty terrain plan");
        for(int y=10;y<=135;y++)for(int x=0;x<=38;x++)
            if(field.maximum(x,y)>=-.65)throw new IllegalStateException("Terrain intrudes on staging/boat approaches");
        // Each side of the valley keeps a pedestrian route behind the roadside facilities.
        for(int[] flank:new int[][]{{39,18,263,28},{39,116,263,116}}) {
            boolean[] side=new boolean[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)side[grid.index(x,y)]=flank[1]<75 ? y<65 : y>85;
            if(Paths.isEmpty(GridPathfinder.findPath(grid,flank[0],flank[1],flank[2],flank[3],null,side)))
                throw new IllegalStateException("Infantry side route disconnected at y="+flank[1]);
        }
        System.out.println("[thunder-terrain] "+solid+" solid / "+openNatural+" open natural cells; connected outdoor ground and north/south infantry approaches.");
        for(VehicleType type:List.of(VehicleType.CARGO_CARRIER,VehicleType.ESCORT_APC,VehicleType.BREAKTHROUGH_TANK)) {
            int radius=VehicleClearance.radiusForWidth(type.visualWidthCells);
            int exit=grid.getWidth()-1-radius; // Last center position with the whole hull still on-map.
            var clearance=VehicleClearance.erode(grid,radius);
            boolean[] mask=new boolean[grid.getWidth()*grid.getHeight()];
            for(int y=0;y<grid.getHeight();y++)for(int x=0;x<grid.getWidth();x++)mask[grid.index(x,y)]=clearance.isPassable(x,y);
            if(Paths.isEmpty(GridPathfinder.findPath(grid,21,75,exit,75,null,mask)))
                throw new IllegalStateException(type+" cannot traverse freight road");
            for(int y=68;y<=82;y++)for(int x=294;x<=311;x++)mask[grid.index(x,y)]=false;
            if(!Paths.isEmpty(GridPathfinder.findPath(grid,21,75,exit,75,null,mask)))
                throw new IllegalStateException(type+" can bypass the intact checkpoint");
        }
        // The same closed road band must still admit an infantry route round the guns.
        boolean[] infantry=new boolean[grid.getWidth()*grid.getHeight()];
        Arrays.fill(infantry,true);
        for(int y=68;y<=82;y++)for(int x=294;x<=311;x++)infantry[grid.index(x,y)]=false;
        if(Paths.isEmpty(GridPathfinder.findPath(grid,21,75,318,75,null,infantry)))
            throw new IllegalStateException("Infantry lost the checkpoint flank");
    }
}
