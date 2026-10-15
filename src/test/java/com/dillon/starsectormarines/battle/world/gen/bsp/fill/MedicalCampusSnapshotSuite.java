package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.MedicalServiceCourt;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InteriorAnchorFitStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;

/** Production medical compounds, independent yard poses and actual city adoption. */
public final class MedicalCampusSnapshotSuite implements SnapshotSuite {
    public String id() { return "medical-campus"; }
    public String label() { return "Medical compounds and service courts"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer=new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> result=new ArrayList<>();
        for (int turn=0;turn<4;turn++) {
            GenContext c=open(40,30);
            RoomPose pose=new RoomPose(turn,false);
            BlockLeaf parcel=new BlockLeaf(8,7,7+pose.posedWidth(16,14),6+pose.posedHeight(16,14),false);
            MedicalServiceCourt.stamp(c,parcel,turn); verify(c);
            result.add(new SnapshotArtifact("court-turn-"+turn+".png",
                    frame(renderer,map(c),parcel.centerX(),parcel.centerY(),"MEDICAL SERVICE COURT / "+turn*90+" DEGREES")));
        }
        for (boolean large:new boolean[]{false,true}) {
            GenContext c=campus(large); verify(c);
            if (c.doodads.stream().noneMatch(d -> "sickbay-clinic.cabinet".equals(d.definitionId())))
                throw new IllegalStateException("Medical support has no clinical storage");
            result.add(new SnapshotArtifact(large ? "campus-expanded.png" : "campus-compact.png",
                    frame(renderer,map(c),large?28:23,large?24:19,large?"EXPANDED MEDICAL CAMPUS":"COMPACT MEDICAL CAMPUS")));
        }
        int adopted=0, bestCourtArea=0;
        MapResult reviewCity=null;
        Compound reviewCampus=null;
        long reviewSeed=0;
        for (long seed=0;seed<8;seed++) {
            BspCityGenerator generator=new BspCityGenerator();
            MapResult city=generator.generate(240,168,seed);
            verifyAccess(city);
            verifyRoads(city);
            for (Compound campus:generator.getLastCompounds()) if (campus.kind==BlockKind.MEDICAL_CAMPUS) {
                adopted++;
                int courtArea=campus.members.stream().filter(m -> m!=campus.seed).mapToInt(BlockLeaf::area).min().orElse(0);
                if (courtArea>bestCourtArea) {
                    bestCourtArea=courtArea; reviewCity=city; reviewCampus=campus; reviewSeed=seed;
                }
            }
        }
        if (adopted==0) throw new IllegalStateException("No medical campuses reached ordinary cities");
        result.add(new SnapshotArtifact("generated-medical-campus.png",frame(renderer,reviewCity,
                (reviewCampus.left+reviewCampus.right)/2,(reviewCampus.top+reviewCampus.bottom)/2,
                "MEDICAL CAMPUS / GENERATED CITY / SEED "+reviewSeed)));
        // Keep the existing small-city adoption measurement out of the unit-test task.
        int smallCampuses=0;
        for (long seed=0;seed<60;seed++) {
            BspCityGenerator generator=new BspCityGenerator();
            generator.generate(80,80,seed);
            for (Compound campus:generator.getLastCompounds()) if (campus.kind==BlockKind.MEDICAL_CAMPUS) smallCampuses++;
        }
        if (smallCampuses<2) throw new IllegalStateException("Small-city medical campus adoption regressed");
        for (TraversalAxis axis:TraversalAxis.values()) {
            MapResult city=new BspCityGenerator().generate(560,336,42,axis);
            verifyAccess(city);
        }
        System.out.println("[medical-campus] four yard poses, two compound sizes, "+adopted+" ordinary city campuses, "+smallCampuses+" small-city campuses, both Conquest axes verified");
        return result;
    }

    private static BufferedImage frame(HeadlessBattleMapRenderer renderer, MapResult map, int x,int y,String title) {
        BufferedImage image=renderer.renderView(map,42,x,y,80,45,24);
        Graphics2D g=image.createGraphics();
        g.setColor(new Color(0x111c22)); g.fillRect(0,0,1920,72); g.fillRect(0,1025,1920,55);
        text(g,title,32,46,25);
        text(g,"Production Java2D / 1920 x 1080 / receiving, clear transfer bay, utilities and supply annex / no live capture",32,1060,18);
        g.dispose(); return image;
    }

