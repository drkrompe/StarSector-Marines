package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
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

class CrewLoungeFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(RoomPose pose, int doorX, int doorY) {
        RoomShape shape = RoomShape.rectangle(16,12).posed(pose);
        int w=shape.width()+8,h=shape.height()+8;
        NavigationGrid grid=new NavigationGrid(w,h);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) grid.setWalkableFloor(x,y);
        GenContext ctx=new GenContext(grid,new CellTopology(w,h),new Random(7),w,h,7);
        int[] door=pose.map(doorX,doorY,16,12);
        RoomFloor floor=new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.CREW_LOUNGE,
                List.of(new Doorway(door[0]+4,door[1]+4))),RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>();floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.CREW_LOUNGE,RoomShape.rectangle(16,12),RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void everyPoseKeepsDistinctRecreationGroupsAndExclusiveAccessiblePlaces() {
        for(RoomPose pose:RoomPose.all()) {
            Fixture f=fit(pose,7,-1);
            assertEquals(2,pieces(f,"sofa-low")); assertEquals(1,pieces(f,"sofa-high"));
            for(String id:List.of("media","card-table","pool","refreshments","books")) assertEquals(1,pieces(f,id),id);
            assertEquals(16,f.ctx.fixtureTasks.size());
            assertEquals(4,f.ctx.doodads.stream().filter(d->"crew-lounge.stool".equals(d.definitionId())).count());
            verify(f);
            for(int x=0;x<16;x++) for(int y:new int[]{0,5,6,11}) walkable(f,x,y);
            for(int y=0;y<12;y++) for(int x:new int[]{0,7,8,15}) walkable(f,x,y);
            for(int x=10;x<12;x++) { walkable(f,x,7);walkable(f,x,10); }
        }
    }

    @Test void perimeterHatchesKeepAllRecreationGroupsAndAccess() {
        for(RoomPose pose:RoomPose.all())
            for(int[] door:new int[][]{{3,-1},{3,12},{-1,3},{-1,8},{16,2},{16,9},{10,-1},{10,12}}) {
                Fixture f=fit(pose,door[0],door[1]);
                assertEquals(16,f.ctx.fixtureTasks.size());
                assertEquals(2,pieces(f,"sofa-low")); assertEquals(1,pieces(f,"sofa-high"));
                for(String id:List.of("media","card-table","pool","refreshments","books"))
                    assertEquals(1,pieces(f,id),id);
                verify(f);
                walkable(f,Math.max(0,Math.min(15,door[0])),Math.max(0,Math.min(11,door[1])));
            }
    }

    @Test void gameAndMagazineOverlaysStayWithinTheirTableRims() throws Exception {
        var image=ImageIO.read(Path.of("mod/graphics/tilesets/crew-lounge.png").toFile());
        for(String id:List.of("magazines","game")) {
            var def=TileRegistry.installed().doodad("crew-lounge."+id);
            assertEquals(0,def.cover.level());assertEquals(0f,def.ballisticHalfHeight);
            int height=def.footprintCellsY*64;
            for(int y=0;y<height;y++) for(int x:new int[]{2,5,122,125})
                assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2);
            for(int x=0;x<128;x++) for(int y:new int[]{2,height-3})
                assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2);
        }
    }

    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();
        HashSet<String> stands=new HashSet<>();
        for(var task:f.ctx.fixtureTasks) {
            assertEquals(Affordance.UNWIND,task.affordance());
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
        }
    }
    private static long pieces(Fixture f,String id) {
        return f.ops.stream().filter(op->op instanceof LayoutOp.Assembly a && a.baseId().equals("crew-lounge."+id)).count();
    }
    private static void walkable(Fixture f,int x,int y) {
        int[] cell=f.floor.toLocal(x,y);assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
