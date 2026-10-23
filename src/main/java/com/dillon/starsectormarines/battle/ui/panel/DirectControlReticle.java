package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground body's aim reticle as screen strokes: a short tick either side of a neutral centre dot on the aim
 * point, the left tick for the left mouse button and the right tick for the right, each in its {@link Mark}'s
 * colour. Grey is also drawn broken, so a held trigger does not rely on hue; red carries a short end bar. Every tick
 * sits on a dark outline so it holds on busy or light ground. The strokes are pure geometry, drawn by the GL panel
 * and by review frames alike.
 */
public final class DirectControlReticle {
    private DirectControlReticle() {}

    public static final Color GOOD = new Color(0xF4, 0xF6, 0xF8);
    public static final Color FALLING_OFF = new Color(0xFF, 0xD2, 0x4A);
    public static final Color WASTED = new Color(0xFF, 0x5A, 0x4A);
    public static final Color HELD = new Color(0xB4, 0xBA, 0xC0);
    /** The outline under every tick. */
    static final Color OUTLINE = new Color(0x0A, 0x0C, 0x10, 0xB0);

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
    private static final float OUTLINE_WIDTH = 4f;
    private static final float DASH = 3f;
    private static final float GAP = 2f;
    private static final float END_BAR = 4f;
    private static final float INNER = 5f;
    private static final float OUTER = 14f;

    /** The strokes for {@code marks}, centred on screen point {@code (x, y)}, outlines first. */
    public static List<DirectControlAimLane.Stroke> strokes(Marks marks, float x, float y, Color centre) {
        List<DirectControlAimLane.Stroke> lines = new ArrayList<>();
        tick(lines, marks.left(), x, y, -1f);
        tick(lines, marks.right(), x, y, 1f);
        List<DirectControlAimLane.Stroke> out = new ArrayList<>(lines.size() * 2 + 1);
        for (var line : lines) out.add(outline(line));
        out.addAll(lines);
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
        float inner = x + side * INNER;
        float outer = x + side * OUTER;
        line(out, mark, inner, y, outer, y, color);
        if (mark == Mark.WASTED) {
            out.add(new DirectControlAimLane.Stroke(outer, y - END_BAR * 0.75f, outer, y + END_BAR * 0.75f,
                    WIDTH, color));
        }
    }

    /** A solid line, or dashes for a held mark. */
    private static void line(List<DirectControlAimLane.Stroke> out, Mark mark, float x0, float y0,
                             float x1, float y1, Color color) {
        if (mark != Mark.HELD) {
            out.add(new DirectControlAimLane.Stroke(x0, y0, x1, y1, WIDTH, color));
            return;
        }
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        for (float at = 0f; at < length; at += DASH + GAP) {
            float end = Math.min(length, at + DASH);
            out.add(new DirectControlAimLane.Stroke(x0 + (x1 - x0) * at / length, y0 + (y1 - y0) * at / length,
                    x0 + (x1 - x0) * end / length, y0 + (y1 - y0) * end / length, WIDTH, color));
        }
    }

    /** The dark stroke under {@code line}, a pixel wider on each side and a pixel longer at each end. */
    private static DirectControlAimLane.Stroke outline(DirectControlAimLane.Stroke line) {
        float dx = line.endX() - line.x();
        float dy = line.endY() - line.y();
        float length = (float) Math.hypot(dx, dy);
        float ux = length > 0f ? dx / length : 0f;
        float uy = length > 0f ? dy / length : 0f;
        return new DirectControlAimLane.Stroke(line.x() - ux, line.y() - uy, line.endX() + ux, line.endY() + uy,
                OUTLINE_WIDTH, OUTLINE);
    }
}
