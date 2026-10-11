package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** A breach draws the material and connected footprint the wall had before collapse. */
final class RubbleTiles {
    private RubbleTiles() {}

    static GridBlockDef skin(TileRegistry registry, CellTopology.WallRemnant remnant, String defaultWall) {
        if (registry == null || remnant == null) return null;
        GridBlockDef wall = registry.block(remnant.materialId() == null ? defaultWall : remnant.materialId());
        return wall == null ? null : registry.block(wall.rubbleBlockId);
    }

    static int[] cell(GridBlockDef skin, CellTopology.WallRemnant remnant) {
        int mask = remnant.exposedFaces();
        return skin.resolve((mask & CellTopology.WALL_DIR_N) != 0,
                (mask & CellTopology.WALL_DIR_S) != 0,
                (mask & CellTopology.WALL_DIR_E) != 0,
                (mask & CellTopology.WALL_DIR_W) != 0);
    }
}
