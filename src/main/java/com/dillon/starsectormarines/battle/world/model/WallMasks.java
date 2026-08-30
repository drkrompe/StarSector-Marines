package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;

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
     * {@link com.dillon.starsectormarines.battle.world.gen.BlockLeaf}
     * names use screen-space {@code top}/{@code bottom}, but the
     * topology is queried in math-space, so a cell at {@code y == bb}
     * sees north-of-it as out-of-building.
     */
    /**
     * Give a face to every wall cell nobody claimed.
     *
     * <p>A wall whose mask is still zero resolves to the block's centre cell,
     * which is transparent — so it draws nothing at all. That is right for a
     * building, where an interior wall cell sits under the roof and is never
     * seen. It is wrong for a ship, whose bulkheads are one cell thick between
     * two open spaces and are looked straight down at: <b>every wall on a
     * generated deck rendered as empty air</b> until this ran.
     *
     * <p>The rule is that a face is exterior where the neighbour is not a wall,
     * which for a bulkhead means both of its long sides are. A horizontal run
     * therefore takes the block's north-edge tile and its corners take corner
     * tiles, which is what a wall seen from above should look like.
     *
     * <p><b>Only cells at zero are touched.</b> A building stamper has already
     * said which faces of its walls are exterior, and that answer is about the
     * building rather than about what happens to abut it — re-deriving it here
     * would overwrite a considered mask with a guess. Nothing that renders today
     * changes; only what renders as nothing.
     */
    public static void stampUnclaimed(CellTopology topology) {
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (!topology.isWall(x, y) || topology.getWallDirMask(x, y) != 0) continue;
                int mask = 0;
                if (!isWallOrOutside(topology, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
                if (!isWallOrOutside(topology, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
                if (!isWallOrOutside(topology, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
                if (!isWallOrOutside(topology, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
                topology.orWallDirMask(x, y, mask);
            }
        }
    }

    /**
     * Whether this side has nothing to show a face to.
     *
     * <p>Off the map counts as wall rather than as open, so a bulkhead running
     * along the edge of the grid does not grow a cap pointing at nothing.
     */
    private static boolean isWallOrOutside(CellTopology topology, int x, int y) {
        return !topology.inBounds(x, y) || topology.isWall(x, y);
    }

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
     *
     * <p>The caller supplies the wall block, because which block is the wall is
     * a {@link SurfaceRole#WALL} mapping question and the render systems that
     * hold the mapping resolve it once per pass rather than once per cell.
     * A {@code null} block falls back to the static
     * {@link TileManifest#pickWallTile}.
     *
     * <p>The fallback is a last resort, not a second authority. It carries the
     * origin the sheet was hand-cut at, and the atlas is now packed by the
     * tileset exporter, which is free to move the block. Prefer the block.
     */
    public static TileManifest.TileFrame pickTileFromMask(int wallDirMask, GridBlockDef wall) {
        boolean n = (wallDirMask & CellTopology.WALL_DIR_N) != 0;
        boolean s = (wallDirMask & CellTopology.WALL_DIR_S) != 0;
        boolean e = (wallDirMask & CellTopology.WALL_DIR_E) != 0;
        boolean w = (wallDirMask & CellTopology.WALL_DIR_W) != 0;
        if (wall == null) return TileManifest.pickWallTile(n, s, e, w);
        int[] c = wall.resolve(n, s, e, w);
        return c == null ? null : new TileManifest.TileFrame(c[0], c[1]);
    }
}
