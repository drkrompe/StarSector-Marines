package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
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

class ShipShuttleBayFittingTest {
    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}
    private record Fixture(GenContext ctx, RoomFloor floor, List<LayoutOp> ops) {}

    private static Fixture fit(int w, int h, RoomPose pose, List<int[]> doors) {
        RoomShape canonical=RoomShape.rectangle(w,h), shape=canonical.posed(pose);
        int mw=shape.width()+8, mh=shape.height()+8;
        NavigationGrid grid=new NavigationGrid(mw,mh);
        for (int y=0;y<shape.height();y++) for (int x=0;x<shape.width();x++)
            grid.setWalkableFloor(x+4,y+4);
        List<Doorway> entrances=new ArrayList<>();
        for (int[] door:doors) {
            int[] cell=pose.map(door[0],door[1],w,h);
            entrances.add(new Doorway(cell[0]+4,cell[1]+4));
            grid.setWalkableFloor(cell[0]+4,cell[1]+4);
        }
        GenContext ctx=new GenContext(grid,new CellTopology(mw,mh),new Random(7),mw,mh,7);
        RoomFloor floor=new RoomFloor(ctx,new Room(shape,4,4,pose,RoomPurpose.HANGAR,entrances),RoomFit.STANDARD);
        List<LayoutOp> ops=new ArrayList<>();floor.record(ops::add);
        RoomFittings.forShipRoom(RoomPurpose.HANGAR,canonical,RoomFit.STANDARD).fit(floor);
        return new Fixture(ctx,floor,ops);
    }

    @Test void fullBayKeepsSixBoatsAndClearTaxiEnvelopeInEveryPose() {
        for(RoomPose pose:RoomPose.all()) {
            Fixture f=fit(24,30,pose,List.of(new int[]{12,-1}));
            assertEquals(6,f.ctx.gantries.size());
            assertEquals(26,f.ctx.fixtureTasks.size());
            assertEquals(8,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly).count());
            assertEquals(24,f.ctx.doodads.stream().filter(d -> d.definitionId().equals("shuttle-bay.tie-down")).count());
            assertEquals(3,f.ops.stream().filter(op -> op instanceof LayoutOp.Assembly a
                    && a.baseId().equals("main-stores.rack") && a.layers().size()==4).count());
            verify(f,6);
            for(int y=0;y<28;y++) for(int x=8;x<16;x++) walkable(f,x,y);
            for(int y=0;y<4;y++) for(int x=2;x<22;x++) walkable(f,x,y);
        }
    }

    @Test void hatchesYieldWholeEquipmentGroupsWithoutLosingBoatsOrWorkAccess() {
        for(int[] size:List.of(new int[]{24,30},new int[]{16,10}))
            for(RoomPose pose:RoomPose.all()) {
                for(int[] door:RoomShape.rectangle(size[0],size[1]).doorways())
                    verify(fit(size[0],size[1],pose,List.of(door)),BoatBayFitting.boatsIn(size[0],size[1]));
                verify(fit(size[0],size[1],pose,List.of(new int[]{size[0]/2,-1},new int[]{size[0]/2,size[1]},
                        new int[]{-1,size[1]/2},new int[]{size[0],size[1]/2})),BoatBayFitting.boatsIn(size[0],size[1]));
            }
    }

    @Test void smallBoatBayRetainsItsGigAndCompactSupportEquipment() {
        Fixture f=fit(16,10,RoomPose.CANONICAL,List.of(new int[]{8,-1}));
        verify(f,1);
        assertEquals(6,f.ctx.fixtureTasks.size());
        assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("shuttle-bay.flight-desk")));
        assertTrue(f.ctx.doodads.stream().anyMatch(d -> d.definitionId().equals("main-stores.pallet")));
        assertArrayEquals(new int[]{0,-1},BoatBayFitting.shipboard().outboard());
    }

    @Test void layeredWheelsAndFlushMarkingsHaveClearRimsAndNoBlocking() throws Exception {
        var image=ImageIO.read(Path.of("mod/graphics/tilesets/shuttle-bay.png").toFile());
        for(String id:List.of("wheels","tie-down")) {
            var def=TileRegistry.installed().doodad("shuttle-bay."+id);
            assertEquals(0,def.cover.level());assertEquals(0f,def.ballisticHalfHeight);
            int size=64*def.footprintCellsX;
            for(int y=0;y<size;y++) for(int x=0;x<size;x++)
                if(x<6||y<6||x>=size-6||y>=size-6)
                    assertTrue((image.getRGB(def.col*64+x,def.row*64+y)>>>24)<=2,id+" rim");
        }
        assertNotNull(TileRegistry.installed().block("shuttle-bay.landing-deck"));
    }

    private static void verify(Fixture f,int boats) {
        assertEquals(boats,f.ctx.gantries.size());
        assertEquals(boats*2,f.ctx.fixtureTasks.stream().filter(t -> t.affordance()==Affordance.SERVICE).count());
        assertTrue(f.floor.circulationSurvives());assertEquals(0,f.floor.dropUnreachableWork());
        f.floor.seal();HashSet<String> stands=new HashSet<>();
        for(Gantry berth:f.ctx.gantries) {
            assertEquals(Gantry.Holds.BOAT,berth.holds);
            for(int y=berth.bottom();y<=berth.top();y++) for(int x=berth.left();x<=berth.right();x++) {
                assertTrue(f.ctx.grid.isWalkable(x,y),"blocked berth");
                assertEquals("shuttle-bay.landing-deck",f.ctx.topology.getSurfaceId(x,y));
            }
        }
        for(var task:f.ctx.fixtureTasks) {
            assertTrue(stands.add(task.cellX()+","+task.cellY()));
            assertTrue(f.ctx.grid.isWalkable(task.cellX(),task.cellY()));
            assertEquals(1,Math.abs(task.cellX()-task.fixtureX())+Math.abs(task.cellY()-task.fixtureY()));
            if(task.berth()==FixtureTask.NO_BERTH)
                assertTrue(f.ctx.topology.isFixture(task.fixtureX(),task.fixtureY()));
        }
        assertTrue(f.ctx.doodads.stream().noneMatch(d -> d.definitionId().startsWith("doodad.")),"old scatter");
    }
    private static void walkable(Fixture f,int x,int y) {
        int[] cell=f.floor.toLocal(x,y);assertTrue(f.ctx.grid.isWalkable(cell[0]+4,cell[1]+4),x+","+y);
    }
}
