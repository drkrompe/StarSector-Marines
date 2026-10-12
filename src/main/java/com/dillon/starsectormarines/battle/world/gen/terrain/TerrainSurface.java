package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.world.gen.CellContour;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.Arrays;

/** Retained, clipped texture geometry. Borrowed arrays are read-only. No render-time shape generation. */
public final class TerrainSurface {
    public static final int LAYERS=8;
    /** A zero surface selects the map renderer's ordinary grass, clipped to the summit. */
    public record Patch(int surface,float[] vertices) {}
    private final int width;
    private final Patch[][] patches;
    private TerrainSurface(int width,Patch[][] patches) { this.width=width;this.patches=patches; }
    public Patch patch(int x,int y,int layer) { return patches[layer][y*width+x]; }
    /** Full cap cells can support props without their sprites spilling over a cliff face. */
    public boolean isFullSummit(int x, int y) {
        Patch cap = patch(x, y, 6);
        return cap != null && Arrays.equals(cap.vertices(), FULL_CELL);
    }
    private static final float[] FULL_CELL = {0, 0, 1, 0, 1, 1, 0, 1};
    public static TerrainSurface build(TerrainPlan plan,TerrainField field,CellTopology topology) {
        int w=plan.width(),h=plan.height();Patch[][] patches=new Patch[LAYERS][w*h];
        String material=plan.finish().materialPrefix();
        int talus=topology.surfaceIndex(material+"talus");
        int[] rock=new int[7];
        int cap=plan.finish()==TerrainPlan.Finish.GRASS_TOP?0:topology.surfaceIndex(material+"rock");
        for(int i=0;i<7;i++) {
            rock[i]=topology.surfaceIndex(material+"face."+i);
        }
        double[] depths={-.65,0,plan.faceDepth()*.68,plan.faceDepth()};
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
        return new TerrainSurface(w,patches);
    }
}
