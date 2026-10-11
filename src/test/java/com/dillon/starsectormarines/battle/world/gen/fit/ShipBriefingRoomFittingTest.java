package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipBriefingRoomFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(RoomShape canonical, RoomPose pose, List<int[]> doors) {
        RoomShape shape = canonical.posed(pose);
        int w = shape.width()+8, h = shape.height()+8;
        NavigationGrid grid = new NavigationGrid(w,h);
        for (int y=0;y<shape.height();y++) for (int x=0;x<shape.width();x++)
            if (shape.contains(x,y)) grid.setWalkableFloor(x+4,y+4);
        List<Doorway> entrances = new ArrayList<>();
        for (int[] d : doors) {
            int[] cell = pose.map(d[0],d[1],canonical.width(),canonical.height());
            entrances.add(new Doorway(cell[0]+4,cell[1]+4));
            grid.setWalkableFloor(cell[0]+4,cell[1]+4);
        }
        GenContext ctx = new GenContext(grid,new CellTopology(w,h),new Random(7),w,h,7);
        RoomFloor floor = new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.CONFERENCE_ROOM,entrances),RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.CONFERENCE_ROOM,canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void eightSeatsAndLayeredTableFaceThePresentationAreaInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f=fit(RoomShape.rectangle(14,10),pose,List.of(new int[]{3,-1}));
            verify(f);
            assertEquals(11,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
            assertEquals(8,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().startsWith("briefing-room.chair-")).count());
            LayoutOp.Assembly table=f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("briefing-room.table")).map(op -> (LayoutOp.Assembly)op).findFirst().orElseThrow();
            assertEquals(List.of(new LayoutOp.Layer(0,0,"briefing-room.tactical-map")),table.layers());
            for(String id:List.of("display","lectern"))
                assertEquals(1,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                        && a.baseId().equals("briefing-room."+id)).count());
            for(int x=2;x<12;x++) {walkable(f,x,2);walkable(f,x,7);}
            for(int y=2;y<8;y++) {walkable(f,2,y);walkable(f,11,y);}
            for(int x:new int[]{4,6,8}) {
                chair(f,x,3,"bottom");chair(f,x,6,"top");
            }
            chair(f,3,4,"left");chair(f,10,4,"right");
        }
    }

    @Test void allHatchesKeepTheBriefingFurnitureAndClearRoutes() {
        RoomShape shape=RoomShape.rectangle(14,10);
        for(RoomPose pose:RoomPose.all()) {
            for(int[] door:shape.doorways()) verify(fit(shape,pose,List.of(door)));
            verify(fit(shape,pose,List.of(new int[]{7,-1},new int[]{7,10},new int[]{-1,5},new int[]{14,5})));
        }
        assertInstanceOf(AisleFitting.class,RoomFittings.forPurpose(RoomPurpose.CONFERENCE_ROOM));
        Fixture small=fit(RoomShape.rectangle(8,6),new RoomPose(0,false),List.of(new int[]{-1,3}));
        assertTrue(small.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("briefing-room.")));
        assertFalse(small.ctx.doodads.isEmpty());assertTrue(small.ctx.fixtureTasks.isEmpty());
    }

    @Test void tacticalOverlayStaysInsideTableRim() throws Exception {
        var image=ImageIO.read(Path.of("mod/graphics/tilesets/briefing-room.png").toFile());
        var def=TileRegistry.installed().doodad("briefing-room.tactical-map");
        assertEquals(0,def.cover.level());assertEquals(0f,def.ballisticHalfHeight);
        for(int y=0;y<128;y++) for(int x=0;x<384;x++)
            if(x<48 || x>=336 || y<20 || y>=108)
                assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2,"table rim");
    }

    private static void chair(Fixture f,int x,int y,String back) {
        assertTrue(f.ops.stream().anyMatch(op -> op instanceof LayoutOp.Assembly a
                && a.x()==x && a.y()==y && a.baseId().equals("briefing-room.chair-back-"+back)));
    }
    private static void verify(Fixture f) {
        assertEquals(11,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
        assertTrue(f.ctx.fixtureTasks.isEmpty(),"briefings must not create permanent work");
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();
        // Every unoccupied interior cell is reachable from the outer approach.
        java.util.Set<String> reached=new HashSet<>();
        java.util.ArrayDeque<int[]> queue=new java.util.ArrayDeque<>();queue.add(new int[]{4,4});
        while(!queue.isEmpty()) {
            int[] c=queue.remove();
            if(!f.ctx.grid.isWalkable(c[0],c[1]) || !reached.add(c[0]+","+c[1])) continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=c[0]+d[0],y=c[1]+d[1];
                if(x>=0 && y>=0 && x<f.ctx.width && y<f.ctx.height) queue.add(new int[]{x,y});
            }
        }
        for(int y=0;y<f.floor.height();y++) for(int x=0;x<f.floor.width();x++)
            if(f.ctx.grid.isWalkable(x+4,y+4)) assertTrue(reached.contains((x+4)+","+(y+4)));
    }
    private static void walkable(Fixture f, int x, int y) {
        int[] cell=f.floor.toLocal(x,y); assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
