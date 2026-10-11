package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Opening appearance follows its shell; navigation remains owned by the actual gap or barrier. */
final class OpeningTiles {
    private OpeningTiles() {}

    static Direction facing(SharedEdgeBarrier barrier) {
        boolean ownerIsCanonical = barrier.structureCellX() == barrier.cellX()
                && barrier.structureCellY() == barrier.cellY();
        return ownerIsCanonical ? barrier.direction() : barrier.direction().opposite();
    }

    static Direction doorFacing(CellTopology topology, int x, int y) {
        int mask = topology.getWallDirMask(x, y);
        if ((mask & CellTopology.WALL_DIR_N) != 0) return Direction.N;
        if ((mask & CellTopology.WALL_DIR_E) != 0) return Direction.E;
        if ((mask & CellTopology.WALL_DIR_S) != 0) return Direction.S;
        if ((mask & CellTopology.WALL_DIR_W) != 0) return Direction.W;
        // Older maps did not retain a facing on gaps. Opposing jamb walls determine the axis.
        int horizontal = wall(topology, x - 1, y) + wall(topology, x + 1, y);
        int vertical = wall(topology, x, y - 1) + wall(topology, x, y + 1);
        return vertical > horizontal ? Direction.E : Direction.N;
    }

    static GridBlockDef skin(TileRegistry registry, CellTopology topology,
                             int x, int y, Direction facing, boolean window) {
        if (registry == null) return null;
        // The two jamb walls are the most precise material authority, including accent-floor cases.
        Direction along = facing == Direction.N || facing == Direction.S ? Direction.E : Direction.N;
        GridBlockDef a = wallSkin(registry, topology, x + along.dx, y + along.dy, window);
        GridBlockDef b = wallSkin(registry, topology, x - along.dx, y - along.dy, window);
        if (a != null && b != null) return a.id.compareTo(b.id) <= 0 ? a : b;
        if (a != null || b != null) return a != null ? a : b;
        return paired(registry, registry.block(topology.getSurfaceId(x, y)), window);
    }

    private static GridBlockDef wallSkin(TileRegistry registry, CellTopology topology,
                                         int x, int y, boolean window) {
        return wall(topology, x, y) == 0 ? null
                : paired(registry, registry.block(topology.getSurfaceId(x, y)), window);
    }

    private static GridBlockDef paired(TileRegistry registry, GridBlockDef material, boolean window) {
        return material == null ? null
                : registry.block(window ? material.windowBlockId : material.doorBlockId);
    }

    private static int wall(CellTopology topology, int x, int y) {
        return x >= 0 && y >= 0 && x < topology.getWidth() && y < topology.getHeight()
                && topology.isWall(x, y) ? 1 : 0;
    }

    static int[] cell(GridBlockDef skin, Direction facing) {
        return skin.resolve(facing == Direction.N, facing == Direction.S,
                facing == Direction.E, facing == Direction.W);
    }
}
