package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InstallationServiceFittingTest {
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    @Test void armoryKeepsIssueApronRepairBenchAndSeparateStockInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            var f = fit(12, 8, pose, RoomPurpose.ARMORY, List.of(new int[]{5,8}, new int[]{6,8}));
            for (String id : List.of("counter", "bench", "bench-tools", "ready-locker", "ammo", "rack", "rifles", "rack-frame"))
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("armory-workshop." + id)), id + " / " + pose);
            assertEquals(2, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.ISSUE).count());
            assertEquals(1, f.ctx.fixtureTasks.stream().filter(t -> t.affordance() == Affordance.REPAIR).count());
            verify(f);
        }
    }

    @Test void utilityHasFullGeneratorSwitchgearAndReadoutAcrossTheServiceAisle() {
        for (RoomPose pose : RoomPose.all()) {
            var f = fit(8, 8, pose, RoomPurpose.ENGINE_ROOM, List.of(new int[]{3,8}, new int[]{4,8}));
            for (String id : List.of("generator", "switchgear", "control"))
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("ship-engineering." + id)), id + " / " + pose);
            assertEquals(4, f.ctx.fixtureTasks.size());
            verify(f);
            for (int x = 0; x < 8; x++) for (int y = 4; y <= 5; y++) {
                int[] at = f.floor.toLocal(x,y);
                assertTrue(f.ctx.grid.isWalkable(4+at[0],4+at[1]), "Plant service aisle blocked");
            }
        }
    }

    @Test void alternateDoorEdgesAndCompactRoomsRetainAccessibleWork() {
        for (RoomPose pose : RoomPose.all()) {
            verify(fit(12,8,pose,RoomPurpose.ARMORY,List.of(new int[]{1,-1},new int[]{12,3},new int[]{5,8})));
            verify(fit(8,8,pose,RoomPurpose.ENGINE_ROOM,List.of(new int[]{1,-1},new int[]{8,3},new int[]{3,8})));
            for (RoomPurpose purpose : List.of(RoomPurpose.ARMORY, RoomPurpose.ENGINE_ROOM)) {
                var f = fit(7,6,pose,purpose,List.of(new int[]{3,6}));
                assertFalse(f.ctx.fixtureTasks.isEmpty());
                verify(f);
            }
        }
    }

    private static Fixture fit(int w, int h, RoomPose pose, RoomPurpose purpose, List<int[]> doors) {
        var shape = RoomShape.rectangle(w,h).posed(pose);
        int width=shape.width()+8, height=shape.height()+8;
        var grid=new NavigationGrid(width,height);
        for(int x=0;x<width;x++) for(int y=0;y<height;y++) grid.setWalkableFloor(x,y);
        var ctx=new GenContext(grid,new CellTopology(width,height),new Random(42),width,height,42);
        List<Doorway> worldDoors=new ArrayList<>();
        for(int[] door:doors) {
            int[] d=pose.map(door[0],door[1],w,h); worldDoors.add(new Doorway(4+d[0],4+d[1]));
        }
        var room=new RoomPacker.Placed(shape,pose,4,4,purpose,worldDoors);
        var floor=new RoomFloor(ctx,room,RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forInstallation(purpose).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    private static void verify(Fixture f) {
        assertTrue(f.floor.circulationSurvives(), "Circulation severed");
        assertEquals(0,f.floor.dropUnreachableWork(),"Published work must remain accessible");
        f.floor.seal();
        for(var task:f.ctx.fixtureTasks) {
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            if (task.berth() < 0) assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()),
                    "Work must target installed equipment");
        }
        for(var d:f.ctx.doodads) if(d.cover>0)
            assertTrue(PropDamage.EQUIPMENT.containsKey(d.definitionId()),"Missing damage registration: "+d.definitionId());
        for(LayoutOp op:f.ops) if(op instanceof LayoutOp.Assembly a) {
            assertFalse(a.baseId().startsWith("doodad."));
        }
    }
}
