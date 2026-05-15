package com.dillon.starsectormarines.battle;

/**
 * Hand-curated mapping from semantic battle-tile categories to source
 * {@code (col, row)} positions in {@code graphics/battle/scifi_space_rpg_tiles.png}.
 * Labels came from a manual pass through {@code TilesetDebugScreen} — any new
 * tile additions should go through the debug viewer first so indices match
 * what's visible on the sheet.
 *
 * <p>Wall tiles in this sheet are drawn as 2-tile-tall pairs (3/4 perspective):
 * the lower image-Y row is the wall's base, the upper image-Y row is the wall's
 * top edge. The auto-battler's nav grid only allocates one cell per wall, so
 * {@link TileFrame#heightTiles} = 2 frames render with a 2-tall source squashed
 * into one cell. Walls end up looking vertically compressed but read cleanly
 * as walls instead of getting cut off at the bottom edge.
 */
public final class TileManifest {

    public static final String SHEET = "graphics/battle/scifi_space_rpg_tiles.png";
    public static final int TILE_SIZE = 48;

    /**
     * Floor variants for walkable cells. Per-cell hash picks deterministically
     * from this pool so floors stay stable across frames and reload. Excludes:
     * <ul>
     *   <li>(2,1) and (3,1) — corner-wall variants (would carry stray wall geometry).</li>
     *   <li>(0,2) through (1,4) and (2,4) — text-stamped variants that read as
     *       visual noise at small cell sizes; dropped per playtest feedback.</li>
     * </ul>
     */
    public static final TileFrame[] FLOOR_POOL = {
            floor(0, 1), floor(1, 1),                           floor(4, 1), floor(5, 1), floor(6, 1), floor(7, 1),
                                      floor(2, 2), floor(3, 2), floor(4, 2), floor(5, 2), floor(6, 2), floor(7, 2),
                                      floor(2, 3), floor(3, 3), floor(4, 3), floor(5, 3), floor(6, 3), floor(7, 3),
                                                                floor(3, 4), floor(4, 4), floor(5, 4), floor(6, 4), floor(7, 4),
    };

    /**
     * The all-black tile at (0,0) — used as a "roof" for wall cells whose 4
     * cardinal neighbors are all walls (i.e., interior of a building). Stops
     * the wall variant pool from being stamped through the middle of a wall
     * block where there's no visible wall edge anyway.
     */
    public static final TileFrame INTERIOR_ROOF = new TileFrame(0, 0, 1);

    /**
     * Wall variants — 2-tile-tall in source (anchored at the top row), squashed
     * 2:1 into one cell when rendered. Pooled and picked per-cell by hash so
     * adjacent walls don't all share the same texture and a building reads as
     * varied masonry rather than a stamped repeat.
     *
     * <p>From the user's manual labeling: (0,7+0,8), (1,7+1,8), (2,7+2,8) are
     * the three 2-tall wall variants. No directional / autotile system yet —
     * the sheet's wall inventory doesn't form a clean 3×3 autotile block, so
     * variant variety is the cheap win and edge/corner pieces wait for a
     * future polish pass.
     */
    public static final TileFrame[] WALL_POOL = {
            wallPair(0, 7),
            wallPair(1, 7),
            wallPair(2, 7),
    };

    private TileManifest() {}

    private static TileFrame floor(int col, int row) {
        return new TileFrame(col, row, 1);
    }

    /** Constructs a 2-tile-tall frame anchored at {@code topRow} (image-Y, decreases upward in pixels). */
    private static TileFrame wallPair(int col, int topRow) {
        return new TileFrame(col, topRow, 2);
    }

    /**
     * Source-sheet region for a single semantic tile.
     *
     * <p>{@code row} is in image-Y coordinates (top of sheet = row 0). For
     * multi-tile-tall sprites (e.g., {@code heightTiles=2}), the frame spans
     * rows {@code [row, row + heightTiles)}; {@code row} is the topmost row.
     */
    public static final class TileFrame {
        public final int col;
        public final int row;
        public final int heightTiles;

        public TileFrame(int col, int row, int heightTiles) {
            this.col = col;
            this.row = row;
            this.heightTiles = heightTiles;
        }
    }
}
