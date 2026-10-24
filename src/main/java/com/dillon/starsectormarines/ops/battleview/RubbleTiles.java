package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** A breach draws the material and connected footprint the wall had before collapse. */
final class RubbleTiles {
    private RubbleTiles() {}

    /** {@code look} names the works style of the cell's lot (`fort-works.md`), whose rubble look it draws if any. */
    static GridBlockDef skin(TileRegistry registry, CellTopology.WallRemnant remnant, String defaultWall, String look) {
        if (registry == null || remnant == null) return null;
        GridBlockDef wall = registry.block(remnant.materialId() == null ? defaultWall : remnant.materialId());
        if (wall == null) return null;
        GridBlockDef styled = look == null ? null : registry.blockLook(wall.rubbleBlockId, look);
        return styled != null ? styled : registry.block(wall.rubbleBlockId);
    }

    static int[] cell(GridBlockDef skin, CellTopology.WallRemnant remnant) {
        int mask = remnant.exposedFaces();
        return skin.resolve((mask & CellTopology.WALL_DIR_N) != 0,
                (mask & CellTopology.WALL_DIR_S) != 0,
                (mask & CellTopology.WALL_DIR_E) != 0,
                (mask & CellTopology.WALL_DIR_W) != 0);
    }
}
