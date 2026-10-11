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

class ShipEngineeringFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(RoomRecipe recipe, RoomPose pose, List<int[]> doors) {
        RoomShape canonical = recipe.shape(), shape = canonical.posed(pose);
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
        RoomFloor floor = new RoomFloor(ctx,new Room(shape,4,4,pose,recipe.purpose(),entrances),RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(recipe.purpose(),canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void bothRoomTypesKeepTheirCompletePlantAndAccessibleJobsInEveryPose() {
        for (RoomRecipe recipe : List.of(RoomRecipe.ENGINE_ROOM,RoomRecipe.ENGINEERING))
            for (RoomPose pose : RoomPose.all()) {
                Fixture f = fit(recipe,pose,List.of(new int[]{3,-1}));
                verify(f,recipe.purpose());
                int[] rows = recipe.purpose()==RoomPurpose.ENGINE_ROOM ? new int[]{9,10,11,16,17} : new int[]{6,7};
                for (int y : rows) for (int x=0;x<recipe.shape().width();x++) walkable(f,x,y);
            }
    }

    @Test void everyPerimeterHatchAndFourSimultaneousHatchesKeepAllMachineryAndWork() {
        for (RoomRecipe recipe : List.of(RoomRecipe.ENGINE_ROOM,RoomRecipe.ENGINEERING))
            for (RoomPose pose : RoomPose.all()) {
                for (int[] door : recipe.shape().doorways()) verify(fit(recipe,pose,List.of(door)),recipe.purpose());
                int w=recipe.shape().width(),h=recipe.shape().height();
                verify(fit(recipe,pose,List.of(new int[]{w/2,-1},new int[]{w/2,h},
                        new int[]{-1,h/2},new int[]{w,h/2})),recipe.purpose());
            }
    }

    @Test void factoryMachineryKeepsTheGenericFitting() {
        for (RoomPurpose purpose : List.of(RoomPurpose.ENGINE_ROOM,RoomPurpose.PRODUCTION_FLOOR))
            assertInstanceOf(MachinerySpaceFitting.class,RoomFittings.forPurpose(purpose));
    }

    private static void verify(Fixture f, RoomPurpose purpose) {
        boolean drive = purpose==RoomPurpose.ENGINE_ROOM;
        assertEquals(drive ? 10 : 7,f.ctx.fixtureTasks.size());
        assertEquals(drive ? 8 : 6,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
        for (String id : List.of("cooling","life-support","control","bench","switchgear","spares"))
            assertEquals(1,pieces(f,id),id);
        assertEquals(drive ? 1 : 0,pieces(f,"drive-train"));
        assertEquals(drive ? 1 : 0,pieces(f,"generator"));
        assertEquals(drive ? 0 : 1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.FABRICATE).count());
        assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.WATCH).count());
        assertTrue(f.ctx.fixtureTasks.stream().anyMatch(t -> t.affordance()==Affordance.TEND));
        assertTrue(f.ctx.fixtureTasks.stream().anyMatch(t -> t.affordance()==Affordance.REPAIR));
        assertTrue(f.ctx.doodads.stream().allMatch(d -> d.definitionId().startsWith("ship-engineering.")));
        assertTrue(f.floor.circulationSurvives()); assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal(); HashSet<String> stands = new HashSet<>();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
        }
    }
    private static long pieces(Fixture f, String id) {
        return f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                && a.baseId().equals("ship-engineering."+id)).count();
    }
    private static void walkable(Fixture f, int x, int y) {
        int[] cell=f.floor.toLocal(x,y); assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
