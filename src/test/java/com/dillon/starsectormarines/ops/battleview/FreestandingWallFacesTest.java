package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreestandingWallFacesTest {

    /**
     * A window in a compound's wall stands to its sill, an embrasure in the wall, rather than leaving a gap in it: the
     * wall either side and the sill between are three runs, the sill the low one.
     */
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
        assertEquals(3, runs.size(), "the wall either side, the sill between");
        int sill = 0, wall = 0;
        for (FreestandingWalls.Run run : runs) {
            if (run.kind() == FreestandingWalls.Kind.MASONRY_SILL) sill += run.cells().length;
            else wall += run.cells().length;
        }
        assertEquals(2, sill);
        assertEquals(6, wall);
    }
}
