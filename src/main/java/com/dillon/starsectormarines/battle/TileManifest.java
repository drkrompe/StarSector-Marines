package com.dillon.starsectormarines.battle;

/**
 * Hand-curated mapping from semantic battle-tile categories to source
 * {@code (col, row)} positions in {@code graphics/tilesets/urban-tileset.png}.
 *
 * <p>This sheet is a top-down 32px set with no 2-tall walls (the previous sheet
 * had wall pieces drawn in 3/4 perspective, which had to be squashed vertically
 * and looked awkward next to the marine sprites). All tiles here are 1×1, so
 * walls slot cleanly into one nav-grid cell.
 *
 * <p>Layout on the sheet (10×10 cells):
 * <ul>
 *   <li>{@code (0..2, 0..2)} — clean floor 3×3 (nine variants pulled into the floor pool)</li>
 *   <li>{@code (3..5, 0..2)} — clean wall 3×3 autotile, center cell empty</li>
 *   <li>{@code (6, 2)} — closed door with green LED (decorative doodad)</li>
 *   <li>{@code (7, 2)} — open door overhead (transparent overlay rendered above floor at doorway cells)</li>
 *   <li>{@code (0..2, 4..6)} — damaged floor 3×3 (rubble pool)</li>
 *   <li>{@code (3..5, 4..6)} — damaged wall 3×3 autotile, center cell empty (reserved for a future "damaged-but-standing" wall state)</li>
 *   <li>row 7 cols 6-9 — chairs + chest doodads</li>
 *   <li>row 3 cols 3-4, row 1 cols 8-9 — crate doodads</li>
 *   <li>remaining cells — grates, bookshelves, terminals, rubble piles (reserved for prop placement later)</li>
 * </ul>
 *
 * <p>Walls are picked via {@link #pickWallTile} from the 4-neighbor exposure
 * pattern — top-edge cells use the top row, left-edge use the left column,
 * corners use the matching corner cell. The center cell {@code (4, 1)} is
 * transparent in the source art and is reserved for the "all four neighbors
 * are walls" case — the renderer paints a solid color there instead of stamping
 * the empty tile.
 */
public final class TileManifest {

    public static final String SHEET = "graphics/tilesets/urban-tileset.png";
    public static final int TILE_SIZE = 32;

    /** Top-left cell of the clean-wall 3×3 autotile block. */
    private static final int WALL_COL_ORIGIN = 3;
    private static final int WALL_ROW_ORIGIN = 0;
    /** Top-left cell of the clean-floor 3×3 autotile block. Center (1,1) has no frame edges and renders for open floor. */
    private static final int FLOOR_COL_ORIGIN = 0;
    private static final int FLOOR_ROW_ORIGIN = 0;
    /** Top-left cell of the damaged-floor 3×3 autotile block. Same directional shape as clean floor, just at row 4. */
    private static final int RUBBLE_COL_ORIGIN = 0;
    private static final int RUBBLE_ROW_ORIGIN = 4;

    /**
     * Overhead-door overlay stamped on top of the floor for doorway cells (the
     * cells {@link com.dillon.starsectormarines.battle.UrbanMapGenerator#punchDoorway
     * punches} through building perimeters). Source art is mostly transparent
     * with a slim overhead bar — units walk underneath cleanly.
     */
    public static final TileFrame DOOR_OPEN = new TileFrame(7, 2);

    /**
     * Pool of decorative props scattered through hollow building interiors.
     * Visual-only — placed on walkable cells, never block movement. Mix of
     * crates, chairs, a chest, and the closed-door panel to read as
     * lived-in rooms.
     */
    public static final TileFrame[] DOODAD_POOL = {
            new TileFrame(8, 1), new TileFrame(9, 1),       // tan + amber crates
            new TileFrame(3, 3), new TileFrame(4, 3),       // gold + green crates
            new TileFrame(6, 7),                            // bench / paired-seat
            new TileFrame(7, 7),                            // brown chest
            new TileFrame(8, 7), new TileFrame(9, 7),       // small stools
            new TileFrame(6, 2),                            // closed-door panel (decoration only)
    };

    /**
     * Returns the wall tile for a cell given which cardinal neighbors are also
     * walls (or out-of-bounds — treated identically). Returns {@code null} when
     * the cell is fully interior (all four neighbors are walls) — the caller
     * paints a solid fill there because the source sheet's center cell is
     * transparent.
     *
     * <p>Mapping picks the column from east/west exposure and the row from
     * north/south independently, which yields the correct corner/edge tile for
     * every perimeter case our buildings produce. Stranded-wall edge cases
     * (e.g., a 1-wide wall strip exposed on opposite sides) fall through to a
     * matching edge tile rather than the empty center.
     */
    public static TileFrame pickWallTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        if (nWall && sWall && eWall && wWall) return null;

        int col = !wWall ? 0 : (!eWall ? 2 : 1);
        int row = !nWall ? 0 : (!sWall ? 2 : 1);

        // (1,1) is the empty middle of the source 3×3. Only reachable if both N+S
        // are walls AND both E+W are walls — which already returned null above.
        return new TileFrame(WALL_COL_ORIGIN + col, WALL_ROW_ORIGIN + row);
    }

    /**
     * Returns the clean-floor tile for a walkable cell given which cardinal
     * neighbors are walls (in-bounds non-walkable cells only — OOB is treated
     * as open so streets at the map edge stay center-tiled). The floor 3×3 has
     * its frame edges drawn on the side that touches a wall, so picking
     * directionally makes the floor "kiss" each wall it abuts. Cells with no
     * wall neighbors get the open-floor center {@code (1, 1)}.
     */
    public static TileFrame pickFloorTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);
        return new TileFrame(FLOOR_COL_ORIGIN + col, FLOOR_ROW_ORIGIN + row);
    }

    /**
     * Damaged-floor counterpart to {@link #pickFloorTile} — same directional
     * shape, drawn from the damaged 3×3 block. Used on rubble cells (former
     * walls that were knocked down by damage) so the breach reads as broken
     * masonry rather than fresh paneling.
     */
    public static TileFrame pickRubbleTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);
        return new TileFrame(RUBBLE_COL_ORIGIN + col, RUBBLE_ROW_ORIGIN + row);
    }

    private TileManifest() {}

    /** Source-sheet region for a single 1×1 tile. */
    public static final class TileFrame {
        public final int col;
        public final int row;

        public TileFrame(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }
}
