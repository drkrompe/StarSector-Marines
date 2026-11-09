package com.dillon.starsectormarines.battle.world.gen.nature;

import java.util.List;

/** Convex fractured bodies in cell coordinates, viewed from directly above. */
public enum MineralFormation implements NaturalPropShape {
    BOULDERS("boulders", 4, 3, Material.ROCK, List.of(
            new Facet(.25,.65, 1.1,.22, 2.05,.55, 2.3,1.35, 1.6,2.05, .4,1.8),
            new Facet(1.75,1.6, 2.45,.9, 3.55,1.2, 3.8,2.1, 3.15,2.75, 2.1,2.55))),
    SPIRES("spires", 3, 5, Material.ROCK, List.of(
            new Facet(.4,.3, 1.35,.15, 1.9,1.0, 1.5,2.1, .3,1.7),
            new Facet(1.1,1.55, 2.4,1.15, 2.8,2.1, 2.5,3.2, 1.4,3.6, .7,2.7),
            new Facet(.45,3.15, 1.4,2.95, 2.2,3.7, 1.85,4.7, .7,4.8, .2,4.0))),
    ICE_BLOCKS("ice-blocks", 4, 3, Material.ICE, List.of(
            new Facet(.2,.7, 1.1,.2, 2.2,.4, 2.5,1.45, 1.6,2.0, .3,1.8),
            new Facet(1.85,1.8, 2.5,.85, 3.7,1.15, 3.75,2.2, 2.95,2.8, 2.0,2.6))),
    ICE_PINNACLES("ice-pinnacles", 3, 5, Material.ICE, List.of(
            new Facet(.3,.7, 1.25,.15, 1.95,.85, 1.45,2.05, .25,1.65),
            new Facet(1.0,1.5, 2.5,1.25, 2.8,2.6, 1.9,3.55, .7,2.6),
            new Facet(.35,3.3, 1.3,2.85, 2.25,3.75, 1.8,4.8, .45,4.6, .2,4.0))),
    /**
     * A pair of dead husks on an irradiated world: squat, hollowed, bleached shells of whatever stood here, glazed
     * where the heat fused them. Drawn on their own sheet ({@code irradiated-formations}), not the planet sheet.
     */
    HUSKS("husks", 4, 3, Material.HUSK, List.of(
            new Facet(.3,.6, 1.15,.25, 2.1,.6, 2.25,1.45, 1.55,2.0, .45,1.75),
            new Facet(1.8,1.7, 2.5,.95, 3.6,1.25, 3.7,2.15, 3.05,2.7, 2.1,2.5))),
    /** Three tall dead husks, stripped and split, on an irradiated world. */
    HUSK_SPIRES("husk-spires", 3, 5, Material.HUSK, List.of(
            new Facet(.4,.35, 1.3,.2, 1.85,1.0, 1.45,2.05, .35,1.7),
            new Facet(1.1,1.6, 2.35,1.2, 2.75,2.1, 2.45,3.15, 1.45,3.55, .75,2.7),
            new Facet(.5,3.2, 1.4,3.0, 2.15,3.7, 1.8,4.65, .75,4.75, .25,4.0)));

    /** What a formation is made of: how it takes damage and which sheet draws it. */
    public enum Material { ROCK, ICE, HUSK }

    public record Facet(double... points) {
        public Facet { points = points.clone(); }
        @Override public double[] points() { return points.clone(); }
        /** Separating-axis intersection includes the complete visible body, not only its center. */
        boolean intersects(int x, int y) {
            double minX=Double.POSITIVE_INFINITY, minY=minX, maxX=-minX, maxY=-minX;
            for (int i=0;i<points.length;i+=2) {
                minX=Math.min(minX,points[i]); maxX=Math.max(maxX,points[i]);
                minY=Math.min(minY,points[i+1]); maxY=Math.max(maxY,points[i+1]);
            }
            if (maxX<=x || minX>=x+1 || maxY<=y || minY>=y+1) return false;
            for (int i=0;i<points.length;i+=2) {
                int j=(i+2)%points.length;
                double nx=points[j+1]-points[i+1], ny=points[i]-points[j];
                double lo=Double.POSITIVE_INFINITY, hi=-lo;
                for (int k=0;k<points.length;k+=2) {
                    double p=nx*points[k]+ny*points[k+1]; lo=Math.min(lo,p); hi=Math.max(hi,p);
                }
                double center=nx*(x+.5)+ny*(y+.5), radius=(Math.abs(nx)+Math.abs(ny))*.5;
                if (hi<=center-radius || lo>=center+radius) return false;
            }
            return true;
        }
    }
    private final String id;
    private final int width, height;
    private final Material material;
    private final List<Facet> facets;
    MineralFormation(String name,int width,int height,Material material,List<Facet> facets) {
        id="formation."+name; this.width=width; this.height=height; this.material=material; this.facets=facets;
    }
    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public Material material() { return material; }
    public boolean ice() { return material==Material.ICE; }
    /** Ice and husks are brittle; rock is not. */
    public int hitPoints() { return material==Material.ROCK?400:250; }
    public List<Facet> facets() { return facets; }
    public boolean blocks(int x,int y,int turns) {
        if(x<0 || y<0 || x>=width(turns) || y>=height(turns)) return false;
        int sx,sy;
        switch(Math.floorMod(turns,4)) {
            case 1 -> { sx=y; sy=height-1-x; }
            case 2 -> { sx=width-1-x; sy=height-1-y; }
            case 3 -> { sx=width-1-y; sy=x; }
            default -> { sx=x; sy=y; }
        }
        for(var facet:facets) if(facet.intersects(sx,sy)) return true;
        return false;
    }
}
