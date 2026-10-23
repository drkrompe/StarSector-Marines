package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground body's aim reticle as screen strokes: one mark per mouse button in its {@link Mark}'s colour, around a
 * neutral centre dot on the aim point. Grey is also drawn broken and thin, so a held trigger does not rely on hue;
 * red carries a short outward tick. The strokes are pure geometry, drawn by the GL panel and by review frames alike.
 */
public final class DirectControlReticle {
    private DirectControlReticle() {}

    /**
     * Reticle shapes on trial for the owner's pick (picks question {@code aim-reticle-shape}); the looks not chosen
     * are removed with this enum. {@code BRACKETS} is built on meanwhile.
     */
    public enum Look {
        /** {@code [ · ]}: the left bracket is the left button, the right bracket the right. */
        BRACKETS,
        /** An inner ring for the left button and an outer ring for the right. */
        CONCENTRIC,
        /** A short tick either side of the dot. */
        TICKS
    }

    /** The shape the live screen draws. */
    public static final Look LIVE = Look.BRACKETS;

    public static final Color GOOD = new Color(0xF4, 0xF6, 0xF8);
    public static final Color FALLING_OFF = new Color(0xFF, 0xD2, 0x4A);
    public static final Color WASTED = new Color(0xFF, 0x5A, 0x4A);
    public static final Color HELD = new Color(0x8C, 0x92, 0x98);

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

    private static final float WIDTH = 2f;
    private static final float HELD_WIDTH = 1.25f;
    private static final float DASH = 3f;
    private static final float GAP = 2.5f;
    private static final float TICK = 4f;
    private static final int RING_SEGMENTS = 24;

    /** The strokes for {@code marks} in {@code look}, centred on screen point {@code (x, y)}. */
    public static List<DirectControlAimLane.Stroke> strokes(Look look, Marks marks, float x, float y,
                                                            Color centre) {
        List<DirectControlAimLane.Stroke> out = new ArrayList<>();
        switch (look) {
            case BRACKETS -> {
                bracket(out, marks.left(), x, y, -1f);
                bracket(out, marks.right(), x, y, 1f);
            }
            case CONCENTRIC -> {
                ring(out, marks.left(), x, y, 6.5f);
                ring(out, marks.right(), x, y, 13f);
            }
            case TICKS -> {
                tick(out, marks.left(), x, y, -1f);
                tick(out, marks.right(), x, y, 1f);
            }
        }
        out.add(new DirectControlAimLane.Stroke(x - 1.25f, y, x + 1.25f, y, 2.5f, centre));
        return out;
    }

    /**
     * The scatter ring at {@code radius} pixels in its mark's colour, broken into dashes when the mark is held so a
     * refused rack reads without its hue.
     */
    public static List<DirectControlAimLane.Stroke> scatterRing(Mark mark, float x, float y, float radius) {
        List<DirectControlAimLane.Stroke> out = new ArrayList<>();
        Color color = color(mark);
        if (color == null) return out;
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            if (mark == Mark.HELD && i % 4 >= 2) continue;
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            out.add(new DirectControlAimLane.Stroke(x + (float) (Math.cos(a0) * radius), y + (float) (Math.sin(a0) * radius),
                    x + (float) (Math.cos(a1) * radius), y + (float) (Math.sin(a1) * radius), 1.5f, color));
        }
        return out;
    }

    /** {@code side} -1 draws {@code [} left of the dot, 1 draws {@code ]} right of it. */
    private static void bracket(List<DirectControlAimLane.Stroke> out, Mark mark, float x, float y, float side) {
        Color color = color(mark);
        if (color == null) return;
        float bx = x + side * 11f;
        float half = 8f;
        float arm = 4.5f;
        line(out, mark, bx, y - half, bx, y + half, color);
        line(out, mark, bx, y - half, bx - side * arm, y - half, color);
        line(out, mark, bx, y + half, bx - side * arm, y + half, color);
        if (mark == Mark.WASTED) line(out, mark, bx, y, bx + side * TICK, y, color);
    }

    private static void ring(List<DirectControlAimLane.Stroke> out, Mark mark, float x, float y, float radius) {
        Color color = color(mark);
        if (color == null) return;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            // A held ring is every other segment: hollow by gaps as well as by hue.
            if (mark == Mark.HELD && i % 2 == 1) continue;
            double a0 = Math.PI * 2.0 * i / RING_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / RING_SEGMENTS;
            out.add(new DirectControlAimLane.Stroke(x + (float) (Math.cos(a0) * radius), y + (float) (Math.sin(a0) * radius),
                    x + (float) (Math.cos(a1) * radius), y + (float) (Math.sin(a1) * radius),
                    mark == Mark.HELD ? HELD_WIDTH : WIDTH, color));
        }
        if (mark != Mark.WASTED) return;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5 * i + (radius < 10f ? Math.PI * 0.25 : 0.0);
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            out.add(new DirectControlAimLane.Stroke(x + c * radius, y + s * radius,
                    x + c * (radius + TICK), y + s * (radius + TICK), WIDTH, color));
        }
    }

    private static void tick(List<DirectControlAimLane.Stroke> out, Mark mark, float x, float y, float side) {
        Color color = color(mark);
        if (color == null) return;
        float inner = x + side * 5f;
        float outer = x + side * 14f;
        line(out, mark, inner, y, outer, y, color);
        if (mark == Mark.WASTED) line(out, mark, outer, y - TICK * 0.75f, outer, y + TICK * 0.75f, color);
    }

    /** A solid line, or a dashed thin one for a held mark. */
    private static void line(List<DirectControlAimLane.Stroke> out, Mark mark, float x0, float y0,
                             float x1, float y1, Color color) {
        if (mark != Mark.HELD) {
            out.add(new DirectControlAimLane.Stroke(x0, y0, x1, y1, WIDTH, color));
            return;
        }
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        if (length <= DASH) {
            out.add(new DirectControlAimLane.Stroke(x0, y0, x1, y1, HELD_WIDTH, color));
            return;
        }
        for (float at = 0f; at < length; at += DASH + GAP) {
            float end = Math.min(length, at + DASH);
            out.add(new DirectControlAimLane.Stroke(x0 + (x1 - x0) * at / length, y0 + (y1 - y0) * at / length,
                    x0 + (x1 - x0) * end / length, y0 + (y1 - y0) * end / length, HELD_WIDTH, color));
        }
    }
}
