package com.dillon.starsectormarines.battle.world.tiles;

/**
 * One fixed-grid tile/autotile block's authoritative definition, loaded from a
 * {@code *.tileset.json} {@code blocks} entry into the {@link TileRegistry} and
 * addressed by stable string id. The grid-sheet counterpart to {@link TileDef}
 * (which covers sliced sheets).
 *
 * <p>Two shapes:
 * <ul>
 *   <li><b>Autotile</b> — an {@code (originCol, originRow)} on a fixed
 *       {@link #cellPx}px grid plus a {@link GridLayout} that resolves the final
 *       cell from a cell's wall-neighbor mask (e.g. wall / floor / road / striped).</li>
 *   <li><b>Variant pool</b> ({@link #isVariantPool}) — an explicit list of
 *       {@code {col,row}} {@link #cells}, picked by a stable {@code (x,y)} hash.
 *       Models the Floors/Water "center variant" grounds, whose production render
 *       only ever uses the neighbor-independent center pick (the flat-edges
 *       convention); a faithful port of {@code TileManifest.pickGrass/Stone/Sand/
 *       Water/BrickTile}'s center branch.</li>
 * </ul>
 *
 * <p>Replaces the hardcoded origin constants + {@code pickXxx} resolvers in
 * {@code TileManifest}. See {@code moddable-tilesets-nouns.md}.
 */
public final class GridBlockDef {

    public final String id;
    public final String sheetPath;
    public final int cellPx;
    public final int originCol;
    public final int originRow;
    /** Autotile resolver; {@code null} for a variant pool. */
    public final GridLayout layout;
    /**
     * Solid fill color (0xRRGGBB) the caller paints when {@link #resolve} returns
     * {@code null} (the open/enclosed case of a hollow-perimeter layout, whose
     * source center cell is transparent). {@code null} when not applicable.
     */
    public final Integer fillRgb;
    /** {@code {col,row}} variant cells for a variant pool; {@code null} for an autotile. */
    public final int[][] cells;
    /** Optional cardinal opening blocks paired with this wall or floor material. */
    public final String windowBlockId, doorBlockId;
    /** Optional wall-faces-4x4 debris paired with this material. */
    public final String rubbleBlockId;
    /** Optional transparent, coordinate-stable damage overlay pool for ground. */
    public final String damageBlockId;
    /**
     * For a variant pool laid as a pattern, the pattern's width in cells; zero for a hashed pool. A pattern's
     * {@link #cells} are row-major from the picture's top row, and the battle grid's y runs up the screen, so cell
     * {@code (x, y)} draws column {@code x mod cols} of row {@code rows - 1 - (y mod rows)}: one large seamless
     * picture (deck plates spanning several cells) repeats across the grid the right way up.
     */
    public final int patternCols;

    /** Autotile block (origin + named {@link GridLayout}). */
    public GridBlockDef(String id, String sheetPath, int cellPx,
                        int originCol, int originRow, GridLayout layout, Integer fillRgb) {
        this(id, sheetPath, cellPx, originCol, originRow, layout, fillRgb, null, null, null, null, null);
    }

    public GridBlockDef(String id, String sheetPath, int cellPx, int originCol, int originRow,
                        GridLayout layout, Integer fillRgb, String windowBlockId, String doorBlockId) {
        this(id, sheetPath, cellPx, originCol, originRow, layout, fillRgb, null, windowBlockId, doorBlockId, null, null);
    }

    public GridBlockDef(String id, String sheetPath, int cellPx, int originCol, int originRow,
                        GridLayout layout, Integer fillRgb, String windowBlockId, String doorBlockId,
                        String rubbleBlockId) {
        this(id, sheetPath, cellPx, originCol, originRow, layout, fillRgb,
                windowBlockId, doorBlockId, rubbleBlockId, null);
    }

    public GridBlockDef(String id, String sheetPath, int cellPx, int originCol, int originRow,
                        GridLayout layout, Integer fillRgb, String windowBlockId, String doorBlockId,
                        String rubbleBlockId, String damageBlockId) {
        this(id, sheetPath, cellPx, originCol, originRow, layout, fillRgb, null,
                windowBlockId, doorBlockId, rubbleBlockId, damageBlockId);
    }

