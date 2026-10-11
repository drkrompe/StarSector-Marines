package com.dillon.starsectormarines.ops.battleview;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadEdgeBlendTest {
    @Test void cardinalMaskTracksSurroundingTerrainAndProtectsBuildingsAndPaint() {
        var t=new CellTopology(3,3);
        t.setGroundKind(1,1,GroundKind.STREET);t.setTag(1,1,Tag.ROAD_EDGE_BLEND,true);
        t.setGroundKind(1,2,GroundKind.GRASS);t.setGroundKind(1,0,GroundKind.STONE);
        t.setGroundKind(2,1,GroundKind.COURTYARD);t.setGroundKind(0,1,GroundKind.SAND);
        assertEquals(15,RoadEdgeBlend.mask(t,1,1));
        t.setWall(1,2,true);t.setGroundKind(1,0,GroundKind.STRIPED);t.setGroundKind(2,1,GroundKind.STREET);
        assertEquals(8,RoadEdgeBlend.mask(t,1,1));
        assertEquals(-1,RoadEdgeBlend.dx(8));assertEquals(0,RoadEdgeBlend.dy(8));
        t.setGroundKind(0,1,GroundKind.WATER);assertEquals(0,RoadEdgeBlend.mask(t,1,1));
    }
    @Test void explicitHardEdgesAndReplacedRoadsHaveNoBlend() {
        var t=new CellTopology(2,2);t.setGroundKind(1,0,GroundKind.GRASS);t.setGroundKind(0,0,GroundKind.STREET);
        assertEquals(0,RoadEdgeBlend.mask(t,0,0));
        t.setTag(0,0,Tag.ROAD_EDGE_BLEND,true);assertEquals(4,RoadEdgeBlend.mask(t,0,0));
        t.setGroundKind(0,0,GroundKind.INDOOR);assertEquals(0,RoadEdgeBlend.mask(t,0,0));
        assertTrue(Tag.ROAD_EDGE_BLEND.drawn(),"toggling edging must invalidate resident ground");
    }
}
