package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;

/**
 * The marks a selection draws on the world, shared by everything that draws one: rings round what is selected and
 * dotted tethers from what is selected to what it is attached to. A mark is coloured by whose it is: the company's
 * green, or a neutral yellow for the rescue's civilians.
 */
final class SelectionMarks {

    /** The company's tether green: an attached mech to its squad. */
    static final float COMPANY_R = 0.5f, COMPANY_G = 1f, COMPANY_B = 0.5f;
    /** A neutral selection, the rescue's civilians: yellow, apart from the company's green and amber. */
    static final float NEUTRAL_R = 1f, NEUTRAL_G = 0.92f, NEUTRAL_B = 0.2f;
    /** A tether's dot and the gap after it, in screen pixels, so it reads the same at every zoom. */
    static final float DOT_PX = 4f, GAP_PX = 5f;
    private static final float TETHER_WIDTH = 1.2f;

    private SelectionMarks() { }

    /** A ring of {@code radius} screen pixels round {@code (x, y)}. */
    static void ring(DrawList out, float x, float y, float radius, float width,
                     float r, float g, float b, float alpha) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            double next = (i + 1) * Math.PI * 2 / 32;
            out.addLine(RenderLayer.HIGHLIGHTS,
                    x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(next), y + radius * (float) Math.sin(next),
                    width, r, g, b, alpha);
        }
    }

    /** A dotted tether from {@code (x0, y0)} to {@code (x1, y1)}, in screen pixels. */
    static void tether(DrawList out, float x0, float y0, float x1, float y1, float r, float g, float b, float alpha) {
        for (float[] dot : dots(x0, y0, x1, y1, DOT_PX, GAP_PX)) {
            out.addLine(RenderLayer.HIGHLIGHTS, dot[0], dot[1], dot[2], dot[3], TETHER_WIDTH, r, g, b, alpha);
        }
    }

    /**
     * The dots of a dotted line, each {@code {fromX, fromY, toX, toY}}: {@code dot} long with {@code gap} after it,
     * starting at {@code (x0, y0)}, the last cut short at {@code (x1, y1)}. None for a line of no length.
     */
    static List<float[]> dots(float x0, float y0, float x1, float y1, float dot, float gap) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        List<float[]> out = new ArrayList<>();
        if (length <= 0f || dot <= 0f) return out;
        float ux = dx / length, uy = dy / length;
        for (float at = 0f; at < length; at += dot + gap) {
            float end = Math.min(length, at + dot);
            out.add(new float[]{x0 + ux * at, y0 + uy * at, x0 + ux * end, y0 + uy * end});
        }
        return out;
    }
}
