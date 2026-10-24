package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.fitting.BoardHover;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;

/**
 * What is under the pointer on a fitting board, named beside it: the Mech Lab's and the Boat
 * Deck's alike. A board shows each part by its art alone, so its name is a hover away rather
 * than a caption on every socket.
 *
 * <p>Set in the pages' own type: the part's name in the heading face, a detail line in the body
 * face, on the dark plate the stores sit on. Whether to show at all is {@link BoardHover}'s:
 * never while a part is held, so it cannot cover the socket the part is going into.
 */
final class BoardTooltip {

    private static final Color PLATE = new Color(0x0d, 0x12, 0x14, 240);
    private static final Color EDGE = new Color(0x59, 0x63, 0x62);
    private static final Color TITLE = new Color(0xe4, 0xe7, 0xdf);
    private static final Color DETAIL = new Color(0xa0, 0xad, 0xa8);
    private static final float PAD = 6f;
    private static final float OFFSET_X = 14f;
    private static final float OFFSET_Y = 18f;

    private BoardTooltip() { }

    /**
     * Draws {@code title}, and {@code detail} beneath it when there is one, beside the pointer
     * and kept inside the canvas. Nothing is drawn without a title, or while the board may
     * name nothing.
     */
    static void draw(CanvasContext c, BoardHover hover, float canvasWidth, float canvasHeight,
                     String title, String detail) {
        if (title == null || title.isEmpty() || !hover.names()) return;
        float pointerX = hover.x();
        float pointerY = hover.y();
        boolean hasDetail = detail != null && !detail.isEmpty();
        float width = Fonts.ORBITRON_12_BOLD.measureWidth(title);
        if (hasDetail) width = Math.max(width, Fonts.INSIGNIA_15_AA.measureWidth(detail));
        width += PAD * 2f;
        float height = PAD * 2f + Fonts.ORBITRON_12_BOLD.getLineHeight()
                + (hasDetail ? 2f + Fonts.INSIGNIA_15_AA.getLineHeight() : 0f);
        float x = pointerX + OFFSET_X;
        float y = pointerY + OFFSET_Y;
        if (x + width > canvasWidth - 2f) x = pointerX - OFFSET_X - width;
        if (y + height > canvasHeight - 2f) y = pointerY - OFFSET_Y - height;
        x = Math.max(2f, x);
        y = Math.max(2f, y);
        c.fillRect(x, y, width, height, PLATE);
        c.strokeRect(x, y, width, height, EDGE, 1f);
        c.text(Fonts.ORBITRON_12_BOLD, title, x + PAD, y + PAD, TITLE);
        if (hasDetail) {
            c.text(Fonts.INSIGNIA_15_AA, detail, x + PAD, y + PAD + Fonts.ORBITRON_12_BOLD.getLineHeight() + 2f, DETAIL);
        }
    }
}
