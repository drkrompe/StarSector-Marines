package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.turret.FortWorks;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Opening appearance follows its shell; navigation remains owned by the actual gap or barrier. */
final class OpeningTiles {
    private OpeningTiles() {}

    /** A contiguous cargo opening uses the actual shell ends as jambs, never one frame per cell. */
    static boolean wideDoorway(NavigationGrid grid, int x, int y) {
        if (!grid.isDoorway(x, y)) return false;
        for (Direction direction : Direction.CARDINALS) {
            int nx = x + direction.dx, ny = y + direction.dy;
            if (grid.inBounds(nx, ny) && grid.isDoorway(nx, ny)) return true;
        }
        return false;
    }

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
        // An opening in a fortification lot is drawn in the lot's works style where the style has a look for it.
        String look = FortWorks.lookAt(topology, x, y);
        // The two jamb walls are the most precise material authority, including accent-floor cases.
        Direction along = facing == Direction.N || facing == Direction.S ? Direction.E : Direction.N;
        GridBlockDef a = wallSkin(registry, topology, x + along.dx, y + along.dy, window, look);
        GridBlockDef b = wallSkin(registry, topology, x - along.dx, y - along.dy, window, look);
        if (a != null && b != null) return a.id.compareTo(b.id) <= 0 ? a : b;
        if (a != null || b != null) return a != null ? a : b;
        return paired(registry, registry.block(topology.getSurfaceId(x, y)), window, look);
    }

    private static GridBlockDef wallSkin(TileRegistry registry, CellTopology topology,
                                         int x, int y, boolean window, String look) {
        return wall(topology, x, y) == 0 ? null
                : paired(registry, registry.block(topology.getSurfaceId(x, y)), window, look);
    }

    private static GridBlockDef paired(TileRegistry registry, GridBlockDef material, boolean window, String look) {
        if (material == null) return null;
        String id = window ? material.windowBlockId : material.doorBlockId;
        GridBlockDef styled = look == null ? null : registry.blockLook(id, look);
        return styled != null ? styled : registry.block(id);
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
