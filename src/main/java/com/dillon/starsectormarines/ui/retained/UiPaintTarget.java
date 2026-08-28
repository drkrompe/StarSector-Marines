package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;

import java.awt.Color;

/**
 * Backend boundary for painting one laid-out retained document.
 *
 * <p>All geometry is expressed in top-left, Y-down document coordinates. A
 * live Starsector target converts that geometry to the host UI pass; tooling
 * targets can rasterize the same traversal without loading the game.
 */
public interface UiPaintTarget {

    void begin();

    void end();

    float devicePixelRatio();

    void clip(Rect clip);

    void fill(Rect rect, Color color, float alphaMult);

    void outline(Rect rect, Color color, float width, float alphaMult);

    void text(BitmapFont font, String text, Rect lineBox, Color color, float alphaMult);

    CanvasContext canvasContext(CanvasMetrics metrics, Rect visibleBounds, float alphaMult);

    /**
     * Resolves one image asset path for this backend, or null when it cannot
     * be loaded. The default answer is null so a backend that has no image
     * story simply paints no images rather than failing a whole document.
     */
    default UiImage image(String path) {
        return null;
    }
}
