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

class ShipLoadingBayFittingTest {
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
        RoomFloor floor = new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.LOADING_BAY,entrances),RoomFit.STANDARD);
        List<LayoutOp> ops = new ArrayList<>(); floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.LOADING_BAY,canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void receivingFlowKeepsHatchApronAndHandlingAislesClearInEveryPose() {
        for (RoomPose pose : RoomPose.all()) {
            Fixture f=fit(RoomShape.rectangle(18,12),pose,List.of(new int[]{-1,5}));
            assertEquals(10,f.ctx.fixtureTasks.size());
            assertEquals(7,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
            for (String id : List.of("manifest-desk","pallet-tug","conveyor"))
                assertEquals(1,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                        && a.baseId().equals("loading-bay."+id)).count(),id);
            LayoutOp.Assembly rack=f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("main-stores.rack")).map(op -> (LayoutOp.Assembly)op).findFirst().orElseThrow();
            assertEquals(List.of(new LayoutOp.Layer(0,0,"loading-bay.insulated-tote"),
                    new LayoutOp.Layer(0,2,"loading-bay.insulated-tote"),
                    new LayoutOp.Layer(0,4,"loading-bay.provision-cartons"),
                    new LayoutOp.Layer(0,0,"main-stores.frame")),rack.layers());
            assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.READOUT).count());
            assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.TEND).count());
            assertEquals(1,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.REPAIR).count());
            assertEquals(7,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.STOW).count());
            verify(f);
            for(int[] pad:List.of(new int[]{9,2},new int[]{12,2},new int[]{15,9}))
                for(int y=pad[1];y<pad[1]+3;y++) for(int x=pad[0];x<pad[0]+2;x++) {
                    int[] cell=f.floor.toLocal(x,y);
                    assertEquals("loading-bay.staging-deck",f.ctx.topology.getSurfaceId(cell[0]+4,cell[1]+4));
                }
            for(int y=0;y<12;y++) for(int x=0;x<4;x++) walkable(f,x,y);
            for(int y=5;y<8;y++) for(int x=0;x<15;x++) walkable(f,x,y);
        }
    }

    @Test void anyHatchRetainsAccessibleWorkAndWholeAssemblies() {
        RoomShape shape=RoomShape.rectangle(18,12);
        for(RoomPose pose:RoomPose.all()) {
            for(int[] door:shape.doorways()) verify(fit(shape,pose,List.of(door)));
            verify(fit(shape,pose,List.of(new int[]{7,-1},new int[]{7,12},new int[]{-1,5},new int[]{18,5})));
        }
        RoomFitting fitting=RoomFittings.forShipRoom(RoomPurpose.LOADING_BAY,shape,RoomFit.STANDARD);
        assertTrue(fitting.handed());
        assertEquals(1,fitting.hookups(shape).size());
        for(var hookup:fitting.hookups(shape)) for(var slot:hookup.slots())
            for(int[] cell:slot.cells()) assertEquals(-1,cell[0]);
        assertInstanceOf(LoadingBayFitting.class,RoomFittings.forPurpose(RoomPurpose.LOADING_BAY));
        Fixture small=fit(RoomShape.rectangle(10,6),new RoomPose(0,false),List.of(new int[]{-1,3}));
        assertTrue(small.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("loading-bay.")));
        assertFalse(small.ctx.doodads.isEmpty());
    }

    @Test void cargoLayersAndTugOpeningKeepTheirClearPixels() throws Exception {
        var image=ImageIO.read(Path.of("mod/graphics/tilesets/loading-bay.png").toFile());
        for(String id:List.of("insulated-tote","provision-cartons")) {
            var def=TileRegistry.installed().doodad("loading-bay."+id);
            assertEquals(0,def.cover.level());assertEquals(0f,def.ballisticHalfHeight);
            for(int y=0;y<128;y++) for(int x=0;x<128;x++)
                if(x<12 || x>=116 || y<12 || y>=116)
                    assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2,id+" rim");
        }
        var tug=TileRegistry.installed().doodad("loading-bay.pallet-tug");
        for(int y=110;y<178;y++) for(int x=52;x<75;x++)
            assertTrue((image.getRGB(tug.col*64+x,tug.row*64+y)>>>24)<=2,"fork opening");
    }

    private static void verify(Fixture f) {
        assertTrue(f.ctx.fixtureTasks.size()>=5);
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();HashSet<String> stands=new HashSet<>();
        for(var task:f.ctx.fixtureTasks) {
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
