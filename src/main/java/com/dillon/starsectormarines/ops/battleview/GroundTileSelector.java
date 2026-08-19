package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;

/**
 * Shared selection rules for the sliced nature/urban-3 ground sheets.
 *
 * <p>The color and height passes must resolve the same tile id for a cell. Keep
 * the coordinate hash and sidewalk-neighbor rules here rather than maintaining
 * a second, nearly-identical copy in the micro-height sampler.
 */
final class GroundTileSelector {

    private GroundTileSelector() {}

    static String natureTileId(CellTopology.GroundKind kind, int x, int y) {
        if (kind == CellTopology.GroundKind.GRASS) return TileManifest.pickNatureGrassTileId(x, y);
        if (kind == CellTopology.GroundKind.DIRT) return TileManifest.pickNatureDirtTileId(x, y);
        return null;
    }

    static String urban3TileId(NavigationGrid grid, CellTopology topology,
                               String streetTileId, int x, int y) {
        CellTopology.GroundKind kind = topology.getGroundKind(x, y);
        if (kind == CellTopology.GroundKind.STREET) {
            if (!isSidewalkCell(grid, topology, x, y)) return streetTileId;
        } else if (kind != CellTopology.GroundKind.SIDEWALK) {
            return null;
        }
        return TileManifest.pickStreet3SidewalkFrame(
                !isSidewalkLikeCell(grid, topology, x, y + 1),
                !isSidewalkLikeCell(grid, topology, x, y - 1),
                !isSidewalkLikeCell(grid, topology, x + 1, y),
                !isSidewalkLikeCell(grid, topology, x - 1, y));
    }

    static boolean isSidewalkCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !topology.isStreet(x, y)) return false;
        return isInBoundsWall(topology, x + 1, y)
                || isInBoundsWall(topology, x - 1, y)
                || isInBoundsWall(topology, x, y + 1)
                || isInBoundsWall(topology, x, y - 1);
    }

    static boolean isSidewalkLikeCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return false;
        if (topology.getGroundKind(x, y) == CellTopology.GroundKind.SIDEWALK) return true;
        return isSidewalkCell(grid, topology, x, y);
    }

    static boolean isRoadBoundary(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        if (topology.isWall(x, y)) return true;
        return isSidewalkCell(grid, topology, x, y);
    }

    static boolean isInBoundsWall(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }
}
