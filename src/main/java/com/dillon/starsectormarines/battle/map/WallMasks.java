package com.dillon.starsectormarines.battle.map;

import com.dillon.starsectormarines.battle.TileManifest;

/**
 * Shared utilities for the per-cell wall-direction mask carried by
 * {@link CellTopology}. Each mask bit ({@link CellTopology#WALL_DIR_N},
 * S, E, W) says "the building's exterior is on this side of the cell"
 * — set once at gen-time by the building stamper and read at render
 * time by the wall picker.
 *
 * <p>Pulled out of {@code BuildingShellCore} so the same code can run in
 * both the production carve path and hand-constructed preview test
 * scenes; the alternative is each caller reinventing the bit-test
 * boilerplate and the perimeter-tagging rule, which is exactly how the
 * preview tests' walls ended up rendering with the wrong directional
 * caps before this extraction.
 */
public final class WallMasks {

    private WallMasks() {}

    /**
     * Stamps the perimeter wall-direction mask on every cell of the
     * inclusive rect {@code [bl..br] × [bt..bb]}. Corner cells get two
     * bits; edge cells get one. Interior cells (if any) are untouched —
     * a fully-enclosed wall cell with no exterior face keeps its mask
     * at 0, which {@link TileManifest#pickWallTile} resolves to the
     * empty center tile (caller paints a solid fill there).
     *
     * <p>Math y-up convention: {@code bt} is the building's BOTTOM
     * (south, smaller Y) and {@code bb} is the TOP (north, larger Y).
     * {@link com.dillon.starsectormarines.battle.mapgen.BlockLeaf}
     * names use screen-space {@code top}/{@code bottom}, but the
     * topology is queried in math-space, so a cell at {@code y == bb}
     * sees north-of-it as out-of-building.
     */
    public static void stampPerimeter(CellTopology topology, int bl, int bt, int br, int bb) {
        for (int x = bl; x <= br; x++) {
            // Bottom row (y == bt) → south face is exterior.
            topology.orWallDirMask(x, bt, CellTopology.WALL_DIR_S);
            // Top row (y == bb) → north face is exterior.
            topology.orWallDirMask(x, bb, CellTopology.WALL_DIR_N);
        }
        for (int y = bt; y <= bb; y++) {
            // Left col (x == bl) → west face is exterior.
            topology.orWallDirMask(bl, y, CellTopology.WALL_DIR_W);
            // Right col (x == br) → east face is exterior.
            topology.orWallDirMask(br, y, CellTopology.WALL_DIR_E);
        }
    }

    /**
     * Converts a per-cell wall-direction mask into the matching tile
     * from the wall 3×3 autotile block. Returns {@code null} when the
     * cell is fully enclosed (no exterior face) — caller paints a
     * solid fill there because the source's center cell is transparent.
     * Equivalent to calling {@link TileManifest#pickWallTile} with the
     * four cardinal bits unpacked.
     */
    public static TileManifest.TileFrame pickTileFromMask(int wallDirMask) {
        return TileManifest.pickWallTile(
                (wallDirMask & CellTopology.WALL_DIR_N) != 0,
                (wallDirMask & CellTopology.WALL_DIR_S) != 0,
                (wallDirMask & CellTopology.WALL_DIR_E) != 0,
                (wallDirMask & CellTopology.WALL_DIR_W) != 0);
    }
}
