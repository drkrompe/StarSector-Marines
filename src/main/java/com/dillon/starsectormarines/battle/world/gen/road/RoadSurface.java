package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Immutable subcell pavement boundary. Navigation continues to use the center-sampled raster. */
public final class RoadSurface {
    /** Borrowed vertex arrays are read-only; collecting a frame allocates no geometry. */
    public record Patch(int pavement,float[] first,float[] second) {}
    public record Underlay(GroundKind ground,int surface,boolean shoulder,boolean hard) {}
    public static final int MATERIALS=RoadPlan.Material.values().length;
    private final int width;
    private final Patch[][] patches;
    private final Underlay[] underlays;
    private RoadSurface(int width,Patch[][] patches,Underlay[] underlays) {
        this.width=width;this.patches=patches;this.underlays=underlays;
    }
    public Patch patch(int x,int y,int material) { return patches[material][y*width+x]; }
    public Underlay underlay(int x,int y) { return underlays[y*width+x]; }

    /** Sample signed distances at cell corners, preserving first-authored-road precedence. */
    public static RoadSurface build(RoadPlan plan,RoadRasterizer.Raster raster,CellTopology topology) {
        int w=plan.width(),h=plan.height(),vw=w+1,vertices=vw*(h+1),cells=w*h;
        double[][] fields=new double[MATERIALS][vertices];
        int[][] owner=new int[MATERIALS][cells];
        for(int m=0;m<MATERIALS;m++){Arrays.fill(fields[m],-1000);Arrays.fill(owner[m],-1);}
        for(int ri=0;ri<plan.routes().size();ri++) {
            var route=plan.routes().get(ri);if(!route.autoEdge())continue;
            int material=route.material().ordinal();
            double[] distance=distances(route,w,h);
            for(int vertex=0;vertex<vertices;vertex++) {
                double value=distance[vertex];
                for(int other=0;other<MATERIALS;other++)if(other!=material)
                    value=Math.min(value,-fields[other][vertex]);
                fields[material][vertex]=Math.max(fields[material][vertex],value);
            }
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(owner[material][y*w+x]<0 &&
                    Math.max(Math.max(distance[y*vw+x],distance[y*vw+x+1]),
                            Math.max(distance[(y+1)*vw+x],distance[(y+1)*vw+x+1]))>=0)
                owner[material][y*w+x]=ri;
        }
        Patch[][] patches=new Patch[MATERIALS][cells];
        Underlay[] underlays=new Underlay[cells];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int cell=y*w+x,navOwner=raster.owner()[cell];
            boolean hard=navOwner>=0 && !plan.routes().get(navOwner).autoEdge();
            boolean touched=navOwner>=0 || raster.shoulder()[cell];
            for(int m=0;m<MATERIALS;m++) {
                int ri=owner[m][cell];if(ri<0 || (hard && navOwner<ri))continue;
                double[] f=fields[m];
                double a=f[y*vw+x],b=f[y*vw+x+1],c=f[(y+1)*vw+x+1],d=f[(y+1)*vw+x];
                if(Math.max(Math.max(a,b),Math.max(c,d))<0)continue;
                var route=plan.routes().get(ri);
                if(navOwner>=0 && plan.routes().get(navOwner).material().ordinal()==m)
                    route=plan.routes().get(navOwner);
                int surface=topology.surfaceIndex(route.material().block(RoadRasterizer.age(route.wear(),route.seed(),x,y)));
                float[] first,second;
                if(Math.min(Math.min(a,b),Math.min(c,d))>=0) {
                    first=new float[]{0,0,1,0,1,1,0,1};second=null;
                } else {
                    first=clip(new float[]{0,0,1,0,1,1},new double[]{a,b,c});
                    second=clip(new float[]{0,0,1,1,0,1},new double[]{a,c,d});
                }
                patches[m][cell]=new Patch(surface,first,second);touched=true;
            }
            if(touched)underlays[cell]=new Underlay(topology.getGroundKind(x,y),topology.getSurface(x,y),
                    raster.shoulder()[cell],hard);
        }
        return new RoadSurface(w,patches,underlays);
    }

    private static double[] distances(RoadPlan.Route route,int w,int h) {
        int vw=w+1;
        var points=RoadRasterizer.sample(route);double radius=route.width()/2;
        double[] distance=new double[vw*(h+1)];Arrays.fill(distance,Double.POSITIVE_INFINITY);
        double[] progress=new double[distance.length];
        double travelled=0;
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var z=points.get(i);
            double dx=z.x()-a.x(),dy=z.y()-a.y(),length=Math.hypot(dx,dy);
            if(length<1e-9)continue;
            for(int y=Math.max(0,(int)Math.floor(Math.min(a.y(),z.y())-radius));y<=Math.min(h,(int)Math.ceil(Math.max(a.y(),z.y())+radius+1));y++)
                for(int x=Math.max(0,(int)Math.floor(Math.min(a.x(),z.x())-radius));x<=Math.min(w,(int)Math.ceil(Math.max(a.x(),z.x())+radius+1));x++) {
                    double t=((x-.5-a.x())*dx+(y-.5-a.y())*dy)/(length*length),clamped=Math.max(0,Math.min(1,t));
                    double d=Math.hypot(x-.5-a.x()-clamped*dx,y-.5-a.y()-clamped*dy);
                    int vertex=y*vw+x;
                    if(d<distance[vertex]) {
                        distance[vertex]=d;progress[vertex]=travelled+clamped*length;
                    }
                }
            travelled+=length;
        }
        var first=points.get(0);var second=points.get(1);
        var last=points.get(points.size()-1);var previous=points.get(points.size()-2);
        double startLength=Math.hypot(second.x()-first.x(),second.y()-first.y());
        double endLength=Math.hypot(last.x()-previous.x(),last.y()-previous.y());
        for(int vertex=0;vertex<distance.length;vertex++) {
            double value=radius-distance[vertex],x=vertex%vw-.5,y=vertex/vw-.5;
            if(route.endCap()==RoadPlan.EndCap.BUTT) {
                if(progress[vertex]<radius+1 && startLength>0)
                    value=Math.min(value,((x-first.x())*(second.x()-first.x())+(y-first.y())*(second.y()-first.y()))/startLength);
                if(travelled-progress[vertex]<radius+1 && endLength>0)
                    value=Math.min(value,((last.x()-x)*(last.x()-previous.x())+(last.y()-y)*(last.y()-previous.y()))/endLength);
            }
            distance[vertex]=value;
        }
        return distance;
    }
    /** Marching triangles: a clipped triangle has at most four vertices; triangles duplicate their last vertex. */
    static float[] clip(float[] triangle,double[] distances) {
        List<Float> out=new ArrayList<>(8);
        for(int i=0;i<3;i++) {
            int j=(i+1)%3;double a=distances[i],b=distances[j];
            if(a>=0){out.add(triangle[i*2]);out.add(triangle[i*2+1]);}
            if((a>=0)!=(b>=0)) {
                double t=a/(a-b);out.add((float)(triangle[i*2]+t*(triangle[j*2]-triangle[i*2])));
                out.add((float)(triangle[i*2+1]+t*(triangle[j*2+1]-triangle[i*2+1])));
            }
        }
        if(out.size()<6)return null;
        float[] result=new float[8];for(int i=0;i<8;i++)result[i]=out.get(i<out.size()?i:out.size()-2+i%2);
        return result;
    }
}
