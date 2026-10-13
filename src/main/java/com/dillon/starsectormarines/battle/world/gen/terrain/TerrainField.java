package com.dillon.starsectormarines.battle.world.gen.terrain;

import java.util.Arrays;

/** Continuous capsule strokes combined in order; shared corner samples drive art and navigation. */
public final class TerrainField {
    private final int width, height;
    private final double[] values;
    private final double[] excavations;
    private TerrainField(int width,int height,double[] values,double[] excavations) {
        this.width=width;this.height=height;this.values=values;this.excavations=excavations;
    }
    public double corner(int x,int y) { return values[y*(width+1)+x]; }
    public double center(int x,int y) { return (corner(x,y)+corner(x+1,y+1))*.5; }
    public boolean raised(int x,int y) { return center(x,y)>=0; }
    public double excavationCorner(int x,int y) { return excavations[y*(width+1)+x]; }
    public boolean excavated(int x,int y) { return (excavationCorner(x,y)+excavationCorner(x+1,y+1))*.5>=0; }
    public boolean blocked(int x,int y) { return raised(x,y) || excavated(x,y); }
    public double maximum(int x,int y) { return Math.max(Math.max(corner(x,y),corner(x+1,y)),Math.max(corner(x,y+1),corner(x+1,y+1))); }
    public double excavationMaximum(int x,int y) {
        return Math.max(Math.max(excavationCorner(x,y),excavationCorner(x+1,y)),
                Math.max(excavationCorner(x,y+1),excavationCorner(x+1,y+1)));
    }
    public int width() { return width; }
    public int height() { return height; }
    public static TerrainField build(TerrainPlan plan) {
        int w=plan.width(),h=plan.height(),vw=w+1;
        double[] field=new double[vw*(h+1)]; Arrays.fill(field,-128);
        double[] excavations=new double[field.length]; Arrays.fill(excavations,-128);
        double[] strokeField=new double[field.length];
        for (var stroke:plan.strokes()) {
            Arrays.fill(strokeField,-128); double reach=stroke.radius()+4;
            var points=stroke.points();
            int minX=w,minY=h,maxX=0,maxY=0;
            for(int i=0;i<points.size();i++) {
                var a=points.get(Math.max(0,i-1));var b=points.get(i);
                int x0=Math.max(0,(int)Math.floor(Math.min(a.x(),b.x())-reach));
                int y0=Math.max(0,(int)Math.floor(Math.min(a.y(),b.y())-reach));
                int x1=Math.min(w,(int)Math.ceil(Math.max(a.x(),b.x())+reach));
                int y1=Math.min(h,(int)Math.ceil(Math.max(a.y(),b.y())+reach));
                minX=Math.min(minX,x0);minY=Math.min(minY,y0);maxX=Math.max(maxX,x1);maxY=Math.max(maxY,y1);
                double dx=b.x()-a.x(),dy=b.y()-a.y(),den=dx*dx+dy*dy;
                for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++) {
                    double px=x-.5-a.x(),py=y-.5-a.y(),t=den==0?0:Math.max(0,Math.min(1,(px*dx+py*dy)/den));
                    double distance=stroke.radius()-Math.hypot(px-t*dx,py-t*dy);
                    int at=y*vw+x;strokeField[at]=Math.max(strokeField[at],distance);
                }
            }
            for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++) {
                int at=y*vw+x;
                double phase=(plan.seed()&65535)*.013;
                double noise=Math.sin(x*.59+Math.sin(y*.37)+phase)*.65+Math.sin(y*1.13-x*.34+phase)*.25
                        +Math.sin(x*2.1+y*1.8+phase)*.10;
                double value=strokeField[at]+plan.ruggedness()*1.4*noise;
                switch (stroke.mode()) {
                    case RAISE -> field[at]=Math.max(field[at],value);
                    case EXCAVATE -> excavations[at]=Math.max(excavations[at],value);
                    case ERASE -> {
                        field[at]=Math.min(field[at],-value);
                        excavations[at]=Math.min(excavations[at],-value);
                    }
                }
            }
        }
        return new TerrainField(w,h,field,excavations);
    }
}
