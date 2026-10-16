package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.PowerPlantKit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.AuthoredFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PowerHallFittingTest {
    @Test void bothHallsKeepTheirCoreCrewAndConnectedServiceFloorInEveryPose() {
        for (var plan : List.of(BatteryFort.PLAN,ShieldFort.PLAN)) {
            var spec=plan.rooms().stream().filter(r -> r.aspect()==FortAspect.POWER).findFirst().orElseThrow();
            for (RoomPose pose : RoomPose.all()) {
                var shape=RoomShape.rectangle(spec.width(),spec.height()).posed(pose);
                var grid=new NavigationGrid(24,24);var topology=new CellTopology(24,24);
                for (int y=0;y<shape.height();y++) for (int x=0;x<shape.width();x++) grid.setWalkableFloor(4+x,4+y);
                for (int y=spec.core()[1]-1;y<=spec.core()[1]+1;y++) for(int x=spec.core()[0]-1;x<=spec.core()[0]+1;x++) {
                    int[] cell=pose.map(x,y,spec.width(),spec.height());grid.setWalkable(4+cell[0],4+cell[1],false);
                }
                List<Doorway> doors=new ArrayList<>();
                for (var d:spec.doors()) {
                    int[] cell=pose.map(d.x(),d.y(),spec.width(),spec.height());
                    doors.add(new Doorway(4+cell[0],4+cell[1]));grid.setWalkableFloor(4+cell[0],4+cell[1]);
                }
                var ctx=new GenContext(grid,topology,new Random(42),24,24,42);
                var floor=new RoomFloor(ctx,new RoomPacker.Placed(shape,pose,4,4,spec.purpose(),doors),RoomFit.STANDARD);
                new AuthoredFitting(spec.layout()).fit(floor);
                assertTrue(floor.circulationSurvives());floor.seal();
                assertEquals(7,ctx.doodads.size(),"Two complete machines, three console cells, two seats");
                for (var machine : List.of(PowerPlantKit.TURBINE,PowerPlantKit.COOLANT)) {
                    var prop=ctx.doodads.stream().filter(d -> d.definitionId().equals(machine.id())).findFirst().orElseThrow();
                    assertEquals(pose.posedWidth(machine.width(),machine.height()),prop.footprintCellsX);
                    assertEquals(pose.posedHeight(machine.width(),machine.height()),prop.footprintCellsY);
                    for(int y=prop.cellY;y<prop.cellY+prop.footprintCellsY;y++) for(int x=prop.cellX;x<prop.cellX+prop.footprintCellsX;x++) {
                        assertFalse(grid.isWalkable(x,y));assertFalse(grid.isSeeThrough(x,y));
                    }
                }
                for (var op:spec.ops()) if(op instanceof LayoutOp.Lane lane)
                    for(int y=lane.y();y<lane.y()+lane.spanY();y++) for(int x=lane.x();x<lane.x()+lane.spanX();x++)
                        walkable(grid,pose,spec,x,y);
                for (int[] stand:spec.stands()) walkable(grid,pose,spec,stand[0],stand[1]);
                var queue=new ArrayDeque<int[]>();var seen=new HashSet<Integer>();
                queue.add(new int[]{doors.get(0).x(),doors.get(0).y()});
                while(!queue.isEmpty()) {
                    int[] at=queue.removeFirst();
                    if(!seen.add(grid.index(at[0],at[1]))) continue;
                    for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int x=at[0]+step[0],y=at[1]+step[1];
                        if(grid.inBounds(x,y) && grid.canTraverseCellStep(at[0],at[1],x,y)) queue.add(new int[]{x,y});
                    }
                }
                for(int y=0;y<24;y++) for(int x=0;x<24;x++)
                    if(grid.isWalkable(x,y)) assertTrue(seen.contains(grid.index(x,y)),"Isolated service floor");
            }
        }
    }

    @Test void machineryStatesKeepTheirCompleteRegisteredFootprint() {
        var registry=TileRegistry.installed();
        for(var machine:List.of(PowerPlantKit.TURBINE,PowerPlantKit.COOLANT))
            for(String suffix:List.of("","-damaged","-destroyed")) {
                var def=registry.doodad(machine.id()+suffix);assertNotNull(def);
                assertEquals(machine.width(),def.footprintCellsX);assertEquals(machine.height(),def.footprintCellsY);
                assertEquals(suffix.equals("-destroyed")?0:3,def.cover.level());
            }
    }

    private static void walkable(NavigationGrid grid,RoomPose pose,FortRoom spec,int x,int y) {
        int[] at=pose.map(x,y,spec.width(),spec.height());assertTrue(grid.isWalkable(4+at[0],4+at[1]));
    }
}
