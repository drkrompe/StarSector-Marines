package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InstallationWorkshopFittingTest {
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    @Test void compactWorkshopHasMachiningBenchAndLayeredStockInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            var f = fit(10, 8, pose, RoomPurpose.PARTS_CAGE, List.of(new int[]{4,8}, new int[]{5,8}));
            for (String id : List.of("factory-machines.fabricator", "main-stores.packing", "main-stores.rack"))
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals(id)), id + " / " + pose);
            assertEquals(5, f.ctx.fixtureTasks.size());
            verify(f);
        }
    }

    @Test void vehicleShedKeepsAllBerthsAndModernEquipmentWithoutLooseLegacyClutter() {
        for (RoomPose pose : RoomPose.all()) {
            var f = fit(31, 16, pose, RoomPurpose.VEHICLE_BAY,
                    List.of(new int[]{31,6}, new int[]{31,7}, new int[]{31,8}, new int[]{1,-1}, new int[]{2,-1}));
            assertEquals(6, f.ctx.gantries.size());
            for (String id : List.of("factory-machines.fabricator", "factory-machines.coolant", "main-stores.packing",
                    "main-stores.rack", "colony-furnishings.shelf", "colony-furnishings.locker"))
                assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals(id)), id);
            assertTrue(f.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("doodad.")
                    && !d.definitionId().equals("doodad.fl-striped-yellow")), "Legacy equipment remains");
            verify(f);
            for (var berth : f.ctx.gantries)
                for (int x = berth.left(); x <= berth.right(); x++)
                    for (int y = berth.bottom(); y <= berth.top(); y++)
                        assertTrue(f.ctx.grid.isWalkable(x,y), "Equipment occupies a vehicle berth");
        }
    }

    @Test void wallAndStoresDoorsKeepTheirApproachesAcrossShedSizes() {
        for (int w : new int[]{16,24,31,40}) for (RoomPose pose : RoomPose.all()) {
            verify(fit(w,16,pose,RoomPurpose.VEHICLE_BAY,List.of(
                    new int[]{1,-1}, new int[]{w-1,16}, new int[]{8,-1}, new int[]{3,16}, new int[]{w,7})));
        }
    }

    @Test void shallowWorkshopKeepsAFullBenchAndDamageableStorage() {
        for (RoomPose pose : RoomPose.all()) {
            var f = fit(7,6,pose,RoomPurpose.PARTS_CAGE,List.of(new int[]{6,-1}));
            assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("main-stores.packing")));
            verify(f);
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
