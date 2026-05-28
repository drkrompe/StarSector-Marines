package com.dillon.starsectormarines.battle.world.tiles;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Test-side {@link TileSink} that writes through a
 * {@link java.awt.Graphics2D} against a {@code BufferedImage} sheet loaded
 * via {@code ImageIO}. Mirrors what the in-game sink will do through
 * Starsector's {@code SpriteAPI}; both share the source-rect math in
 * {@link SlicedTileDrawer}.
 *
 * <p>Top-down coordinates: the sink takes destination center + size in
 * image-pixel units. Y grows downward to match {@code Graphics2D}'s native
 * convention — the test caller handles any grid-y flip before passing in
 * {@code dstCy}.
 *
 * <p>The {@code alphaMult} argument is currently ignored — the preview
 * test always renders at full opacity. If a future test wants to fade
 * tiles in/out, route it through an {@link java.awt.AlphaComposite}.
 */
public final class Graphics2DTileSink implements TileSink {

    private final Graphics2D g;
    private final BufferedImage sheet;

    public Graphics2DTileSink(Graphics2D g, BufferedImage sheet) {
        this.g = g;
        this.sheet = sheet;
    }

    @Override
    public void drawSlice(int srcX, int srcY, int srcW, int srcH,
                          float dstCx, float dstCy, float dstW, float dstH,
                          float alphaMult) {
        int dstX1 = Math.round(dstCx - dstW / 2f);
        int dstY1 = Math.round(dstCy - dstH / 2f);
        int dstX2 = dstX1 + Math.round(dstW);
        int dstY2 = dstY1 + Math.round(dstH);
        g.drawImage(sheet,
                dstX1, dstY1, dstX2, dstY2,
                srcX,  srcY,  srcX + srcW, srcY + srcH,
                null);
    }
}
