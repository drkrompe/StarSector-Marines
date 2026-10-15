package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ServiceYardTemplate;
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

/** Reusable campaign structures in ordinary parcel/compound consumers; production renderer, no story host. */
public final class ServiceYardSnapshotSuite implements SnapshotSuite {
    public String id() { return "service-yard-templates"; }
    public String label() { return "Campaign-derived buildings and industrial compounds"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> result = new ArrayList<>();
        for (int turn=0;turn<4;turn++) {
            BufferedImage board=board(); Graphics2D g=board.createGraphics();
            text(g,"REUSABLE WORKING YARDS / "+turn*90+" DEGREES",40,55,28);
            int panel=0;
            for (ServiceYardTemplate template : ServiceYardTemplate.values()) {
                GenContext c=open(34,34);
                template.place(c,(34-template.width(turn))/2,(34-template.height(turn))/2,turn);
                verify(c);
                text(g,template.name().replace('_',' '),40+panel*620,140,22);
                g.drawImage(renderer.render(map(c),42,18),25+panel*620,180,null);
                panel++;
            }
            text(g,"Whole footprints / four open gates / rotated vehicles and equipment / no campaign objectives",40,925,21);
            text(g,"Production Java2D renderer / 1920 x 1080 / 100% / no live OpenGL capture",40,1035,17);
            g.dispose(); result.add(new SnapshotArtifact("yards-turn-"+turn+".png",board));
        }
        GenContext c=compound(); verify(c);
        if (c.doodads.stream().noneMatch(d -> d.definitionId().contains("parked-")))
            throw new IllegalStateException("Generated compound did not adopt a working yard");
        BufferedImage board=board(); Graphics2D g=board.createGraphics();
        text(g,"GENERATED INDUSTRIAL COMPOUND / SHARED CAMPAIGN TEMPLATES",40,55,27);
        g.drawImage(renderer.render(map(c),42,17),30,110,null);
        text(g,"PRODUCTION + RECEIVING",1320,190,22);
        text(g,"Complete factory work groups",1320,230,19);
        text(g,"Freight bay and operator aisles",1320,260,19);
        text(g,"CAMPAIGN-DERIVED YARD",1320,390,22);
        text(g,"Vehicle bays and service equipment",1320,430,19);
        text(g,"Whole template fits a claimed parcel",1320,460,19);
        text(g,"SHARED ACCESS",1320,590,22);
        text(g,"Warehouse and working apron",1320,630,19);
        text(g,"Reserved vehicle lane stays clear",1320,660,19);
        text(g,"Production compound filler / constructed parcel fixture / Java2D / 100%",40,1050,17);
        g.dispose(); result.add(new SnapshotArtifact("industrial-compound.png",board));

        board=board(); g=board.createGraphics();
        text(g,"CAMPAIGN FREIGHT BUILDINGS / ORDINARY INDUSTRIAL LOTS",40,55,27);
        int panel=0;
        for (int[] size : new int[][]{{20,17},{19,19}}) {
            GenContext lot=open(28,28);
            new BuildingIndustrialFiller().fill(new BlockLeaf(4,4,3+size[0],3+size[1],false),lot);
            verify(lot);
            if (lot.pois.size()!=1 || lot.pois.get(0).right-lot.pois.get(0).left+1 != size[0])
                throw new IllegalStateException("Large freight building was not selected by ordinary filler");
            text(g,panel==0 ? "L DEPOT / STOCK + OPERATIONS" : "T TRANSFER WORKSHOP",90+panel*940,125,22);
            g.drawImage(renderer.render(map(lot),42,28),70+panel*940,180,null); panel++;
        }
        text(g,"Ordinary building selection / original authored footprint / real windows and accessible interior anchors",40,1035,18);
        g.dispose(); result.add(new SnapshotArtifact("freight-buildings.png",board));
        int adopted=0;
        for (long seed=42;seed<46;seed++) {
            BspCityGenerator generator=new BspCityGenerator();
            List<BlockLeaf> yards=new ArrayList<>();
            generator.register(new BlockFiller() {
                public BlockKind kind() { return BlockKind.INDUSTRIAL_YARD; }
                public void fill(BlockLeaf leaf,GenContext ctx) {
                    int before=ctx.doodads.size(); new IndustrialYardFiller().fill(leaf,ctx);
                    if (ctx.doodads.subList(before,ctx.doodads.size()).stream()
                            .anyMatch(d -> d.definitionId().contains("industrial-turbine-set"))) yards.add(leaf);
                }
            });
            MapResult city=generator.generate(240,168,seed);
            verifyAccess(city);
            if (!city.grid.isWalkable(city.marineSpawnX,city.marineSpawnY)
                    || !city.grid.isWalkable(city.defenderSpawnX,city.defenderSpawnY))
                throw new IllegalStateException("Generated template city lost a spawn");
            for (var edge : city.roadGraph.edges()) for (int i=0;i<edge.cellsX.length;i++)
                if (!city.grid.isWalkable(edge.cellsX[i],edge.cellsY[i]))
                    throw new IllegalStateException("Published road crosses occupied ground");
            System.out.println("[service-yard-templates] generated city "+seed+": "+yards.size()+" shared yards");
            if (adopted==0 && !yards.isEmpty()) {
                BlockLeaf yard=yards.get(0);
                BufferedImage cityFrame=renderer.renderView(city,seed,yard.centerX(),yard.centerY(),80,45,24);
                result.add(new SnapshotArtifact("generated-city.png",cityFrame));
            }
            adopted+=yards.size();
        }
        if (adopted==0) throw new IllegalStateException("Shared yards never reached the generated city consumer");
        for (TraversalAxis axis : TraversalAxis.values()) {
            MapResult conquest=new BspCityGenerator().generate(560,336,42,axis);
            verifyAccess(conquest);
            System.out.println("[service-yard-templates] Conquest "+axis+": connected floor and interior anchors");
        }
        return result;
    }

