package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.BitmapFont;
import java.awt.Color;

/**
 * Frozen bitmap label: live GL and offline evidence drain the same text, metrics and position.
 *
 * <p>A {@code halo} draws the text first in that colour at {@link #HALO_OFFSETS}, the same
 * ring the retained painter gives {@code text-shadow}, so a label with no panel behind it
 * (battle glass) still reads on any ground. Null draws none.
 */
public record WorldLabel(BitmapFont font, String text, float centerX, float topY,
                         Color color, float alpha, Color halo) implements Runnable {

    /** Battle glass's shadow, as {@code .hud-glass} declares it. */
    public static final Color GLASS_HALO = new Color(0, 0, 0, 0xd9);

    public static final float[][] HALO_OFFSETS = {
            {-1f, 0f}, {1f, 0f}, {0f, -1f}, {0f, 1f}, {1f, 1f}, {-1f, -1f}, {1f, -1f}, {-1f, 1f}, {1.5f, -1.5f}};

    public WorldLabel(BitmapFont font, String text, float centerX, float topY, Color color, float alpha) {
        this(font, text, centerX, topY, color, alpha, null);
    }

    @Override public void run() {
        if (!font.ensureLoaded()) return;
        float left = centerX - font.measureWidth(text) / 2;
        if (halo != null) {
            for (float[] offset : HALO_OFFSETS)
                font.drawString(text, left + offset[0], topY + offset[1], halo, alpha);
        }
        font.drawString(text, left, topY, color, alpha);
    }
}
