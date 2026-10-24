package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.Arrays;
import java.util.List;
import com.dillon.starsectormarines.battle.world.gen.CellContour;

/** Immutable subcell pavement boundary. Navigation continues to use the center-sampled raster. */
public final class RoadSurface {
    /** Borrowed vertex arrays are read-only; collecting a frame allocates no geometry. */
    public record Patch(int pavement,float[] first,float[] second) {
        /** The share of the cell this patch covers; an edge on a cell boundary leaves a patch of none. */
        public double area() { return area(first)+area(second); }
        private static double area(float[] p) {
            if(p==null)return 0;
            double sum=0;
            for(int i=0;i<p.length/2;i++) { int j=(i+1)%(p.length/2); sum+=p[i*2]*p[j*2+1]-p[j*2]*p[i*2+1]; }
            return Math.abs(sum)/2;
        }
    }
    public record Underlay(GroundKind ground,int surface,boolean shoulder,boolean hard) {}
    public static final int MATERIALS=RoadPlan.Material.values().length;
    /** The patch layer road paint is drawn in, over every material. */
    public static final int MARKING=MATERIALS;
    /** Patch layers: one per material, then the paint. */
    public static final int LAYERS=MATERIALS+1;
    /** The paint's pool prefix; its ages follow the pavement's. */
    public static final String MARKING_BLOCK="pavement.marking.";

    /**
     * Road paint: a straight stroke from {@code a} to {@code b}, {@code width}
     * across and flush at both ends, laid over asphalt and nothing else. A
     * centre-line dash, a stop line or a crossing bar are all one of these.
     * Strokes run along a grid axis: a patch is one quad, which holds the
     * overlap of two axis-aligned rectangles and nothing slanted.
     */
    public record Marking(RoadPlan.Point a,RoadPlan.Point b,double width) {
        public Marking {
            if(a.x()!=b.x() && a.y()!=b.y()) throw new IllegalArgumentException("a marking runs along a grid axis: "+a+" "+b);
            if(!(width>0)) throw new IllegalArgumentException("marking width "+width);
        }
    }
    private final int width;
    private final Patch[][] patches;
    private final Underlay[] underlays;
    private RoadSurface(int width,Patch[][] patches,Underlay[] underlays) {
        this.width=width;this.patches=patches;this.underlays=underlays;
    }
    public Patch patch(int x,int y,int material) { return patches[material][y*width+x]; }
    public Underlay underlay(int x,int y) { return underlays[y*width+x]; }

    /**
     * The same pavement over different ground: each non-null {@code ground} cell
     * shows that underlay, whether or not any pavement covers it.
     */
    public RoadSurface withUnderlays(Underlay[][] ground) {
        Underlay[] next=underlays.clone();
        for(int x=0;x<ground.length;x++)for(int y=0;y<ground[0].length;y++)
            if(ground[x][y]!=null)next[y*width+x]=ground[x][y];
        return new RoadSurface(width,patches,next);
    }

    /**
     * The same pavement with nothing over the {@code cleared} cells (indexed {@code y * width + x}): no patch of any
     * material, no paint and no underlay, so each shows the ground the topology gives it. Ground a later hand has
     * rebuilt (a yard laid over a street's kerb) loses the kerb with the street.
     */
    public RoadSurface without(boolean[] cleared) {
        if(cleared.length!=underlays.length) throw new IllegalArgumentException("cleared mask is "+cleared.length+" cells, the surface "+underlays.length);
        Patch[][] next=new Patch[patches.length][];
        for(int m=0;m<patches.length;m++)next[m]=patches[m].clone();
        Underlay[] ground=underlays.clone();
        for(int cell=0;cell<cleared.length;cell++) {
            if(!cleared[cell])continue;
            for(int m=0;m<next.length;m++)next[m][cell]=null;
            ground[cell]=null;
        }
        return new RoadSurface(width,next,ground);
    }

