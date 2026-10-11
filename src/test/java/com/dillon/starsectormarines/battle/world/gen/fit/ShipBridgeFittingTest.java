package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShipBridgeFittingTest {
    private record Room(RoomShape shape,int originX,int originY,RoomPose pose,
                        RoomPurpose purpose,List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx,RoomFloor floor,List<LayoutOp> ops) {}

    private static Fixture fit(RoomPose pose,List<int[]> doors) {
        RoomShape shape=RoomRecipe.COMMAND.shape().posed(pose);
        int w=shape.width()+8,h=shape.height()+8;
        NavigationGrid grid=new NavigationGrid(w,h);
        // Restrict navigation to this diamond and its actual doors; no escape through corners.
        for(int y=0;y<shape.height();y++) for(int x=0;x<shape.width();x++)
            if(shape.contains(x,y)) grid.setWalkableFloor(x+4,y+4);
        List<Doorway> localDoors=new ArrayList<>();
        for(int[] d:doors) {
            int[] cell=pose.map(d[0],d[1],13,11);
            localDoors.add(new Doorway(cell[0]+4,cell[1]+4));
            grid.setWalkableFloor(cell[0]+4,cell[1]+4);
        }
        GenContext ctx=new GenContext(grid,new CellTopology(w,h),new Random(7),w,h,7);
        RoomFloor floor=new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.BRIDGE,localDoors),RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>();floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.BRIDGE,RoomRecipe.COMMAND.shape(),RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void everyPoseKeepsThePlotFourBanksAndNineExclusiveWatchPositions() {
        for(RoomPose pose:RoomPose.all()) {
            Fixture f=fit(pose,List.of(new int[]{6,-1}));
            assertBanks(f);assertEquals(9,f.ctx.fixtureTasks.size());verify(f);
            for(int y=3;y<=7;y++) for(int x=4;x<=8;x++)
                if(x==4 || x==8 || y==3 || y==7) walkable(f,x,y);
        }
    }

    @Test void everyDiamondHatchAndOppositeHatchesPreserveCirculationAndWatch() {
        for(RoomPose pose:RoomPose.all()) {
            for(int[] door:RoomRecipe.COMMAND.shape().doorways()) {
                Fixture f=fit(pose,List.of(door));
                assertBanks(f);assertEquals(9,f.ctx.fixtureTasks.size());verify(f);
            }
            Fixture f=fit(pose,List.of(new int[]{6,-1},new int[]{6,11},new int[]{-1,5},new int[]{13,5}));
            assertBanks(f);assertEquals(9,f.ctx.fixtureTasks.size());verify(f);
        }
    }

    @Test void genericBridgesKeepTheirExistingFitting() {
        assertInstanceOf(BridgeFitting.class,RoomFittings.forPurpose(RoomPurpose.BRIDGE));
        assertInstanceOf(ShipBridgeFitting.class,RoomFittings.forShipRoom(RoomPurpose.BRIDGE,
                RoomRecipe.COMMAND.shape(),RoomFit.STANDARD));
    }

    private static void assertBanks(Fixture f) {
        for(String id:List.of("plot","helm","systems","console-west","console-east"))
            assertEquals(1,f.ops.stream().filter(op->op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("ship-bridge."+id)).count(),id);
    }
    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();HashSet<String> stands=new HashSet<>();
        for(var task:f.ctx.fixtureTasks) {
            assertEquals(Affordance.WATCH,task.affordance());
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
        }
    }
    private static void walkable(Fixture f,int x,int y) {
        int[] cell=f.floor.toLocal(x,y);assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
