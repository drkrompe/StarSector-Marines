package com.dillon.starsectormarines.battle.world.tiles;

/**
 * One fixed-grid tile/autotile block's authoritative definition, loaded from a
 * {@code *.tileset.json} {@code blocks} entry into the {@link TileRegistry} and
 * addressed by stable string id. The grid-sheet counterpart to {@link TileDef}
 * (which covers sliced sheets): instead of a slicer {@code frame}, a grid block
 * pins a {@code (col, row)} origin on a fixed {@link #cellPx}px grid plus a
 * {@link GridLayout} that resolves the final cell from a cell's wall-neighbor
 * mask.
 *
 * <p>Replaces the hardcoded {@code (col, row)} origin constants + {@code pickXxx}
 * resolvers in {@code TileManifest} (moddable-tilesets Phase 1c). See
 * {@code roadmap/moddable-tilesets/overview.md}.
 */
public final class GridBlockDef {

    public final String id;
    public final String sheetPath;
    public final int cellPx;
    public final int originCol;
    public final int originRow;
    public final GridLayout layout;
    /**
     * Solid fill color (0xRRGGBB) the caller paints when {@link #resolve} returns
     * {@code null} (the enclosed case of a hollow-perimeter layout, whose source
     * center cell is transparent). {@code null} for layouts that never return null.
     */
    public final Integer fillRgb;

    public GridBlockDef(String id, String sheetPath, int cellPx,
                        int originCol, int originRow, GridLayout layout, Integer fillRgb) {
        this.id = id;
        this.sheetPath = sheetPath;
        this.cellPx = cellPx;
        this.originCol = originCol;
        this.originRow = originRow;
        this.layout = layout;
        this.fillRgb = fillRgb;
    }

    /**
     * Resolves this block to a {@code {col, row}} cell on the sheet for the given
     * four-neighbor mask, or {@code null} when the caller should paint
     * {@link #fillRgb} instead.
     */
    public int[] resolve(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        return layout.resolve(originCol, originRow, nWall, sWall, eWall, wWall);
    }
}