    /** Sample signed distances at cell corners, preserving first-authored-road precedence. */
    public static RoadSurface build(RoadPlan plan,RoadRasterizer.Raster raster,CellTopology topology) {
        return build(plan.routes(),plan.width(),plan.height(),raster,topology);
    }

    /** Generated pavement is a finish over an already reserved, fully placed map. */
    public static RoadSurface generated(List<RoadPlan.Route> routes, CellTopology topology,
                                        Underlay[][] allowed) {
        return generated(routes,List.of(),topology,allowed);
    }

    /** As {@link #generated(List, CellTopology, Underlay[][])}, with paint over the finished asphalt. */
    public static RoadSurface generated(List<RoadPlan.Route> routes, List<Marking> markings, CellTopology topology,
                                        Underlay[][] allowed) {
        int w=allowed.length,h=allowed[0].length;
        RoadSurface result=build(routes,w,h,null,topology);
        // Expose the underlay in the corners cut off by a round end too. Cells
        // outside every sweep's bounds retain their original hard paving.
        for(var route:routes) {
            // Only straight runs own the whole bounding rectangle. A turn's
            // bounds can also contain a separate road that must keep its paint.
            if(route.points().size()!=2)continue;
            var samples=RoadRasterizer.sample(route);
            double radius=route.width()/2;
            double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
            for(var p:samples) { x0=Math.min(x0,p.x());x1=Math.max(x1,p.x());y0=Math.min(y0,p.y());y1=Math.max(y1,p.y()); }
            for(int y=Math.max(0,(int)Math.ceil(y0-radius));y<=Math.min(h-1,(int)Math.floor(y1+radius));y++)
                for(int x=Math.max(0,(int)Math.ceil(x0-radius));x<=Math.min(w-1,(int)Math.floor(x1+radius));x++)
                    if(allowed[x][y]!=null)result.underlays[y*w+x]=allowed[x][y];
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int cell=y*w+x;
            if(allowed[x][y]==null) {
                for(int m=0;m<LAYERS;m++)result.patches[m][cell]=null;
                result.underlays[cell]=null;
            } else if(result.underlays[cell]!=null) result.underlays[cell]=allowed[x][y];
        }
        paint(result,markings,topology);
        return result;
    }

