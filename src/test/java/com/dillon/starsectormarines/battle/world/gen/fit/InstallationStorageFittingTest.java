package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InstallationStorageFittingTest {
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    @Test void kitModulesMatchTheirRegisteredFootprintsAndDamageStates() {
        for (var module : List.of(LogisticsKit.rack(LogisticsKit.Stock.CASES),
                LogisticsKit.rack(LogisticsKit.Stock.CARTONS), LogisticsKit.pallet(LogisticsKit.Stock.CASES),
                LogisticsKit.pallet(LogisticsKit.Stock.CARTONS), LogisticsKit.ammunition(),
                LogisticsKit.desk(false),LogisticsKit.desk(true))) {
            var registry=TileRegistry.installed();var base=registry.doodad(module.base());
            assertNotNull(base);assertEquals(module.width(),base.footprintCellsX);assertEquals(module.height(),base.footprintCellsY);
            List<String> ids=new ArrayList<>(List.of(module.base()));
            for (var layer : module.layers()) ids.add(layer.doodadId());
            for (String id : ids) {
                var intact=registry.doodad(id);
                for (String suffix : List.of("-damaged","-destroyed")) {
                    var state=registry.doodad(id+suffix);assertNotNull(state,id+suffix);
                    assertEquals(intact.footprintCellsX,state.footprintCellsX);
                    assertEquals(intact.footprintCellsY,state.footprintCellsY);
                }
            }
        }
    }

    @Test void warehousesAndReceivingRoomsKeepHandlingLanesInEveryPose() {
        for (RoomPurpose purpose : List.of(RoomPurpose.STOCKROOM, RoomPurpose.LOADING_BAY))
            for (RoomPose pose : RoomPose.all()) {
                Fixture f = fit(16,14,pose,purpose,List.of(new int[]{7,-1},new int[]{8,-1},new int[]{-1,7},new int[]{16,7}));
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("main-stores.pallet")));
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals(
                        purpose == RoomPurpose.STOCKROOM ? "main-stores.rack" : "main-stores.packing")));
                verify(f);
                for (int y=0;y<14;y++) for (int x=7;x<=8;x++) walkable(f,x,y);
                for (int x=0;x<16;x++) for (int y : new int[]{2,3,10,11,12,13}) walkable(f,x,y);
            }
    }

    @Test void compactStoresAndAlternateDoorsKeepConnectedFloorAndAccessibleWork() {
        for (int[] size : new int[][]{{4,4},{6,6},{8,8},{12,10},{16,22}}) for (RoomPose pose : RoomPose.all()) {
            int w=size[0],h=size[1];
            Fixture f=fit(w,h,pose,RoomPurpose.STOCKROOM,List.of(new int[]{w/2,-1},new int[]{w/2,h},
                    new int[]{-1,h/2},new int[]{w,h/2}));
            assertFalse(f.ctx.fixtureTasks.isEmpty(), w+" x "+h);
            verify(f);
        }
    }

    private static Fixture fit(int w,int h,RoomPose pose,RoomPurpose purpose,List<int[]> doors) {
        var shape=RoomShape.rectangle(w,h).posed(pose);
        int width=shape.width()+8,height=shape.height()+8;
        var grid=new NavigationGrid(width,height);
        for(int x=0;x<shape.width();x++) for(int y=0;y<shape.height();y++) grid.setWalkableFloor(4+x,4+y);
        var ctx=new GenContext(grid,new CellTopology(width,height),new Random(42),width,height,42);
        List<Doorway> worldDoors=new ArrayList<>();
        for(int[] door:doors) {
            int[] d=pose.map(door[0],door[1],w,h);worldDoors.add(new Doorway(4+d[0],4+d[1]));
            grid.setWalkableFloor(4+d[0],4+d[1]);
        }
        var room=new RoomPacker.Placed(shape,pose,4,4,purpose,worldDoors);
        var floor=new RoomFloor(ctx,room,RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>();floor.record(ops::add);
        RoomFittings.forInstallation(purpose).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    private static void walkable(Fixture f,int x,int y) {
        int[] at=f.floor.toLocal(x,y);assertTrue(f.ctx.grid.isWalkable(4+at[0],4+at[1]),"Walkway occupied");
    }
    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());f.floor.seal();
        var grid=f.ctx.grid;var start=f.floor.room().doors().get(0);
        var seen=new HashSet<Integer>();var queue=new ArrayDeque<int[]>();
        seen.add(grid.index(start.x(),start.y()));queue.add(new int[]{start.x(),start.y()});
        while(!queue.isEmpty()) {
            int[] at=queue.removeFirst();
            for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=at[0]+step[0],y=at[1]+step[1];
                if(grid.inBounds(x,y) && grid.canTraverseCellStep(at[0],at[1],x,y)
                        && seen.add(grid.index(x,y))) queue.add(new int[]{x,y});
            }
        }
        for(int x=0;x<grid.getWidth();x++) for(int y=0;y<grid.getHeight();y++)
            if(grid.isWalkable(x,y)) assertTrue(seen.contains(grid.index(x,y)),"Isolated floor or door");
        var stands=new HashSet<Integer>();
        for(var task:f.ctx.fixtureTasks) {
            assertTrue(stands.add(grid.index(task.cellX(),task.cellY())),"Duplicate watch stand");
            assertTrue(grid.isWalkable(task.cellX(),task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
        }
        for(var d:f.ctx.doodads) assertTrue(PropDamage.EQUIPMENT.containsKey(d.definitionId()) || PropDamage.DECORATIONS.contains(d.definitionId()),"Missing damage registration: "+d.definitionId());
    }
}
