package com.dillon.starsectormarines.battle.world.gen;

import java.util.ArrayList;
import java.util.List;

/** Subcell contour geometry shared by authored road and terrain surfaces. */
public final class CellContour {
    private CellContour() {}
    /** Marching triangles: a clipped triangle has at most four vertices; triangles duplicate their last vertex. */
    public static float[] clip(float[] triangle,double[] distances) {
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