    private static RoadSurface build(List<RoadPlan.Route> routes,int w,int h,
                                     RoadRasterizer.Raster raster,CellTopology topology) {
        int vw=w+1,vertices=vw*(h+1),cells=w*h;
        double[][] fields=new double[MATERIALS][vertices];
        int[][] owner=new int[MATERIALS][cells];
        for(int m=0;m<MATERIALS;m++){Arrays.fill(fields[m],-1000);Arrays.fill(owner[m],-1);}
        for(int ri=0;ri<routes.size();ri++) {
            var route=routes.get(ri);if(!route.autoEdge())continue;
            int material=route.material().ordinal();
            // Outside its sweep's bounds a route's field is -infinity and changes
            // nothing, so a city of short streets costs its streets, not its map.
            Sweep sweep=distances(route,w,h);
            double[] distance=sweep.distance();
            int sw=sweep.x1()-sweep.x0()+1;
            for(int vy=sweep.y0();vy<=sweep.y1();vy++)for(int vx=sweep.x0();vx<=sweep.x1();vx++) {
                int vertex=vy*vw+vx;
                double value=distance[(vy-sweep.y0())*sw+vx-sweep.x0()];
                for(int other=0;other<MATERIALS;other++)if(other!=material)
                    value=Math.min(value,-fields[other][vertex]);
                fields[material][vertex]=Math.max(fields[material][vertex],value);
            }
            for(int y=sweep.y0();y<sweep.y1();y++)for(int x=sweep.x0();x<sweep.x1();x++) {
                int local=(y-sweep.y0())*sw+x-sweep.x0();
                if(owner[material][y*w+x]<0 &&
                    Math.max(Math.max(distance[local],distance[local+1]),
                            Math.max(distance[local+sw],distance[local+sw+1]))>=0)
                owner[material][y*w+x]=ri;
            }
        }
        Patch[][] patches=new Patch[LAYERS][cells];
        Underlay[] underlays=new Underlay[cells];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int cell=y*w+x,navOwner=raster==null?-1:raster.owner()[cell];
            boolean hard=navOwner>=0 && !routes.get(navOwner).autoEdge();
            boolean touched=navOwner>=0 || (raster!=null && raster.shoulder()[cell]);
            boolean upperTaken=false,lowerTaken=false;
            for(int m=0;m<MATERIALS;m++) {
                int ri=owner[m][cell];if(ri<0 || (hard && navOwner<ri))continue;
                double[] f=fields[m];
                double a=f[y*vw+x],b=f[y*vw+x+1],c=f[(y+1)*vw+x+1],d=f[(y+1)*vw+x];
                // A triangle whose corners are all zero lies on two materials' shared
                // edge, where the corner values cannot say whose it is. It goes to the
                // first material that does not already cover it: kept by every
                // material, it put a half cell of a later one over an earlier one at a
                // corner; kept by none, it left a hole where only one of them lay.
                boolean upper=Math.max(a,Math.max(b,c))>0 || (a==0 && b==0 && c==0 && !upperTaken);
                boolean lower=Math.max(a,Math.max(c,d))>0 || (a==0 && c==0 && d==0 && !lowerTaken);
                if(!upper && !lower)continue;
                var route=routes.get(ri);
                if(navOwner>=0 && routes.get(navOwner).material().ordinal()==m)
                    route=routes.get(navOwner);
                int surface=topology.surfaceIndex(route.material().block(RoadRasterizer.age(route.wear(),route.seed(),x,y)));
                float[] first,second;
                if(Math.min(Math.min(a,b),Math.min(c,d))>=0 && upper && lower) {
                    first=new float[]{0,0,1,0,1,1,0,1};second=null;
                } else {
                    first=upper?clip(new float[]{0,0,1,0,1,1},new double[]{a,b,c}):null;
                    second=lower?clip(new float[]{0,0,1,1,0,1},new double[]{a,c,d}):null;
                    if(first==null && second==null)continue;
                }
                upperTaken|=upper;lowerTaken|=lower;
                patches[m][cell]=new Patch(surface,first,second);touched=true;
            }
            if(touched)underlays[cell]=new Underlay(topology.getGroundKind(x,y),topology.getSurface(x,y),
                    raster!=null && raster.shoulder()[cell],hard);
        }
        return new RoadSurface(w,patches,underlays);
    }

    /** A route's signed distance over the vertex rectangle {@code x0..x1, y0..y1} it can reach. */
    private record Sweep(int x0,int y0,int x1,int y1,double[] distance) {}

    private static Sweep distances(RoadPlan.Route route,int w,int h) {
        var points=RoadRasterizer.sample(route);double radius=route.width()/2;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(var p:points){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());}
        int x0=Math.max(0,(int)Math.floor(minX-radius)),y0=Math.max(0,(int)Math.floor(minY-radius));
        int x1=Math.min(w,(int)Math.ceil(maxX+radius+1)),y1=Math.min(h,(int)Math.ceil(maxY+radius+1));
        int sw=x1-x0+1,size=sw*(y1-y0+1);
        double[] distance=new double[size];Arrays.fill(distance,Double.POSITIVE_INFINITY);
        double[] progress=new double[size];
        double travelled=0;
        for(int i=1;i<points.size();i++) {
            var a=points.get(i-1);var z=points.get(i);
            double dx=z.x()-a.x(),dy=z.y()-a.y(),length=Math.hypot(dx,dy);
            if(length<1e-9)continue;
            for(int y=Math.max(y0,(int)Math.floor(Math.min(a.y(),z.y())-radius));y<=Math.min(y1,(int)Math.ceil(Math.max(a.y(),z.y())+radius+1));y++)
                for(int x=Math.max(x0,(int)Math.floor(Math.min(a.x(),z.x())-radius));x<=Math.min(x1,(int)Math.ceil(Math.max(a.x(),z.x())+radius+1));x++) {
                    double t=((x-.5-a.x())*dx+(y-.5-a.y())*dy)/(length*length),clamped=Math.max(0,Math.min(1,t));
                    double d=Math.hypot(x-.5-a.x()-clamped*dx,y-.5-a.y()-clamped*dy);
                    int vertex=(y-y0)*sw+x-x0;
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
        for(int vertex=0;vertex<size;vertex++) {
            double value=radius-distance[vertex],x=x0+vertex%sw-.5,y=y0+vertex/sw-.5;
            if(route.endCap()==RoadPlan.EndCap.BUTT) {
                if(progress[vertex]<radius+1 && startLength>0)
                    value=Math.min(value,((x-first.x())*(second.x()-first.x())+(y-first.y())*(second.y()-first.y()))/startLength);
                if(travelled-progress[vertex]<radius+1 && endLength>0)
                    value=Math.min(value,((last.x()-x)*(last.x()-previous.x())+(last.y()-y)*(last.y()-previous.y()))/endLength);
            }
            distance[vertex]=value;
        }
        return new Sweep(x0,y0,x1,y1,distance);
    }
    /**
     * Lay each stroke over cells the asphalt covers whole; paint never reaches a
     * ragged asphalt edge, a sidewalk or bare ground. The first stroke in a cell
     * keeps it. A stroke is a rectangle, so its share of a cell is the overlap
     * of two rectangles: exact however thin the line, where a patch clipped from
     * corner distances would lose any line narrower than the cell.
     */
    private static void paint(RoadSurface surface,List<Marking> markings,CellTopology topology) {
        int w=surface.width,h=surface.patches[0].length/w,asphalt=RoadPlan.Material.ASPHALT.ordinal();
        for(int mi=0;mi<markings.size();mi++) {
            var m=markings.get(mi);
            double half=m.width()/2,horizontal=m.a().y()==m.b().y()?half:0,vertical=half-horizontal;
            double left=Math.min(m.a().x(),m.b().x())-vertical,right=Math.max(m.a().x(),m.b().x())+vertical;
            double bottom=Math.min(m.a().y(),m.b().y())-horizontal,top=Math.max(m.a().y(),m.b().y())+horizontal;
            for(int y=Math.max(0,(int)Math.floor(bottom+.5));y<=Math.min(h-1,(int)Math.ceil(top-.5));y++)
                for(int x=Math.max(0,(int)Math.floor(left+.5));x<=Math.min(w-1,(int)Math.ceil(right-.5));x++) {
                    int cell=y*w+x;
                    Patch under=surface.patches[asphalt][cell];
                    if(surface.patches[MARKING][cell]!=null || under==null || under.area()<1-1e-6)continue;
                    // The overlap in the cell's own unit square, y up the cell as the patches are.
                    float x0=(float)Math.max(0,left-x+.5),x1=(float)Math.min(1,right-x+.5);
                    float y0=(float)Math.max(0,bottom-y+.5),y1=(float)Math.min(1,top-y+.5);
                    if(x1-x0<1e-4 || y1-y0<1e-4)continue;
                    int age=RoadRasterizer.age(.15,mi*0x9E3779B9L,x,y);
                    int pool=topology.surfaceIndex(MARKING_BLOCK+new String[]{"new","worn","cracked"}[age]);
                    surface.patches[MARKING][cell]=new Patch(pool,new float[]{x0,y0,x1,y0,x1,y1,x0,y1},null);
                }
        }
    }

    static float[] clip(float[] triangle,double[] distances) { return CellContour.clip(triangle,distances); }
}
