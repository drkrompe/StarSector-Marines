package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreestandingWallFacesTest {

    /** A window in a compound's wall stands with the wall, as one run, rather than leaving a gap in it. */
    @Test
    void aWindowStandsWithItsWall() {
        CellTopology ground = new CellTopology(10, 3);
        for (int x = 1; x < 9; x++) {
            ground.setGroundKind(x, 1, GroundKind.STONE);
            ground.setTag(x, 1, Tag.WALL, true);
        }
        ground.setTag(4, 1, Tag.WINDOW, true);
        ground.setTag(5, 1, Tag.WINDOW, true);
        List<FreestandingWalls.Run> runs = FreestandingWallFaces.runs(ground);
        assertEquals(1, runs.size(), "one wall, its windows in it");
        assertEquals(8, runs.get(0).cells().length);
    }
}
