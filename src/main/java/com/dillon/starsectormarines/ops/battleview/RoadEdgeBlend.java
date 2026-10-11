package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;

/** Cardinal road/outdoor boundary, independent of art and without changing navigation. */
final class RoadEdgeBlend {
    private RoadEdgeBlend() {}
    static int mask(CellTopology t,int x,int y) {
        if(!t.hasTag(x,y,Tag.ROAD_EDGE_BLEND) || t.isWall(x,y) || t.getGroundKind(x,y)!=GroundKind.STREET) return 0;
        return (background(t,x,y+1)?1:0)|(background(t,x,y-1)?2:0)
                |(background(t,x+1,y)?4:0)|(background(t,x-1,y)?8:0);
    }
    private static boolean background(CellTopology t,int x,int y) {
        if(!t.inBounds(x,y) || t.isWall(x,y)) return false;
        return switch(t.getGroundKind(x,y)) {
            case GRASS, DIRT, STONE, SAND, SNOW, COURTYARD, SIDEWALK, RUBBLE -> true;
            default -> false;
        };
    }
    // At a corner one adjacent surface underlies the tile. Roads, indoor floors,
    // water and landing markings never qualify as this background.
    static int dx(int mask) { return (mask&3)!=0 ? 0 : (mask&4)!=0 ? 1 : -1; }
    static int dy(int mask) { return (mask&1)!=0 ? 1 : (mask&2)!=0 ? -1 : 0; }
}
