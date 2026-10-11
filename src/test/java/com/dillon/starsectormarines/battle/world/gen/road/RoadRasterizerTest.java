package com.dillon.starsectormarines.battle.world.gen.road;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import java.util.List;
import static com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadRasterizerTest {
    private Route straight(double wear) {return new Route("main",List.of(new Point(0,10),new Point(31,10)),7,2,wear,19,Material.ASPHALT);}
    private RoadPlan plan(double wear) {return new RoadPlan(32,24,List.of(straight(wear)));}
    @Test void straightRoadOwnsExactlyItsWidthAndShoulder() {
        var r=RoadRasterizer.rasterize(plan(0));
        for(int x=0;x<32;x++)for(int y=0;y<24;y++) {
            assertEquals(y>=7&&y<=13,r.road(x,y));
            assertEquals(y>=5&&y<=15,r.shoulder()[y*32+x]);
        }
    }
    @Test void wearNeverMovesPavementAndEveryPatchAgesMonotonically() {
        var fresh=RoadRasterizer.rasterize(plan(0));
        int[] previous=fresh.age();
        for(int percent=1;percent<=100;percent++) {
            var next=RoadRasterizer.rasterize(plan(percent/100d));
            assertArrayEquals(fresh.owner(),next.owner());assertArrayEquals(fresh.shoulder(),next.shoulder());
            for(int i=0;i<previous.length;i++)if(next.owner()[i]>=0){assertTrue(next.age()[i]>=previous[i]);if(percent==100)assertEquals(2,next.age()[i]);}
            previous=next.age();
        }
    }
    @Test void flushEndsPreserveRectangularApronsAndSurviveEditing() throws Exception {
        Route road=new Route("apron",List.of(new Point(8,6),new Point(8,17)),9,0,.3,1,Material.CONCRETE,EndCap.BUTT);
        RoadPlan p=new RoadPlan(24,24,List.of(road));
        assertEquals(p,RoadPlan.read(new JSONObject(p.json().toString())));
        assertEquals(EndCap.BUTT,road.withWear(.9).endCap());
        var raster=RoadRasterizer.rasterize(p);
        for(int y=0;y<24;y++) for(int x=0;x<24;x++)
            assertEquals(x>=4 && x<=12 && y>=6 && y<=17,raster.road(x,y),x+","+y);
    }
    @Test void laterMarkingsAndRoomsReplacePavementWithoutErasingOtherSurfaces() {
        var topology=new CellTopology(8,8);
        int pavement=topology.surfaceIndex("pavement.asphalt.worn"), indoor=topology.surfaceIndex("test.indoor");
        topology.setGroundKind(1,1,GroundKind.STRIPED);topology.setSurface(1,1,pavement);
        topology.setGroundKind(2,1,GroundKind.INDOOR);topology.setSurface(2,1,pavement);
        topology.setGroundKind(3,1,GroundKind.STREET);topology.setSurface(3,1,pavement);
        topology.setGroundKind(4,1,GroundKind.INDOOR);topology.setSurface(4,1,indoor);
        RoadRasterizer.clearReplacedSurfaces(topology,8,8);
        assertEquals(0,topology.getSurface(1,1));assertEquals(0,topology.getSurface(2,1));
        assertEquals(pavement,topology.getSurface(3,1));assertEquals(indoor,topology.getSurface(4,1));
    }
    @Test void crossingShoulderCannotEraseAnotherRoad() {
        Route cross=new Route("cross",List.of(new Point(15,0),new Point(15,23)),3,2,1,1,Material.CONCRETE);
        var r=RoadRasterizer.rasterize(new RoadPlan(32,24,List.of(straight(0),cross)));
        assertEquals(0,r.owner()[10*32+15]);assertEquals(1,r.owner()[2*32+15]);
        assertEquals(0,r.age()[10*32+15]);assertEquals(2,r.age()[2*32+15]);
    }
    @Test void splinePassesThroughAnchorsWithoutSamplingHoles() {
        Route curve=new Route("curve",List.of(new Point(0,5),new Point(8,5),new Point(19,20),new Point(31,18)),5,1,.5,4,Material.ASPHALT);
        var points=RoadRasterizer.sample(curve);
        for(Point p:curve.points())assertTrue(points.stream().anyMatch(q->Math.hypot(p.x()-q.x(),p.y()-q.y())<.00001));
        for(int i=1;i<points.size();i++)assertTrue(Math.hypot(points.get(i).x()-points.get(i-1).x(),points.get(i).y()-points.get(i-1).y())<.25);
        var r=RoadRasterizer.rasterize(new RoadPlan(32,24,List.of(curve)));
        for(Point p:points)if(p.x()>=0&&p.x()<31.5&&p.y()>=0&&p.y()<23.5)assertTrue(r.road((int)Math.round(p.x()),(int)Math.round(p.y())));
    }
    @Test void documentRoundTripPreservesShapeAndSeed() throws Exception {
        RoadPlan p=plan(.37);assertEquals(p,RoadPlan.read(new JSONObject(p.json().toString())));
    }
    @Test void invalidInputsFailBeforeSampling() {
        assertThrows(IllegalArgumentException.class,()->new Point(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new Route("bad",List.of(new Point(1,1),new Point(1,1)),5,1,0,1,Material.ASPHALT));
        assertThrows(IllegalArgumentException.class,()->new RoadPlan(8,8,List.of(straight(0))));
        assertThrows(IllegalArgumentException.class,()->new RoadPlan(32,24,List.of(straight(0),straight(1))));
    }
    @Test void missionContractAllowsWearButRejectsMovedRoads() {
        RoadPlan current=RoadCatalog.thunderRun();
        assertDoesNotThrow(()->RoadCatalog.validateThunderRun(new RoadPlan(current.width(),current.height(),
                current.routes().stream().map(r->r.withWear(1)).toList())));
        Route r=current.routes().get(0);
        Route narrow=new Route(r.id(),r.points(),r.width()-2,r.shoulder(),r.wear(),r.seed(),r.material());
        assertThrows(IllegalArgumentException.class,()->RoadCatalog.validateThunderRun(new RoadPlan(current.width(),current.height(),
                List.of(narrow,current.routes().get(1),current.routes().get(2)))));
    }
    @Test void refusedPlacementLeavesEarlierCellsUntouched() {
        var grid=new NavigationGrid(32,24);var topology=new CellTopology(32,24);
        for(int y=0;y<24;y++)for(int x=0;x<32;x++){grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,GroundKind.GRASS);}
        grid.setWalkable(30,10,false);topology.setWall(30,10,true);
        assertThrows(IllegalArgumentException.class,()->RoadRasterizer.stamp(plan(0),RoadRasterizer.rasterize(plan(0)),grid,topology));
        assertEquals(GroundKind.GRASS,topology.getGroundKind(1,10));assertEquals(0,topology.getSurface(1,10));
    }
}
