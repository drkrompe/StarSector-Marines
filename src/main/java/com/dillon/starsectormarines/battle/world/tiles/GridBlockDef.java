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
 * {@code TileManifest} (moddable-tilesets Phase 1c). See
 * {@code roadmap/moddable-tilesets/overview.md}.
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

    /** Autotile block (origin + named {@link GridLayout}). */
    public GridBlockDef(String id, String sheetPath, int cellPx,
                        int originCol, int originRow, GridLayout layout, Integer fillRgb) {
        this(id, sheetPath, cellPx, originCol, originRow, layout, fillRgb, null);
    }

    private GridBlockDef(String id, String sheetPath, int cellPx, int originCol, int originRow,
                         GridLayout layout, Integer fillRgb, int[][] cells) {
        this.id = id;
        this.sheetPath = sheetPath;
        this.cellPx = cellPx;
        this.originCol = originCol;
        this.originRow = originRow;
        this.layout = layout;
        this.fillRgb = fillRgb;
        this.cells = cells;
    }

    /** Variant pool — hash-picked from an explicit list of {@code {col,row}} cells. */
    public static GridBlockDef variantPool(String id, String sheetPath, int cellPx, int[][] cells) {
        return new GridBlockDef(id, sheetPath, cellPx, 0, 0, null, null, cells);
    }

    public boolean isVariantPool() { return cells != null; }

    /**
     * Resolves this block to a {@code {col, row}} cell. For a variant pool, picks
     * by the {@code (x, y)} hash (neighbor mask ignored). For an autotile, runs
     * the {@link #layout} on the neighbor mask ({@code (x, y)} ignored), returning
     * {@code null} when the caller should paint {@link #fillRgb}.
     */
    public int[] resolve(boolean nWall, boolean sWall, boolean eWall, boolean wWall, int x, int y) {
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
