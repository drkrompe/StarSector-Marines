package com.dillon.starsectormarines.battle.sprites;

import com.dillon.starsectormarines.battle.map.TileManifest;

/**
 * Fixed-grid counterpart to {@link SlicedTileDrawer}. Computes a tile's
 * source rect from {@code (col, row, tileSize)} and forwards through a
 * {@link TileSink}, applying an edge inset the caller passes in.
 *
 * <p>The inset rule lives at the call site rather than on the tile because
 * fixed-grid sheets reuse the same cell for both ground-field tiles and
 * standalone props — e.g. cell (16, 2) on the road sheet is both the LZ
 * marker ground decal AND the LZ pad doodad, drawn through different code
 * paths in {@link com.dillon.starsectormarines.ops.BattleScreen}. The
 * caller knows which one it's rendering and passes the appropriate inset.
 *
 * <p>Shared inset constants:
 * <ul>
 *   <li>{@link #GROUND_INSET_PX_LARGE} — 2px, for 32px sheets (urban, road).</li>
 *   <li>{@link #GROUND_INSET_PX_SMALL} — 1px, for 16px sheets (Floors,
 *       Water). 12.5% per-axis is the largest crop these can take before
 *       the sampler reads into adjacent tiles.</li>
 *   <li>{@link #OVERLAY_INSET_PX} — 0, for doodads / overlays whose edge
 *       pixels are real content.</li>
 * </ul>
 *
 * <p>BattleScreen references these constants directly so the in-game and
 * test renderers can never drift on the inset value.
 */
public final class FixedGridTileDrawer {

    /** Default inset for 32px source sheets — confirmed 2px essentially eliminates the bilinear-sampler seam without cropping perceptible content. */
    public static final int GROUND_INSET_PX_LARGE = 2;

    /** Default inset for 16px source sheets. 1px keeps the sampler off the boundary texel without throwing away 25% of the per-axis content area. */
    public static final int GROUND_INSET_PX_SMALL = 1;

    /** Inset for standalone overlay sprites (doodads, DOOR_OPEN) — their edge pixels are visible art, not part of a tiling field. */
    public static final int OVERLAY_INSET_PX = 0;

    private final int tileSize;

    public FixedGridTileDrawer(int tileSize) {
        this.tileSize = tileSize;
    }

    /** Default ground inset for this drawer's tile size. Callers rendering autotile-field cells pass this; overlay/doodad callers pass {@link #OVERLAY_INSET_PX}. */
    public int defaultGroundInsetPx() {
        return tileSize >= 32 ? GROUND_INSET_PX_LARGE : GROUND_INSET_PX_SMALL;
    }

    public int tileSize() {
        return tileSize;
    }

    /**
     * Draw {@code frame} centered at {@code (dstCx, dstCy)} sized
     * {@code (dstW, dstH)}. The source rect is computed as the cell at
     * {@code (frame.col, frame.row)} on a {@link #tileSize}px grid, with
     * {@code srcEdgeInsetPx} cropped from each side. No-op if either
     * argument is null.
     */
    public void draw(TileSink sink, TileManifest.TileFrame frame,
                     float dstCx, float dstCy, float dstW, float dstH,
                     float alphaMult, int srcEdgeInsetPx) {
        if (sink == null || frame == null) return;
        int srcX = frame.col * tileSize + srcEdgeInsetPx;
        int srcY = frame.row * tileSize + srcEdgeInsetPx;
        int srcW = Math.max(1, tileSize - 2 * srcEdgeInsetPx);
        int srcH = Math.max(1, tileSize - 2 * srcEdgeInsetPx);
        sink.drawSlice(srcX, srcY, srcW, srcH, dstCx, dstCy, dstW, dstH, alphaMult);
    }
}