    private static GenContext compound() {
        GenContext c=open(72,54);
        BlockLeaf factory=new BlockLeaf(2,2,27,22,false), yard=new BlockLeaf(31,2,62,25,false),
                warehouse=new BlockLeaf(2,27,27,47,false);
        boolean[][] roads=new boolean[c.width][c.height], reserved=new boolean[c.width][c.height];
        for (int y=2;y<=47;y++) for (int x=28;x<=30;x++) roads[x][y]=true;
        for (int y=23;y<=26;y++) for (int x=2;x<=30;x++) roads[x][y]=true;
        for (int y=0;y<c.height;y++) for (int x=0;x<c.width;x++) if (roads[x][y]) {
            reserved[x][y]= x==29 || y==24 || y==25;
            c.topology.setGroundKind(x,y,CellTopology.GroundKind.STREET);
        }
        c.put(BspKeys.ROAD_CELLS,roads); c.put(BspKeys.ROAD_RESERVATION,reserved);
        var roles=new IdentityHashMap<BlockLeaf,Compound.Role>();
        roles.put(factory,Compound.Role.COMMAND); roles.put(yard,Compound.Role.BARRACKS); roles.put(warehouse,Compound.Role.ARMORY);
        new IndustrialCompoundFiller().fill(new Compound(BlockKind.INDUSTRIAL_COMPOUND,factory,
                new ArrayList<>(List.of(factory,yard,warehouse)),roles,null),c);
        for (int y=0;y<c.height;y++) for (int x=0;x<c.width;x++)
            if (reserved[x][y] && !c.grid.isWalkable(x,y)) throw new IllegalStateException("Template blocked vehicle road");
        return c;
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
    private static BufferedImage board() {
        BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); g.setColor(new Color(0x111c22)); g.fillRect(0,0,1920,1080); g.dispose(); return image;
    }
    private static void text(Graphics2D g,String text,int x,int y,int size) {
        g.setColor(new Color(0xdce9e8)); g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,size)); g.drawString(text,x,y);
    }
}
