package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class WallFaceGeometryTest {
    @Test
    void allSixteenNeighbourShapesHaveTheirOwnCap() {
        GridBlockDef block = new GridBlockDef("wall", "wall.png", 64, 4, 8,
                GridLayout.WALL_FACES_4X4, null);
        var frames = new HashSet<String>();
        for (int mask = 0; mask < 16; mask++) {
            CellTopology topology = new CellTopology(3, 3);
            topology.setWall(1, 1, true);
            // A legacy authored exterior label cannot hide the other exposed faces.
            topology.setWallDirMask(1, 1, CellTopology.WALL_DIR_N);
            for (Direction d : Direction.CARDINALS) {
                topology.setWall(1 + d.dx, 1 + d.dy, (mask & (1 << d.bit())) == 0);
            }
            var tile = WallMasks.pickTile(topology, 1, 1, block);
            assertEquals(4 + mask % 4, tile.col);
            assertEquals(8 + mask / 4, tile.row);
            frames.add(tile.col + "," + tile.row);
        }
        assertEquals(16, frames.size(), "opposite faces, tees and pillars must not collapse to corners");
    }

    @Test
    void aBreachExposesTheNewEndOfTheAdjacentWall() {
        CellTopology topology = new CellTopology(3, 3);
        topology.setWall(1, 1, true);
        topology.setWall(0, 1, true);
        topology.setWall(2, 1, true);
        GridBlockDef block = TileRegistry.installed().block("colony.masonry-wall");
        var before = WallMasks.pickTile(topology, 1, 1, block);
        topology.setWall(2, 1, false);
        var after = WallMasks.pickTile(topology, 1, 1, block);
        assertNotEquals(before.col, after.col, "destroying the neighbour adds the east-facing end cap");
    }
}
