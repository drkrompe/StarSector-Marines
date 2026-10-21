package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/** Assembled furniture through real structural damage, placement rotations and the production renderer. */
public final class ComposableDamageSnapshotSuite implements SnapshotSuite {
    public String id() { return "composable-damage"; }
    public String label() { return "Furniture and factory machinery damage states"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        List<SnapshotArtifact> images = new ArrayList<>();
        for (String family : List.of("consoles", "storage", "clinic", "factory", "armory", "utility", "command", "power-plant")) for (int turn=0;turn<4;turn++)
            images.add(new SnapshotArtifact(family+"-turn-"+turn+".png",board(context,family,turn)));
        return images;
    }
    private static BufferedImage board(SnapshotContext context,String family,int turn) {
        BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(0x111c23));g.fillRect(0,0,1920,1080);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        text(g,family.toUpperCase()+" / INTACT, DAMAGED, DESTROYED",35,65,30);
        text(g,"Actual structural damage / rotation "+(turn*90)+" degrees / original floors remain",35,105,20);
        for(int state=0;state<3;state++)try(Fixture f=new Fixture()) {
            if(family.equals("power-plant")) {
                f.place("power-plant.turbine",3,11,0,turn);
                f.place("power-plant.coolant",10,11,0,turn);
                f.place("ship-engineering.switchgear",3,5,0,turn);
                f.place("ship-engineering.control",10,5,0,turn);
            } else if(family.equals("command")) {
                f.commandPost(turn);
            } else if(family.equals("consoles")) {
                for(int shape=0;shape<3;shape++)for(var p:ConsoleBankLayout.resolve(ConsoleBankSnapshotSuite.stroke(shape),shape==2,shape!=2)) {
                    int x=p.cell().x()+(shape==1?8:shape==2?4:0),y=7-p.cell().y()+(shape<2?10:0);
                    f.place(p.id(),x,y,Math.floorMod(-p.clockwiseTurns(),4),turn);
                }
            } else if(family.equals("storage")) {
                f.place("main-stores.rack",2,11,0,turn);
                f.place("main-stores.cartons",2,11,0,turn);f.place("main-stores.cases",2,13,0,turn);
                f.place("main-stores.drums",2,15,0,turn);f.place("main-stores.frame",2,11,0,turn);
                for(int[] p:new int[][]{{9,12},{11,12},{9,14}}) {
                    f.place("main-stores.pallet",p[0],p[1],0,turn);f.place("main-stores.cases",p[0],p[1],0,turn);
                }
                f.place("main-stores.tally",2,5,0,turn);f.place("main-stores.packing",9,5,0,turn);

            } else if(family.equals("armory")) {
                f.place("armory-workshop.counter",2,5,0,turn);
                f.place("armory-workshop.counter",4,5,0,turn);
                f.place("armory-workshop.ready-locker",2,9,0,turn);
                f.place("armory-workshop.bench",7,9,0,turn);
                f.place("armory-workshop.bench-tools",7,9,0,turn);
                f.place("armory-workshop.ammo",12,9,0,turn);
                for(int x=2;x<=11;x+=3) {
                    f.place("armory-workshop.rack",x,14,0,turn);
                    f.place("armory-workshop.rifles",x,14,0,turn);
                    f.place("armory-workshop.rack-frame",x,14,0,turn);
                }
            } else if(family.equals("utility")) {
                f.place("ship-engineering.generator",5,11,0,turn);
                f.place("ship-engineering.switchgear",3,5,0,turn);
                f.place("ship-engineering.control",10,5,0,turn);            } else if(family.equals("factory")) {
                f.place("factory-machines.fabricator",3,12,0,turn);
                f.place("factory-machines.conveyor",6,13,0,turn);
                f.place("factory-machines.conveyor",7,13,0,turn);
                f.place("factory-machines.assembly",10,12,0,turn);
                f.place("factory-machines.coolant",3,5,0,turn);
                f.place("main-stores.packing",10,5,0,turn);
                f.place("mess-galley.stool",11,3,0,turn);
            } else {
                f.place("sickbay-clinic.bed",2,12,0,turn);f.place("sickbay-clinic.linen-high",2,12,0,turn);
                f.place("sickbay-clinic.monitor",2,15,0,turn);
                f.place("sickbay-clinic.bed",2,4,0,turn);f.place("sickbay-clinic.linen-low",2,4,0,turn);
                f.place("sickbay-clinic.exam",7,14,0,turn);f.place("sickbay-clinic.monitor",9,14,0,turn);
                f.place("sickbay-clinic.trolley",7,12,0,turn);f.place("sickbay-clinic.screen",12,12,0,turn);
                f.place("sickbay-clinic.station",2,8,0,turn);f.place("sickbay-clinic.sink",12,8,0,turn);
                for(int x=7;x<=11;x+=2) f.place("sickbay-clinic.cabinet",x,5,0,turn);
                f.place("mess-galley.stool",7,8,0,turn);
            }
            if(state>0)f.editor.damageWallsInRadius(10,10,30,state==1?35:100,null);
            for(Doodad d:f.props.getDoodads()) {
                if(state>0&&!d.definitionId().endsWith(state==1?"-damaged":"-destroyed"))
                    throw new IllegalStateException("Missed state: "+d.definitionId());
                if(state==2)for(int y=d.cellY;y<d.cellY+d.footprintCellsY;y++)for(int x=d.cellX;x<d.cellX+d.footprintCellsX;x++)
                    if(!f.grid.isWalkable(x,y)||f.grid.blocksStructuralLineOfSight(x,y)||f.topology.isFixture(x,y))throw new IllegalStateException("Wreck blocks floor or sight");
                if(state<2 && (d.definitionId().startsWith("factory-machines.") || d.definitionId().startsWith("power-plant.")) && !d.definitionId().startsWith("factory-machines.conveyor"))
                    for(int y=d.cellY;y<d.cellY+d.footprintCellsY;y++)for(int x=d.cellX;x<d.cellX+d.footprintCellsX;x++)
                        if(f.grid.isWalkable(x,y)||f.grid.isSeeThrough(x,y))throw new IllegalStateException("Standing machine lost obstruction");
            }
            MapResult map=new MapResult(f.grid,f.topology,0,0,19,19,List.of(),f.props.getDoodads());
            int x=20+state*640;
            text(g,List.of("INTACT","DAMAGED / 35 STRUCTURE","DESTROYED / 100 STRUCTURE").get(state),x,175,22);
            g.drawImage(new HeadlessBattleMapRenderer(context.modRoot()).render(map,42,30),x,210,null);
        }
        text(g,"Matching full footprints / transparent wreck gaps / rotations retained / collapsed bases bring down their supported layers",35,900,20);
        text(g,"Built-in ImageGen / 1920 x 1080 / production Java2D renderer / no live OpenGL capture",35,1035,18);
        g.dispose();return image;
    }
    private static void text(Graphics2D g,String s,int x,int y,int size){g.setColor(new Color(0xdde9e4));g.setFont(new Font("SansSerif",Font.PLAIN,size));g.drawString(s,x,y);}
    private static final class Fixture implements AutoCloseable {
        final NavigationGrid grid=new NavigationGrid(20,20);
        final CellTopology topology=new CellTopology(20,20);
        final DoodadService props=new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        Fixture(){
            for(int y=0;y<20;y++)for(int x=0;x<20;x++) {
                grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,CellTopology.GroundKind.INDOOR);
                topology.setSurface(x,y,topology.surfaceIndex("ship.passage-deck"));
            }
            navigation=new NavigationService(grid,topology,false);editor=new MapEditor(navigation);editor.setDoodads(props);
        }
        void commandPost(int turn) {
            RoomPose pose=new RoomPose(turn,false);
            var shape=RoomShape.rectangle(12,6).posed(pose);
            List<Doorway> doors=new ArrayList<>();
            for(int[] d:new int[][]{{5,6},{6,6},{-1,3}}) {
                int[] at=pose.map(d[0],d[1],12,6);doors.add(new Doorway(4+at[0],4+at[1]));
            }
            var ctx=new GenContext(grid,topology,new Random(42),20,20,42);
            var room=new RoomPacker.Placed(shape,pose,4,4,RoomPurpose.KEEP_THRONE,doors);
            var floor=new RoomFloor(ctx,room,RoomFit.STANDARD);
            RoomFittings.forInstallation(RoomPurpose.KEEP_THRONE).fit(floor);
            if(!floor.circulationSurvives() || floor.dropUnreachableWork()!=0)
                throw new IllegalStateException("Command post lost working access");
            floor.seal();
            for(Doodad d:ctx.doodads) props.addDoodad(d);
        }
        void place(String id,int x,int y,int facing,int turn){
            var def=TileRegistry.installed().doodad(id);
            int w=(facing&1)==0?def.footprintCellsX:def.footprintCellsY,h=(facing&1)==0?def.footprintCellsY:def.footprintCellsX;
            RoomPose pose=new RoomPose(turn,false);int[] a=pose.map(x,y,20,20),b=pose.map(x+w-1,y+h-1,20,20);
            Doodad d=new Doodad(Math.min(a[0],b[0]),Math.min(a[1],b[1]),def,(turn+facing)%4);
            if(d.cover>0)for(int py=d.cellY;py<d.cellY+d.footprintCellsY;py++)for(int px=d.cellX;px<d.cellX+d.footprintCellsX;px++) {
                grid.setWalkable(px,py,false);grid.setSeeThrough(px,py,!(id.startsWith("factory-machines.") || id.startsWith("power-plant.")) || id.startsWith("factory-machines.conveyor"));topology.setFixture(px,py,true);
            }
            props.addDoodad(d);
        }
        public void close(){navigation.close();}
    }
}
