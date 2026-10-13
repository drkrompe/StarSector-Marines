package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.BitmapFont;
import java.awt.Color;

/** Frozen bitmap label: live GL and offline evidence drain the same text, metrics and position. */
public record WorldLabel(BitmapFont font, String text, float centerX, float topY,
                         Color color, float alpha) implements Runnable {
    @Override public void run() {
        if (!font.ensureLoaded()) return;
        font.drawString(text, centerX - font.measureWidth(text) / 2, topY, color, alpha);
    }
}
