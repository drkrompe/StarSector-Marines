package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import static com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.*;

/** Catmull–Rom centerlines swept into a cell mask. No RNG stream or render-time decisions. */
public final class RoadRasterizer {
    private RoadRasterizer() {}
    public record Raster(int width, int height, int[] owner, boolean[] shoulder, int[] age) {
        public boolean road(int x,int y) { return owner[y*width+x]>=0; }
    }
    public static List<Point> sample(Route route) {
        List<Point> out=new ArrayList<>(); List<Point> p=route.points();
        out.add(p.get(0));
        for(int i=0;i<p.size()-1;i++) {
            Point a=p.get(Math.max(0,i-1)),b=p.get(i),c=p.get(i+1),d=p.get(Math.min(p.size()-1,i+2));
            // Bound subdivision by the entire control polygon, including long adjacent tangents.
            int steps=Math.max(1,(int)Math.ceil(8*(distance(a,b)+distance(b,c)+distance(c,d))));
            for(int n=1;n<=steps;n++) {
                double t=(double)n/steps;
                out.add(new Point(cubic(a.x(),b.x(),c.x(),d.x(),t),cubic(a.y(),b.y(),c.y(),d.y(),t)));
            }
        }
        return List.copyOf(out);
    }
    private static double distance(Point a,Point b) { return Math.hypot(a.x()-b.x(),a.y()-b.y()); }
    private static double cubic(double a,double b,double c,double d,double t) {
        return .5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
    }
    public static Raster rasterize(RoadPlan plan) {
        int w=plan.width(),h=plan.height(); int[] owner=new int[w*h],age=new int[w*h];
        Arrays.fill(owner,-1); boolean[] shoulders=new boolean[w*h];
        for(int ri=0;ri<plan.routes().size();ri++) {
            Route r=plan.routes().get(ri); List<Point> path=sample(r); double radius=r.width()/2, outer=radius+r.shoulder();
            double[] nearest = new double[w*h]; Arrays.fill(nearest, Double.POSITIVE_INFINITY);
            boolean[] clipped = new boolean[w*h];
            for(int j=1;j<path.size();j++) {
                Point a=path.get(j-1),b=path.get(j);
                for(int y=Math.max(0,(int)Math.floor(Math.min(a.y(),b.y())-outer));y<=Math.min(h-1,(int)Math.ceil(Math.max(a.y(),b.y())+outer));y++)
                    for(int x=Math.max(0,(int)Math.floor(Math.min(a.x(),b.x())-outer));x<=Math.min(w-1,(int)Math.ceil(Math.max(a.x(),b.x())+outer));x++) {
                        double dx=b.x()-a.x(),dy=b.y()-a.y(),den=dx*dx+dy*dy;
                        double projection=den==0?0:((x-a.x())*dx+(y-a.y())*dy)/den;
                        double t=Math.max(0,Math.min(1,projection));
                        double dist=Math.hypot(x-a.x()-t*dx,y-a.y()-t*dy); int index=y*w+x;
                        if(dist<nearest[index]) {
                            nearest[index]=dist;
                            clipped[index]=r.endCap()==EndCap.BUTT && ((j==1 && projection<0) || (j==path.size()-1 && projection>1));
                        }
                    }
            }
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int i=y*w+x;
                if(clipped[i]) continue;
                if(nearest[i]<outer) shoulders[i]=true;
                // First authored carriageway wins material; shoulders never overwrite roads.
                if(nearest[i]<radius && owner[i]<0) { owner[i]=ri; age[i]=age(r.wear(),r.seed(),x,y); }
            }
        }
        return new Raster(w,h,owner,shoulders,age);
    }
    /** Later authored floors/markings own their surface; never leave pavement inside a room. */
    public static void clearReplacedSurfaces(CellTopology topology, int width, int height) {
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            String id=topology.surfaceId(topology.getSurface(x,y));
            if(id!=null && id.startsWith("pavement.") && (topology.getGroundKind(x,y)!=GroundKind.STREET || topology.isWall(x,y)))
                topology.setSurface(x,y,0);
        }
    }
    public static int age(double wear,long seed,int x,int y) {
        double stage=wear*2; int lower=(int)stage;
        // Stable patches: moving the wear dial only advances a patch, never rerolls the map.
        long h=seed ^ ((long)Math.floorDiv(x,3)*0x9E3779B97F4A7C15L) ^ ((long)Math.floorDiv(y,3)*0xC2B2AE3D27D4EB4FL);
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L; h=(h^(h>>>27))*0x94D049BB133111EBL;
        double pick=((h^(h>>>31))>>>11)*0x1.0p-53;
        return Math.min(2,lower+(pick<stage-lower?1:0));
    }
    /** Apply before structures. Refuse to silently repave occupied or impassable cells. */
    public static void stamp(RoadPlan plan,Raster raster,NavigationGrid grid,CellTopology topology) {
        if(grid.getWidth()!=plan.width() || grid.getHeight()!=plan.height() || raster.width()!=plan.width() || raster.height()!=plan.height())
            throw new IllegalArgumentException("Road and map dimensions differ");
        TileRegistry registry=TileRegistry.installed();
        int[][] surfaces=new int[plan.routes().size()][3];
        for(int r=0;r<surfaces.length;r++) for(int a=0;a<3;a++) {
            String id=plan.routes().get(r).material().block(a);
            if(registry==null || registry.block(id)==null) throw new IllegalStateException("Missing pavement: "+id);
        }
        for(int y=0;y<plan.height();y++) for(int x=0;x<plan.width();x++) if(raster.shoulder()[y*plan.width()+x]) {
            if(!grid.isWalkable(x,y) || topology.isWall(x,y) || topology.isFixture(x,y) || topology.getGroundKind(x,y)==GroundKind.INDOOR)
                throw new IllegalArgumentException("Road overlaps occupied ground at "+x+","+y);
        }
        for(int r=0;r<surfaces.length;r++) for(int a=0;a<3;a++) surfaces[r][a]=topology.surfaceIndex(plan.routes().get(r).material().block(a));
        for(int y=0;y<plan.height();y++) for(int x=0;x<plan.width();x++) {
            int i=y*plan.width()+x,r=raster.owner()[i];
            if(r>=0) { topology.setGroundKind(x,y,GroundKind.STREET); topology.setSurface(x,y,surfaces[r][raster.age()[i]]); }
            else if(raster.shoulder()[i]) { topology.setGroundKind(x,y,GroundKind.STONE); topology.setSurface(x,y,0); }
        }
    }
}