    private GridBlockDef(String id, String sheetPath, int cellPx, int originCol, int originRow,
                         GridLayout layout, Integer fillRgb, int[][] cells,
                         String windowBlockId, String doorBlockId, String rubbleBlockId, String damageBlockId) {
        this.id = id;
        this.sheetPath = sheetPath;
        this.cellPx = cellPx;
        this.originCol = originCol;
        this.originRow = originRow;
        this.layout = layout;
        this.fillRgb = fillRgb;
        this.cells = cells;
        this.windowBlockId = windowBlockId;
        this.doorBlockId = doorBlockId;
        this.rubbleBlockId = rubbleBlockId;
        this.damageBlockId = damageBlockId;
        this.patternCols = 0;
    }

    private GridBlockDef(GridBlockDef pool, int patternCols) {
        if (pool.cells == null || patternCols <= 0 || pool.cells.length % patternCols != 0) {
            throw new IllegalArgumentException("block '" + pool.id + "': a pattern of " + patternCols
                    + " columns needs a variant pool whose cell count it divides");
        }
        this.id = pool.id;
        this.sheetPath = pool.sheetPath;
        this.cellPx = pool.cellPx;
        this.originCol = 0;
        this.originRow = 0;
        this.layout = null;
        this.fillRgb = null;
        this.cells = pool.cells;
        this.windowBlockId = pool.windowBlockId;
        this.doorBlockId = pool.doorBlockId;
        this.rubbleBlockId = pool.rubbleBlockId;
        this.damageBlockId = pool.damageBlockId;
        this.patternCols = patternCols;
    }

    /** This variant pool laid as a {@code cols}-wide pattern by position instead of picked by hash. */
    public GridBlockDef asPattern(int cols) {
        return new GridBlockDef(this, cols);
    }

    /** Variant pool — hash-picked from an explicit list of {@code {col,row}} cells. */
    public static GridBlockDef variantPool(String id, String sheetPath, int cellPx, int[][] cells) {
        return variantPool(id, sheetPath, cellPx, cells, null, null);
    }

    public static GridBlockDef variantPool(String id, String sheetPath, int cellPx, int[][] cells,
                                          String windowBlockId, String doorBlockId) {
        return variantPool(id, sheetPath, cellPx, cells, windowBlockId, doorBlockId, null);
    }

    public static GridBlockDef variantPool(String id, String sheetPath, int cellPx, int[][] cells,
                                          String windowBlockId, String doorBlockId, String rubbleBlockId) {
        return variantPool(id, sheetPath, cellPx, cells, windowBlockId, doorBlockId, rubbleBlockId, null);
    }

    public static GridBlockDef variantPool(String id, String sheetPath, int cellPx, int[][] cells,
                                          String windowBlockId, String doorBlockId, String rubbleBlockId,
                                          String damageBlockId) {
        return new GridBlockDef(id, sheetPath, cellPx, 0, 0, null, null, cells,
                windowBlockId, doorBlockId, rubbleBlockId, damageBlockId);
    }

    public boolean isVariantPool() { return cells != null; }

    /**
     * Resolves this block to a {@code {col, row}} cell. For a variant pool, picks
     * by the {@code (x, y)} hash, or by position for a {@linkplain #patternCols pattern} (neighbor mask ignored). For an autotile, runs
     * the {@link #layout} on the neighbor mask ({@code (x, y)} ignored), returning
     * {@code null} when the caller should paint {@link #fillRgb}.
     */
    public int[] resolve(boolean nWall, boolean sWall, boolean eWall, boolean wWall, int x, int y) {
        if (cells != null && patternCols > 0) {
            int rows = cells.length / patternCols;
            return cells[(rows - 1 - Math.floorMod(y, rows)) * patternCols + Math.floorMod(x, patternCols)];
        }
        if (cells != null) return cells[stableHash(x, y) % cells.length];
        return layout.resolve(originCol, originRow, nWall, sWall, eWall, wWall);
    }

    /** Mask-only resolve for autotile blocks (no variant coordinate). */
    public int[] resolve(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        return resolve(nWall, sWall, eWall, wWall, 0, 0);
    }

    /** Identical to {@code TileManifest.stableHash} — variant-pool parity depends on it matching. */
    private static int stableHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
    }
}
