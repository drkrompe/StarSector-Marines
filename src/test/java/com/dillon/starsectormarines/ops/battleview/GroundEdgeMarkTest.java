package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A marked ground's edge is where that ground stops, not where the walls are. */
class GroundEdgeMarkTest {

    private static final String APRON = "apron.marked";

    @Test
    void aMarkedGroundEndsAtAWallOrAnyOtherGroundButNotAtTheMapEdge() {
        CellTopology topology = new CellTopology(3, 3);
        int apron = topology.surfaceIndex(APRON);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) topology.setSurface(x, y, apron);
        topology.setSurface(2, 1, topology.surfaceIndex("apron.keep-clear"));
        topology.setWall(1, 2, true);
        topology.setSurface(1, 0, 0);

        assertTrue(GroundTileSelector.endsGround(topology, null, APRON, 2, 1), "another block is an end");
        assertTrue(GroundTileSelector.endsGround(topology, null, APRON, 1, 2), "a wall is an end");
        assertTrue(GroundTileSelector.endsGround(topology, null, APRON, 1, 0), "unmarked ground is an end");
        assertFalse(GroundTileSelector.endsGround(topology, null, APRON, 0, 1), "the same apron runs on");
        assertFalse(GroundTileSelector.endsGround(topology, null, APRON, -1, 1), "the map edge is not an end");
    }

    @Test
    void edgeFacesAddressesSixteenMasksLikeWallFacesButIsNoWall() {
        GridLayout layout = GridLayout.fromJson("edge-faces-4x4");
        for (int mask = 0; mask < 16; mask++) {
            boolean n = (mask & 1) != 0, e = (mask & 2) != 0, s = (mask & 4) != 0, w = (mask & 8) != 0;
            assertArrayEquals(new int[]{4 + mask % 4, 8 + mask / 4}, layout.resolve(4, 8, n, s, e, w));
        }
        assertTrue(layout.marksGroundEdges());
        assertFalse(layout.isWall());
        assertEquals(4, layout.span());
    }
}
