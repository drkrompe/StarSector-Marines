package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground body's aim reticle as screen strokes: a short thin tick either side of a neutral centre dot on the aim
 * point, the left tick for the left mouse button and the right tick for the right, each in its {@link Mark}'s
 * colour. A tick also carries its button's landing spread, for the picks that throw rockets or salvos. It stays quiet because the player looks straight through it: no backing and no end caps. Pattern carries
 * what hue does, so the marks read without colour too: white and yellow are solid, red (past range) is dotted, and
 * grey (will not fire) is two wide dashes. The strokes are pure geometry, drawn by the GL panel and by review frames
 * alike.
 */
public final class DirectControlReticle {
    private DirectControlReticle() {}

    /*
     * Partly transparent, so the ticks stay quiet over the point the player is watching. Yellow and red keep a
     * little more than white, since a falling-off or dotted tick carries less ink; grey sits lowest.
     */
    public static final Color GOOD = new Color(0xF4, 0xF6, 0xF8, alpha(0.80f));
    public static final Color FALLING_OFF = new Color(0xFF, 0xD2, 0x4A, alpha(0.85f));
    public static final Color WASTED = new Color(0xFF, 0x5A, 0x4A, alpha(0.85f));
    public static final Color HELD = new Color(0xA4, 0xAA, 0xB0, alpha(0.60f));

    private static int alpha(float share) { return Math.round(share * 255f); }

    /** The colour a mark draws in, or null for {@link Mark#NONE}. */
    public static Color color(Mark mark) {
        return switch (mark) {
            case GOOD -> GOOD;
            case FALLING_OFF -> FALLING_OFF;
            case WASTED -> WASTED;
            case HELD -> HELD;
            case NONE -> null;
        };
    }

    private static final float WIDTH = 1.5f;
    private static final float INNER = 5f;
    private static final float OUTER = 14f;
    /** Past range: three short dots along the tick. */
    private static final int DOTS = 3;
    private static final float DOT = 2f;
    /** Will not fire: two wide dashes. */
    private static final int DASHES = 2;
    private static final float DASH = 3.5f;

    /**
     * How a tick shows its button's landing spread, on trial for the owner's pick (picks question
     * {@code aim-reticle-spread}); the look not chosen is removed with this enum.
     */
    public enum SpreadLook {
        /** The tick grows outward with the landing radius, up to {@link #MAX_GROWTH_PX}. */
        LONGER,
        /** The tick keeps its length and opens away from the dot to the landing radius, up to {@link #MAX_BLOOM_PX}. */
        BLOOM
    }

    /** The look the live screen draws. */
    public static final SpreadLook LIVE = SpreadLook.BLOOM;

    /** A blooming tick's inner end never sits further out than this, so a long throw cannot open it into noise. */
    static final float MAX_BLOOM_PX = 40f;
    /** A longer tick never grows by more than this. */
    static final float MAX_GROWTH_PX = 16f;

    /**
     * The strokes for {@code marks}, each tick carrying its button's landing radius in screen pixels
     * ({@code leftSpreadPx}, {@code rightSpreadPx}; negative for a pick with no ground burst, whose tick rests).
     */
    public static List<DirectControlAimLane.Stroke> strokes(SpreadLook look, Marks marks, float leftSpreadPx,
                                                            float rightSpreadPx, float x, float y, Color centre) {
        List<DirectControlAimLane.Stroke> out = new ArrayList<>();
        tick(out, look, marks.left(), leftSpreadPx, x, y, -1f);
        tick(out, look, marks.right(), rightSpreadPx, x, y, 1f);
        out.add(new DirectControlAimLane.Stroke(x - 1.25f, y, x + 1.25f, y, 2.5f, centre));
        return out;
    }

    /** Where a tick runs, in pixels out from the dot: {@code [inner, outer]}. */
    static float[] extent(SpreadLook look, float spreadPx) {
        if (!(spreadPx > 0f)) return new float[]{INNER, OUTER};
        return switch (look) {
            case LONGER -> new float[]{INNER, OUTER + Math.min(spreadPx, MAX_GROWTH_PX)};
            case BLOOM -> {
                float inner = Math.max(INNER, Math.min(spreadPx, MAX_BLOOM_PX));
                yield new float[]{inner, inner + OUTER - INNER};
            }
        };
    }

    /** {@code side} -1 draws the left button's tick, 1 the right's. */
    private static void tick(List<DirectControlAimLane.Stroke> out, SpreadLook look, Mark mark, float spreadPx,
                             float x, float y, float side) {
        Color color = color(mark);
        if (color == null) return;
        // A refused trigger lands nothing, so a grey tick rests whatever its rounds would have scattered.
        float[] extent = extent(look, mark == Mark.HELD ? -1f : spreadPx);
        float from = x + side * extent[0];
        float to = x + side * extent[1];
        switch (mark) {
            case WASTED -> pieces(out, from, to, y, Math.max(DOTS, Math.round((extent[1] - extent[0]) / 3.5f)),
                    DOT, color);
            case HELD -> pieces(out, from, to, y, DASHES, DASH, color);
            default -> out.add(new DirectControlAimLane.Stroke(from, y, to, y, WIDTH, color));
        }
    }

    /** {@code count} pieces {@code length} long, spread evenly from {@code from} to {@code to}, ends included. */
    private static void pieces(List<DirectControlAimLane.Stroke> out, float from, float to, float y,
                               int count, float length, Color color) {
        float sign = Math.signum(to - from);
        float pitch = (Math.abs(to - from) - length) / (count - 1);
        for (int i = 0; i < count; i++) {
            float start = from + sign * pitch * i;
            out.add(new DirectControlAimLane.Stroke(start, y, start + sign * length, y, WIDTH, color));
        }
    }
}
