package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.world.gen.CellContour;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.Arrays;

/** Retained, clipped texture geometry. Borrowed arrays are read-only. No render-time shape generation. */
public final class TerrainSurface {
    public static final int RAISED_LAYERS=8, LAYERS=16;
    /** The first of the two layers a top's cap (a tier's last band) is laid in, on either tier. */
    static final int CAP_LAYER=6;
    /** Whether a layer is a tier's cap: the first tier's, or the second's {@link #stacked} above it. */
    public static boolean isCap(int layer) { return layer%RAISED_LAYERS==CAP_LAYER || layer%RAISED_LAYERS==CAP_LAYER+1; }
    /** A zero surface selects the map renderer's ordinary grass, clipped to the summit. */
    public record Patch(int surface,float[] vertices) {}
    private final int width;
    private final Patch[][] patches;
    private final boolean[] excavated;
    private TerrainSurface(int width,Patch[][] patches,boolean[] excavated) {
        this.width=width;this.patches=patches;this.excavated=excavated;
    }
    public Patch patch(int x,int y,int layer) { return patches[layer][y*width+x]; }
    /** Center-sampled inaccessible depression, excluding its decorative bank apron. */
    public boolean isExcavated(int x,int y) { return excavated[y*width+x]; }
    /** Full cap cells can support props without their sprites spilling over a cliff face. */
    public boolean isFullSummit(int x, int y) {
        Patch cap = patch(x, y, 6);
        return cap != null && Arrays.equals(cap.vertices(), FULL_CELL);
    }
    private static final float[] FULL_CELL = {0, 0, 1, 0, 1, 1, 0, 1};
    public static TerrainSurface build(TerrainPlan plan,TerrainField field,CellTopology topology) {
        return build(plan.finish(),plan.faceDepth(),field,topology);
    }
    /** The same clipping for a field that did not come from brush strokes (raised ground). */
    public static TerrainSurface build(TerrainPlan.Finish finish,double faceDepth,TerrainField field,CellTopology topology) {
        int w=field.width(),h=field.height();Patch[][] patches=new Patch[LAYERS][w*h];
        boolean[] excavated=new boolean[w*h];
        String material=finish.materialPrefix();
        int talus=topology.surfaceIndex(material+"talus");
        int[] rock=new int[7];
        int cap=finish==TerrainPlan.Finish.GRASS_TOP?0:topology.surfaceIndex(material+"rock");
        for(int i=0;i<7;i++) {
            rock[i]=topology.surfaceIndex(material+"face."+i);
        }
        double[] depths={-.65,0,faceDepth*.68,faceDepth};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            double a=field.corner(x,y),b=field.corner(x+1,y),c=field.corner(x+1,y+1),d=field.corner(x,y+1);
            if(Math.max(Math.max(a,b),Math.max(c,d))<depths[0])continue;
            double gx=(b+c-a-d)*.5,gy=(c+d-a-b)*.5;
            double length=Math.max(.001,Math.hypot(gx,gy));
            // Light from the northwest. Surface normal points away from the mass.
            int light=(int)Math.round(3+2.4*(gx-gy)/length/Math.sqrt(2));light=Math.max(0,Math.min(6,light));
            int[] surfaces={talus,rock[Math.max(0,light-1)],rock[Math.min(6,light+1)],cap};
            for(int band=0;band<depths.length;band++) {
                // A fully opaque later band hides this band everywhere in the cell.
                if(band+1<depths.length && Math.min(Math.min(a,b),Math.min(c,d))>=depths[band+1])continue;
                double level=depths[band];float[] first,second;
                if(Math.min(Math.min(a,b),Math.min(c,d))>=level) {first=new float[]{0,0,1,0,1,1,0,1};second=null;}
                else {
                    first=CellContour.clip(new float[]{0,0,1,0,1,1},new double[]{a-level,b-level,c-level});
                    second=CellContour.clip(new float[]{0,0,1,1,0,1},new double[]{a-level,c-level,d-level});
                }
                if(first!=null)patches[band*2][y*w+x]=new Patch(surfaces[band],first);
                if(second!=null)patches[band*2+1][y*w+x]=new Patch(surfaces[band],second);
            }
        }
        // Excavation uses its own clipped bands, so raised output and summit
        // identity remain unchanged. Inner-bank normals face into the hollow;
        // the deepest band is shaded rock rather than an elevated grass cap.
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            excavated[y*w+x]=field.excavated(x,y);
            double a=field.excavationCorner(x,y),b=field.excavationCorner(x+1,y),
                    c=field.excavationCorner(x+1,y+1),d=field.excavationCorner(x,y+1);
            if(Math.max(Math.max(a,b),Math.max(c,d))<depths[0])continue;
            double gx=(b+c-a-d)*.5,gy=(c+d-a-b)*.5;
            double length=Math.max(.001,Math.hypot(gx,gy));
            int light=(int)Math.round(3-2.4*(gx-gy)/length/Math.sqrt(2));
            light=Math.max(0,Math.min(6,light));
            int[] surfaces={talus,rock[Math.min(6,light+1)],rock[Math.max(0,light-1)],rock[0]};
            for(int band=0;band<depths.length;band++) {
                if(band+1<depths.length && Math.min(Math.min(a,b),Math.min(c,d))>=depths[band+1])continue;
                double level=depths[band];float[] first,second;
                if(Math.min(Math.min(a,b),Math.min(c,d))>=level) {first=FULL_CELL.clone();second=null;}
                else {
                    first=CellContour.clip(new float[]{0,0,1,0,1,1},new double[]{a-level,b-level,c-level});
                    second=CellContour.clip(new float[]{0,0,1,1,0,1},new double[]{a-level,c-level,d-level});
                }
                if(first!=null)patches[RAISED_LAYERS+band*2][y*w+x]=new Patch(surfaces[band],first);
                if(second!=null)patches[RAISED_LAYERS+band*2+1][y*w+x]=new Patch(surfaces[band],second);
            }
        }
        return new TerrainSurface(w,patches,excavated);
    }
    /**
     * This surface with the given patches laid in one layer, replacing what the
     * layer held in those cells. A null entry leaves the cell alone. Raised
     * ground lays a ramp's floor this way, in a layer that draws after the
     * bands its cut would otherwise show.
     */
    public TerrainSurface withPatches(int layer,Patch[] cellPatches) {
        Patch[][] next=patches.clone();
        next[layer]=patches[layer].clone();
        for(int i=0;i<cellPatches.length;i++)if(cellPatches[i]!=null)next[layer][i]=cellPatches[i];
        return new TerrainSurface(width,next,excavated);
    }
    /**
     * Two tiers of raised ground as one surface: {@code lower}'s raised bands in
     * the raised layers, {@code upper}'s raised bands in the layers after them,
     * so an upper tier's apron and face draw over the lower tier's top. The
     * excavation layers are spent on it; a map stacking two tiers keeps no
     * excavation in the cells either tier draws in.
     */
    public static TerrainSurface stacked(TerrainSurface lower,TerrainSurface upper) {
        int cells=lower.excavated.length;Patch[][] out=new Patch[LAYERS][];
        for(int layer=0;layer<RAISED_LAYERS;layer++) {
            out[layer]=lower.patches[layer].clone();
            out[RAISED_LAYERS+layer]=upper.patches[layer].clone();
        }
        return new TerrainSurface(lower.width,out,new boolean[cells]);
    }
    /**
     * Two surfaces as one: a cell {@code over} draws anything in takes all of
     * its layers from {@code over}, every other cell keeps {@code base}'s.
     * Used to lay raised ground over a map that already has brush terrain.
     */
    public static TerrainSurface compose(TerrainSurface base,TerrainSurface over) {
        if(base==null)return over;
        int cells=base.excavated.length;Patch[][] out=new Patch[LAYERS][cells];boolean[] excavated=base.excavated.clone();
        for(int i=0;i<cells;i++) {
            boolean useOver=false;
            for(int layer=0;layer<LAYERS&&!useOver;layer++)useOver=over.patches[layer][i]!=null;
            for(int layer=0;layer<LAYERS;layer++)out[layer][i]=(useOver?over:base).patches[layer][i];
        }
        return new TerrainSurface(base.width,out,excavated);
    }
}
