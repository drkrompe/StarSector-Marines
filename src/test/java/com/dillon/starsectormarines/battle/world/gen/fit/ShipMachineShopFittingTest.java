package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
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

class ShipMachineShopFittingTest {
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
        RoomFloor floor = new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.PARTS_CAGE,entrances),RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.PARTS_CAGE,canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void everyPoseKeepsMachiningBenchesAndLayeredPartsBesideClearAisles() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f = fit(RoomRecipe.MACHINE_SHOP.shape(),pose,List.of(new int[]{3,-1}));
            verify(f);
            for (int y : new int[]{4,5}) for (int x=0;x<11;x++) walkable(f,x,y);
            for (int y=0;y<10;y++) walkable(f,10,y);
        }
    }

    @Test void allHatchesKeepEveryStationAndPickingPositionAccessible() {
        RoomShape shape = RoomRecipe.MACHINE_SHOP.shape();
        for (RoomPose pose : RoomPose.all()) {
            for (int[] door : shape.doorways()) verify(fit(shape,pose,List.of(door)));
            verify(fit(shape,pose,List.of(new int[]{7,-1},new int[]{7,10},new int[]{-1,5},new int[]{14,5})));
        }
    }

    @Test void groundCagesAndSmallUtilityCagesKeepTheirStorageFitting() {
        assertInstanceOf(PartsCageFitting.class,RoomFittings.forPurpose(RoomPurpose.PARTS_CAGE));
        Fixture f = fit(RoomShape.rectangle(6,4),new RoomPose(0,false),List.of(new int[]{-1,2}));
        assertTrue(f.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("machine-shop.")));
        assertFalse(f.ctx.doodads.isEmpty());
        assertTrue(f.floor.circulationSurvives());
    }

    @Test void partsLoadsKeepTransparentMarginsInsideTheRetainingFrame() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/machine-shop.png").toFile());
        for (String id : List.of("bar-stock","bearings","electrical")) {
            var def = TileRegistry.installed().doodad("machine-shop."+id);
            assertEquals(0,def.cover.level()); assertEquals(0f,def.ballisticHalfHeight);
            for (int y=0;y<128;y++) for (int x=0;x<128;x++)
                if (x<12 || x>=116 || y<12 || y>=116)
                    assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2,id+" rim");
        }
    }

    private static void verify(Fixture f) {
        assertEquals(7,f.ctx.fixtureTasks.size());
        assertEquals(5,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
        for (String id : List.of("lathe","mill","assembly-bench","inspection-bench"))
            assertEquals(1,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("machine-shop."+id)).count(),id);
        LayoutOp.Assembly rack = f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                && a.baseId().equals("main-stores.rack")).map(op -> (LayoutOp.Assembly)op).findFirst().orElseThrow();
        assertEquals(List.of(new LayoutOp.Layer(0,0,"machine-shop.bar-stock"),
                new LayoutOp.Layer(0,2,"machine-shop.bearings"),new LayoutOp.Layer(0,4,"machine-shop.electrical"),
                new LayoutOp.Layer(0,0,"main-stores.frame")),rack.layers());
        assertEquals(2,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.FABRICATE).count());
        assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.REPAIR).count());
        assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.READOUT).count());
        assertEquals(3,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.STOW).count());
        assertTrue(f.floor.circulationSurvives()); assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal(); HashSet<String> stands = new HashSet<>();
        for (var task : f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
        }
    }
    private static void walkable(Fixture f, int x, int y) {
        int[] cell=f.floor.toLocal(x,y); assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
