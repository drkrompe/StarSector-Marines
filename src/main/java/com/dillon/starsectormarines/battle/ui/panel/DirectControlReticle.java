package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground body's aim reticle as screen strokes: a short thin tick either side of a neutral centre dot on the aim
 * point, the left tick for the left mouse button and the right tick for the right, each in its {@link Mark}'s
 * colour. It stays quiet because the player looks straight through it: no backing and no end caps. Pattern carries
 * what hue does, so the marks read without colour too: white and yellow are solid, red (past range) is dotted, and
 * grey (will not fire) is two wide dashes. The strokes are pure geometry, drawn by the GL panel and by review frames
 * alike.
 */
public final class DirectControlReticle {
    private DirectControlReticle() {}

    public static final Color GOOD = new Color(0xF4, 0xF6, 0xF8);
    public static final Color FALLING_OFF = new Color(0xFF, 0xD2, 0x4A);
    public static final Color WASTED = new Color(0xFF, 0x5A, 0x4A);
    public static final Color HELD = new Color(0xA4, 0xAA, 0xB0);

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

    /** The strokes for {@code marks}, centred on screen point {@code (x, y)}. */
    public static List<DirectControlAimLane.Stroke> strokes(Marks marks, float x, float y, Color centre) {
        List<DirectControlAimLane.Stroke> out = new ArrayList<>();
        tick(out, marks.left(), x, y, -1f);
        tick(out, marks.right(), x, y, 1f);
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

    /** {@code side} -1 draws the left button's tick, 1 the right's. */
    private static void tick(List<DirectControlAimLane.Stroke> out, Mark mark, float x, float y, float side) {
        Color color = color(mark);
        if (color == null) return;
        float from = x + side * INNER;
        float to = x + side * OUTER;
        switch (mark) {
            case WASTED -> pieces(out, from, to, y, DOTS, DOT, color);
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
