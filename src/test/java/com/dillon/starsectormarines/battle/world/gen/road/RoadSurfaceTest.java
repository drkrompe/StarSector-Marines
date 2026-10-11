package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadSurfaceTest {
    @Test void clippedTriangleKeepsInterpolatedBoundaryInsteadOfRoundingToCells() {
        assertArrayEquals(new float[]{.5f,0,1,0,1,1,.5f,.5f},
                RoadSurface.clip(new float[]{0,0,1,0,1,1},new double[]{-1,1,1}),1e-6f);
        assertNull(RoadSurface.clip(new float[]{0,0,1,0,1,1},new double[]{-2,-1,-3}));
    }
    @Test void curvedVisualSurfaceLeavesTheNavigationRasterAndUnderlyingGroundAlone() {
        Route route=new Route("curve",List.of(new Point(0,4),new Point(9,6),new Point(20,19)),5,1,.3,1,Material.ASPHALT);
        RoadPlan plan=new RoadPlan(24,24,List.of(route));
        var raster=RoadRasterizer.rasterize(plan);int[] owners=raster.owner().clone();
        var topology=new CellTopology(24,24);
        var before=topology.copyGroundKinds();
        var surface=RoadSurface.build(plan,raster,topology);
        assertArrayEquals(owners,raster.owner());assertArrayEquals(before,topology.copyGroundKinds());
        int partial=0;
        for(int y=0;y<24;y++)for(int x=0;x<24;x++) {
            var p=surface.patch(x,y,0);if(p==null)continue;
            double a=area(p.first())+area(p.second());
            assertTrue(a>=0 && a<=1.000001);
            if(a>.0001 && a<.9999)partial++;
        }
        assertTrue(partial>30,"curve must cross cell interiors, not just select full tiles");
    }
    @Test void asphaltAndConcreteMeetOnTheSameCurveWithoutGapsOrOverdraw() {
        Route road=new Route("road",List.of(new Point(0,4),new Point(23,18)),5,0,0,1,Material.ASPHALT);
        Route yard=new Route("yard",List.of(new Point(12,0),new Point(12,23)),10,0,0,2,Material.CONCRETE);
        var plan=new RoadPlan(24,24,List.of(road,yard));
        var surface=RoadSurface.build(plan,RoadRasterizer.rasterize(plan),new CellTopology(24,24));
        int joins=0;
        for(int y=3;y<21;y++)for(int x=9;x<16;x++) {
            var asphalt=surface.patch(x,y,0);var concrete=surface.patch(x,y,1);
            if(asphalt==null || concrete==null)continue;
            double a=area(asphalt.first())+area(asphalt.second());
            double b=area(concrete.first())+area(concrete.second());
            if(a>0 && b>0){assertEquals(1,a+b,1e-5);joins++;}
        }
        assertTrue(joins>5,"material joins need fractional coverage too");
    }
    @Test void flushEndsClipAtTheEndpointAndHardApronsKeepTheirFullCells() {
        Route route=new Route("flush",List.of(new Point(8,6),new Point(8,17)),9,0,0,1,Material.CONCRETE,EndCap.BUTT);
        var plan=new RoadPlan(24,24,List.of(route));
        var surface=RoadSurface.build(plan,RoadRasterizer.rasterize(plan),new CellTopology(24,24));
        assertNull(surface.patch(8,5,1));
        var first=surface.patch(8,6,1);
        assertEquals(.5,area(first.first())+area(first.second()),1e-6);
        Route hard=new Route(route.id(),route.points(),route.width(),0,0,1,Material.CONCRETE,EndCap.BUTT,false);
        plan=new RoadPlan(24,24,List.of(hard));
        surface=RoadSurface.build(plan,RoadRasterizer.rasterize(plan),new CellTopology(24,24));
        assertNull(surface.patch(8,6,1));assertTrue(surface.underlay(8,6).hard());
    }
    private static double area(float[] p) {
        if(p==null)return 0;
        double sum=0;
        for(int i=0;i<4;i++){int j=(i+1)%4;sum+=p[i*2]*p[j*2+1]-p[j*2]*p[i*2+1];}
        return Math.abs(sum)*.5;
    }
}
