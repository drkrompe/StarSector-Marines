package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground body's aim reticle as screen strokes: a short thin tick either side of a neutral centre dot on the aim
 * point, lying across the line of fire. The body's left tick is the left mouse button and its right tick the right,
 * each in its {@link Mark}'s colour; aiming down the screen therefore puts the left button's tick on the screen's
 * right, as a gunsight turns with its gun. A tick also carries its button's landing spread, for the picks that throw
 * rockets or salvos. It stays quiet because the player looks straight through it: no backing and no end caps.
 * Pattern carries what hue does, so the marks read without colour too: white and yellow are solid, red (past range)
 * is dotted, and grey (will not fire) is two wide dashes. Each tick also carries its trigger's {@link Load}: it
 * grows back from the dot over a cycle or reload, its unready length faint, and a pick with limited ammunition draws
 * an arc of its rounds round the dot on its side. The load speaks in brightness only, so hue keeps meaning range. The strokes are pure geometry,
 * drawn by the GL panel and by review frames alike.
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

    /** The load's faint ink: a tick's unready length, a spent round. */
    static final Color SPENT = new Color(0xF4, 0xF6, 0xF8, 46);

    private static int alpha(float share) { return Math.round(share * 255f); }

    /**
     * A button's trigger: {@code readiness} is how far its cycle or reload has run, 1 when it would fire now;
     * {@code rounds} of {@code capacity} are in the magazine, a capacity of 0 being a pick with no limit on its
     * rounds, which draws no arc; {@code reloading} is a magazine being changed, whose arc stays empty until the reload ends.
     */
    public record Load(float readiness, int rounds, int capacity, boolean reloading) {
        /** Ready, with no magazine: the tick as the mark alone draws it. */
        public static final Load READY = new Load(1f, 0, 0, false);

        /** The share of a clock {@code remaining} of {@code duration} seconds that has run, 1 when it has none. */
        public static float clock(float remaining, float duration) {
            if (remaining <= 0f || duration <= 0f) return 1f;
            return Math.max(0f, Math.min(1f, 1f - remaining / duration));
        }

        /**
         * One button firing this mount and {@code other} together, as LINKED does: it is as ready as the readier,
         * since that one fires first, and its arc counts their rounds together. Null is no mount yet.
         */
        public static Load merge(Load load, Load other) {
            if (load == null) return other;
            if (other == null) return load;
            return new Load(Math.max(load.readiness, other.readiness), load.rounds + other.rounds,
                    load.capacity + other.capacity, load.reloading && other.reloading);
        }
    }

    /** Both buttons' loads. */
    public record Loads(Load left, Load right) {
        public static final Loads READY = new Loads(Load.READY, Load.READY);
    }

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

    /** A blooming tick's inner end never sits further out than this, so a long throw cannot open it into noise. */
    static final float MAX_BLOOM_PX = 40f;

    /** The magazine arc: its radius from the dot and how far it reaches either side of the left button's side. */
    private static final float ARC_RADIUS = 22f;
    private static final float ARC_HALF_SPAN_DEG = 70f;
    private static final float ARC_WIDTH = 2.5f;
    private static final float ARC_GAP_DEG = 3f;
    /** A magazine larger than this is one continuous bar: its segments would be under a pixel wide. */
    static final int MAX_ARC_SEGMENTS = 20;
    /** The arc is drawn as chords spanning no more than this. */
    private static final float ARC_CHORD_DEG = 4f;

    /** The strokes for {@code marks} with both buttons ready and no magazine: see the full form. */
    public static List<DirectControlAimLane.Stroke> strokes(Marks marks, float leftSpreadPx,
                                                            float rightSpreadPx, float x, float y,
                                                            float fireX, float fireY, Color centre) {
        return strokes(marks, Loads.READY, leftSpreadPx, rightSpreadPx, x, y, fireX, fireY, centre);
    }

    /**
     * The strokes for {@code marks}, each tick carrying its button's landing radius in screen pixels
     * ({@code leftSpreadPx}, {@code rightSpreadPx}; negative for a pick with no ground burst, whose tick rests).
     * {@code (fireX, fireY)} is the line of fire on screen, y up, from the controlled body toward the cursor: the
     * ticks lie across it, the left button's on the body's own left of that line and the right button's on its
     * right. A zero line of fire reads as straight up the screen, where left is the screen's left. {@code loads}
     * are the buttons' triggers; a button with nothing on it draws no load either.
     */
    public static List<DirectControlAimLane.Stroke> strokes(Marks marks, Loads loads, float leftSpreadPx,
                                                            float rightSpreadPx, float x, float y,
                                                            float fireX, float fireY, Color centre) {
        float length = (float) Math.hypot(fireX, fireY);
        float ux = length > 1e-6f ? fireX / length : 0f;
        float uy = length > 1e-6f ? fireY / length : 1f;
        // The body's left of its line of fire: the line turned a quarter counter-clockwise, y up.
        float leftX = -uy;
        float leftY = ux;
        List<DirectControlAimLane.Stroke> out = new ArrayList<>();
        tick(out, marks.left(), loads.left().readiness(), leftSpreadPx, x, y, leftX, leftY);
        if (marks.left() != Mark.NONE) arc(out, loads.left(), x, y, leftX, leftY, true);
        tick(out, marks.right(), loads.right().readiness(), rightSpreadPx, x, y, -leftX, -leftY);
        if (marks.right() != Mark.NONE) arc(out, loads.right(), x, y, -leftX, -leftY, false);
        out.add(new DirectControlAimLane.Stroke(x - 1.25f, y, x + 1.25f, y, 2.5f, centre));
        return out;
    }

    /**
     * Where a tick runs, in pixels out from the dot: {@code [inner, outer]}. A tick carrying a landing spread blooms:
     * it keeps its length and opens away from the dot until its inner end sits on the landing radius, never further
     * than {@link #MAX_BLOOM_PX}.
     */
    static float[] extent(float spreadPx) {
        float inner = spreadPx > 0f ? Math.max(INNER, Math.min(spreadPx, MAX_BLOOM_PX)) : INNER;
        return new float[]{inner, inner + OUTER - INNER};
    }

    /**
     * A tick out from {@code (x, y)} along the unit {@code (dirX, dirY)}. Short of {@code readiness} 1 the tick is lit
     * only from the dot out to that share of its length and faint beyond, so it grows back as the clock runs.
     */
    private static void tick(List<DirectControlAimLane.Stroke> out, Mark mark, float readiness, float spreadPx,
                             float x, float y, float dirX, float dirY) {
        Color color = color(mark);
        if (color == null) return;
        // A refused trigger lands nothing, so a grey tick rests whatever its rounds would have scattered, and it
        // has no clock to show.
        float[] extent = extent(mark == Mark.HELD ? -1f : spreadPx);
        float grown = mark == Mark.HELD || readiness >= 1f ? extent[1]
                : extent[0] + (extent[1] - extent[0]) * Math.max(0f, readiness);
        switch (mark) {
            case WASTED -> pieces(out, x, y, dirX, dirY, extent,
                    Math.max(DOTS, Math.round((extent[1] - extent[0]) / 3.5f)), DOT, grown, color);
            case HELD -> pieces(out, x, y, dirX, dirY, extent, DASHES, DASH, grown, color);
            default -> {
                if (grown > extent[0]) out.add(new DirectControlAimLane.Stroke(x + dirX * extent[0],
                        y + dirY * extent[0], x + dirX * grown, y + dirY * grown, WIDTH, color));
                if (grown < extent[1]) out.add(new DirectControlAimLane.Stroke(x + dirX * grown, y + dirY * grown,
                        x + dirX * extent[1], y + dirY * extent[1], WIDTH, SPENT));
            }
        }
    }

    /**
     * {@code count} pieces {@code length} long, spread evenly over {@code extent} along the direction, ends included;
     * a piece whose middle lies past {@code grown} is faint.
     */
    private static void pieces(List<DirectControlAimLane.Stroke> out, float x, float y, float dirX, float dirY,
                               float[] extent, int count, float length, float grown, Color color) {
        float pitch = (extent[1] - extent[0] - length) / (count - 1);
        for (int i = 0; i < count; i++) {
            float start = extent[0] + pitch * i;
            out.add(new DirectControlAimLane.Stroke(x + dirX * start, y + dirY * start,
                    x + dirX * (start + length), y + dirY * (start + length), WIDTH,
                    start + length * 0.5f <= grown ? color : SPENT));
        }
    }

    /**
     * The magazine arc around the dot on a button's side ({@code (sideX, sideY)}), one segment a round. Rounds
     * fire from the end nearest the line of fire, so spent rounds go faint from that end. A reload leaves the whole arc
     * faint until it ends, since not one round can fire before then; the tick carries its clock. A magazine over {@link #MAX_ARC_SEGMENTS} is one bar split
     * at the same share.
     */
    private static void arc(List<DirectControlAimLane.Stroke> out, Load load, float x, float y,
                            float sideX, float sideY, boolean left) {
        int capacity = load.capacity();
        if (capacity <= 0) return;
        // Y up, the line of fire is a quarter turn clockwise of the left side, so its end of the left arc is the lower
        // angle; the right arc mirrors it, sweeping the other way.
        float sign = left ? 1f : -1f;
        float from = (float) Math.toDegrees(Math.atan2(sideY, sideX)) - sign * ARC_HALF_SPAN_DEG;
        float span = sign * 2f * ARC_HALF_SPAN_DEG;
        int lit = load.reloading() ? 0 : Math.max(0, Math.min(capacity, load.rounds()));
        // The spent rounds are the run nearest the line of fire.
        int front = capacity - lit;
        if (capacity > MAX_ARC_SEGMENTS) {
            float split = span * front / capacity;
            arcPiece(out, x, y, from, split, SPENT);
            arcPiece(out, x, y, from + split, span - split, GOOD);
            return;
        }
        float step = span / capacity;
        float gap = sign * Math.min(ARC_GAP_DEG, Math.abs(step) * 0.4f);
        for (int i = 0; i < capacity; i++) {
            arcPiece(out, x, y, from + i * step, step - gap, i < front ? SPENT : GOOD);
        }
    }

    /** An arc piece from {@code fromDeg} through {@code spanDeg}, either way round, as chords. */
    private static void arcPiece(List<DirectControlAimLane.Stroke> out, float x, float y, float fromDeg,
                                 float spanDeg, Color color) {
        if (Math.abs(spanDeg) < 1e-3f) return;
        int chords = Math.max(1, (int) Math.ceil(Math.abs(spanDeg) / ARC_CHORD_DEG));
        double a0 = Math.toRadians(fromDeg);
        for (int c = 1; c <= chords; c++) {
            double a1 = Math.toRadians(fromDeg + spanDeg * c / chords);
            out.add(new DirectControlAimLane.Stroke(x + (float) (Math.cos(a0) * ARC_RADIUS),
                    y + (float) (Math.sin(a0) * ARC_RADIUS), x + (float) (Math.cos(a1) * ARC_RADIUS),
                    y + (float) (Math.sin(a1) * ARC_RADIUS), ARC_WIDTH, color));
            a0 = a1;
        }
    }
}
