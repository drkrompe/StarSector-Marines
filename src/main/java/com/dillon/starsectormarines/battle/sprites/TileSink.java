package com.dillon.starsectormarines.battle.sprites;

/**
 * Pixel-pushing abstraction shared by the in-game tile renderer and the
 * test-time preview renderer. The in-game implementation wraps a
 * Starsector {@code SpriteAPI}; the test implementation wraps a
 * {@link java.awt.Graphics2D} drawing into a {@code BufferedImage}.
 *
 * <p>Frame lookup, edge-inset rules, and tile-picker logic all sit above
 * this interface in {@link SlicedTileDrawer}. Keeping the sink dumb means
 * both backends execute the same source-rect math — a new tileset that
 * looks correct in the preview test will look correct in-game with high
 * confidence, since only the final pixel push differs.
 */
public interface TileSink {

    /**
     * Draw a sub-rect of the bound sheet to the given destination rect.
     * The sink is bound to one specific sheet at construction time; callers
     * pass source coordinates in that sheet's pixel space.
     *
     * @param srcX      source rect top-left x in sheet pixels (top-down)
     * @param srcY      source rect top-left y in sheet pixels (top-down)
     * @param srcW      source width in sheet pixels
     * @param srcH      source height in sheet pixels
     * @param dstCx     destination center x in device units
     * @param dstCy     destination center y in device units
     * @param dstW      destination width in device units
     * @param dstH      destination height in device units
     * @param alphaMult overall alpha multiplier in {@code [0..1]}
     */
    void drawSlice(int srcX, int srcY, int srcW, int srcH,
                   float dstCx, float dstCy, float dstW, float dstH,
                   float alphaMult);
}