    private static GenContext campus(boolean large) {
        GenContext c=open(64,48);
        BlockLeaf clinic=large ? new BlockLeaf(5,5,26,25,false) : new BlockLeaf(5,5,19,16,false);
        BlockLeaf support=large ? new BlockLeaf(30,5,47,21,false) : new BlockLeaf(23,5,36,18,false);
        BlockLeaf court=large ? new BlockLeaf(30,25,45,38,false) : new BlockLeaf(23,22,34,31,false);
        boolean[][] roads=new boolean[c.width][c.height],reserved=new boolean[c.width][c.height];
        for (int y=3;y<=court.bottom;y++) for (int x=clinic.right+1;x<support.left;x++) {
            roads[x][y]=true; reserved[x][y]=x==clinic.right+2;
        }
        for (int x=support.left;x<=support.right+2;x++) for (int y=support.bottom+1;y<court.top;y++) {
            roads[x][y]=true; reserved[x][y]=y==support.bottom+2;
        }
        for (int y=0;y<c.height;y++) for (int x=0;x<c.width;x++) if (roads[x][y]) c.topology.setGroundKind(x,y,CellTopology.GroundKind.STREET);
        c.put(BspKeys.ROAD_CELLS,roads); c.put(BspKeys.ROAD_RESERVATION,reserved);
        new MedicalCampusFiller().fill(new Compound(BlockKind.MEDICAL_CAMPUS,clinic,
                new ArrayList<>(List.of(clinic,support,court)),new IdentityHashMap<>(),null),c);
        for (int y=0;y<c.height;y++) for (int x=0;x<c.width;x++)
            if (reserved[x][y] && (!c.grid.isWalkable(x,y) || c.topology.getGroundKind(x,y)!=CellTopology.GroundKind.STREET))
                throw new IllegalStateException("Medical court changed a reserved road");
        return c;
    }

    private static void verifyRoads(MapResult map) {
        for (var edge:map.roadGraph.edges()) for (int i=0;i<edge.cellsX.length;i++)
            if (!map.grid.isWalkable(edge.cellsX[i],edge.cellsY[i])) throw new IllegalStateException("Medical city blocked a road at "+edge.cellsX[i]+","+edge.cellsY[i]);
        for (var pad:map.landingPads) if (!pad.isClear(map.grid,map.topology)) throw new IllegalStateException("Medical city blocked a berth");
    }

    private static GenContext open(int width,int height) {
        NavigationGrid grid=new NavigationGrid(width,height); CellTopology topology=new CellTopology(width,height);
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            grid.setWalkableFloor(x,y); topology.setGroundKind(x,y,CellTopology.GroundKind.STONE);
        }
        return new GenContext(grid,topology,new Random(42),width,height,42);
    }
    private static MapResult map(GenContext c) { return new MapResult(c.grid,c.topology,0,0,0,0,c.pois,c.doodads); }
    private static void verify(GenContext c) {
        new FinalizeStage().run(c);
        new InteriorAnchorFitStage().run(c);
        verifyAccess(map(c));
    }
    private static void verifyAccess(MapResult c) {
        int width=c.grid.getWidth(),height=c.grid.getHeight();
        boolean[][] seen=new boolean[width][height]; ArrayDeque<int[]> queue=new ArrayDeque<>();
        seen[c.marineSpawnX][c.marineSpawnY]=true; queue.add(new int[]{c.marineSpawnX,c.marineSpawnY});
        while (!queue.isEmpty()) {
            int[] at=queue.removeFirst();
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=at[0]+d[0],y=at[1]+d[1];
                if (c.grid.inBounds(x,y) && !seen[x][y] && c.grid.canTraverseCellStep(at[0],at[1],x,y)) {
                    seen[x][y]=true; queue.add(new int[]{x,y});
                }
            }
        }
        for (int y=0;y<height;y++) for (int x=0;x<width;x++)
            if (c.grid.isWalkable(x,y) && !seen[x][y]) throw new IllegalStateException("Template stranded floor at "+x+","+y);
        for (var poi : c.pointsOfInterest) {
            // Solid scenery has no room; InteriorAnchorFit deliberately retains its exterior fallback.
            boolean hasInterior=false;
            for (int y=Math.max(0,poi.top);y<=Math.min(height-1,poi.bottom);y++)
                for (int x=Math.max(0,poi.left);x<=Math.min(width-1,poi.right);x++)
                    hasInterior |= c.grid.isWalkable(x,y) && !c.grid.isDoorway(x,y);
            if (hasInterior && (!c.grid.inBounds(poi.interiorAnchorX,poi.interiorAnchorY)
                    || !seen[poi.interiorAnchorX][poi.interiorAnchorY]))
                throw new IllegalStateException("Template interior anchor is inaccessible: "+poi.kind+" at "+poi.interiorAnchorX+","+poi.interiorAnchorY
                        +" bounds "+poi.left+","+poi.top+".."+poi.right+","+poi.bottom);
        }
    }
    private static void text(Graphics2D g,String text,int x,int y,int size) {
        g.setColor(new Color(0xdce9e8)); g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,size)); g.drawString(text,x,y);
    }
}
