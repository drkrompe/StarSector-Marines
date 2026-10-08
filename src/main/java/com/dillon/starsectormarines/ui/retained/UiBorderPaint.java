package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.retained.style.UiBorderStyle;

import java.awt.Color;

/** Uniform CSS inset/outset borders, with mitred corners inside the border box. */
final class UiBorderPaint {
    private UiBorderPaint() {}

    static void bevel(CanvasContext canvas, float width, float height, float thicknessX, float thicknessY,
                      Color color, UiBorderStyle style) {
        float dx = Math.min(thicknessX, width * 0.5f);
        float dy = Math.min(thicknessY, height * 0.5f);
        if (dx <= 0f || dy <= 0f) return;
        Color light = shade(color, true);
        Color dark = shade(color, false);
        Color leading = style == UiBorderStyle.INSET ? dark : light;
        Color trailing = style == UiBorderStyle.INSET ? light : dark;
        canvas.fillQuad(0, 0, width, 0, width - dx, dy, dx, dy, leading);
        canvas.fillQuad(0, 0, dx, dy, dx, height - dy, 0, height, leading);
        canvas.fillQuad(0, height, dx, height - dy, width - dx, height - dy, width, height, trailing);
        canvas.fillQuad(width, 0, width, height, width - dx, height - dy, width - dx, dy, trailing);
    }

    static Color shade(Color color, boolean light) {
        return new Color(channel(color.getRed(), light), channel(color.getGreen(), light),
                channel(color.getBlue(), light), color.getAlpha());
    }

    private static int channel(int value, boolean light) {
        return light ? Math.round(value + (255 - value) * 0.32f) : Math.round(value * 0.38f);
    }
}
