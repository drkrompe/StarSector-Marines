package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipGymFittingTest {
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
        RoomFloor floor = new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.GYMNASIUM,entrances),RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.GYMNASIUM,canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void everyPoseKeepsWaterContainedAndExerciseOnDryAccessibleDeck() {
        for(RoomPose pose:RoomPose.all()) verify(fit(RoomShape.rectangle(14,10),pose,List.of(new int[]{3,-1})));
    }
    @Test void allPerimeterHatchesRetainTheEquipmentAndPoolSurround() {
        RoomShape shape=RoomShape.rectangle(14,10);
        for(RoomPose pose:RoomPose.all()) {
            for(int[] door:shape.doorways()) verify(fit(shape,pose,List.of(door)));
            verify(fit(shape,pose,List.of(new int[]{7,-1},new int[]{7,10},new int[]{-1,5},new int[]{14,5})));
        }
        assertInstanceOf(GymFitting.class,RoomFittings.forPurpose(RoomPurpose.GYMNASIUM));
        Fixture small=fit(RoomShape.rectangle(8,6),new RoomPose(0,false),List.of(new int[]{-1,3}));
        assertTrue(small.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("ship-gym.")));
        for(int y=0;y<small.ctx.height;y++) for(int x=0;x<small.ctx.width;x++) assertFalse(small.ctx.topology.isWater(x,y));
    }
    private static void verify(Fixture f) {
        assertEquals(4,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
        assertEquals(7,f.ctx.fixtureTasks.size());
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();
        for(int y=0;y<10;y++) for(int x=0;x<14;x++) {
            int[] c=f.floor.toLocal(x,y);int gx=c[0]+4,gy=c[1]+4;
            boolean water=x>=8 && x<12 && y>=3 && y<7;
            assertEquals(water,f.ctx.topology.isWater(gx,gy));
            if(water) {
                assertEquals("ship-gym.water",f.ctx.topology.getSurfaceId(gx,gy));
                assertFalse(f.ctx.grid.isWalkable(gx,gy));assertTrue(f.ctx.grid.isSeeThrough(gx,gy));
                assertFalse(f.ctx.topology.isWall(gx,gy));
            } else if(x>=7 && x<=12 && y>=2 && y<=7) {
                assertEquals("ship-gym.coping",f.ctx.topology.getSurfaceId(gx,gy));walkable(f,x,y);
            }
        }
        for(int y=5;y<8;y++) for(int x=1;x<3;x++) walkable(f,x,y);
        HashSet<String> stands=new HashSet<>();
        for(var task:f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertFalse(f.ctx.topology.isWater(task.cellX(),task.cellY()));
            assertEquals(com.dillon.starsectormarines.battle.world.gen.Affordance.EXERCISE,task.affordance());
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
        }
    }
    private static void walkable(Fixture f, int x, int y) {
        int[] cell=f.floor.toLocal(x,y); assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
