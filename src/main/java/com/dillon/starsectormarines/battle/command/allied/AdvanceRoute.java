package com.dillon.starsectormarines.battle.command.allied;

import java.util.ArrayList;
import java.util.List;

/**
 * The way a screened advance goes ({@code allied-command.md}, Screened advance): a line of cells
 * from the start line to the rally point, measured by distance along it. A machine or a squad is
 * "ahead" of another when its point projects further along. Pure geometry, no simulation.
 */
public final class AdvanceRoute {

    private final float[] xs, ys;
    /** Distance along the route at each corner; {@code cum[0] == 0}. */
    private final float[] cum;

    private AdvanceRoute(float[] xs, float[] ys) {
        this.xs = xs;
        this.ys = ys;
        cum = new float[xs.length];
        for (int i = 1; i < xs.length; i++) {
            cum[i] = cum[i - 1] + (float) Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        }
    }

    /** A straight route from one cell to another. */
    public static AdvanceRoute straight(int fromX, int fromY, int toX, int toY) {
        return through(List.of(new int[]{fromX, fromY}, new int[]{toX, toY}));
    }

    /** A route through {@code points}, {@code [x, y]} cells, start first, rally point last. */
    public static AdvanceRoute through(List<int[]> points) {
        if (points.size() < 2) throw new IllegalArgumentException("a route needs a start and a rally point");
        List<float[]> kept = new ArrayList<>();
        for (int[] p : points) {
            float[] c = {p[0] + .5f, p[1] + .5f};
            if (!kept.isEmpty() && kept.get(kept.size() - 1)[0] == c[0] && kept.get(kept.size() - 1)[1] == c[1]) continue;
            kept.add(c);
        }
        if (kept.size() < 2) throw new IllegalArgumentException("a route's start and rally point are one cell");
        float[] xs = new float[kept.size()], ys = new float[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            xs[i] = kept.get(i)[0];
            ys[i] = kept.get(i)[1];
        }
        return new AdvanceRoute(xs, ys);
    }

    /** Length of the route in cells. */
    public float length() {
        return cum[cum.length - 1];
    }

    /** How far along the route a point lies: the distance at its nearest point on the route. */
    public float along(float x, float y) {
        float best = Float.MAX_VALUE, at = 0f;
        for (int i = 1; i < xs.length; i++) {
            float sx = xs[i] - xs[i - 1], sy = ys[i] - ys[i - 1];
            float segment = cum[i] - cum[i - 1];
            float t = ((x - xs[i - 1]) * sx + (y - ys[i - 1]) * sy) / (segment * segment);
            // The ends run on past the route, so a squad short of the start or past the rally reads so.
            if (i > 1) t = Math.max(t, 0f);
            if (i < xs.length - 1) t = Math.min(t, 1f);
            float px = xs[i - 1] + sx * t, py = ys[i - 1] + sy * t;
            float d = (px - x) * (px - x) + (py - y) * (py - y);
            if (d < best) {
                best = d;
                at = cum[i - 1] + segment * t;
            }
        }
        return at;
    }

    /** The point {@code distance} along the route, clamped to its ends. */
    public float[] pointAt(float distance) {
        int i = segmentAt(distance);
        float segment = cum[i] - cum[i - 1];
        float t = Math.max(0f, Math.min(1f, (distance - cum[i - 1]) / segment));
        return new float[]{xs[i - 1] + (xs[i] - xs[i - 1]) * t, ys[i - 1] + (ys[i] - ys[i - 1]) * t};
    }

    /** The unit direction of the advance {@code distance} along the route. */
    public float[] directionAt(float distance) {
        int i = segmentAt(distance);
        float segment = cum[i] - cum[i - 1];
        return new float[]{(xs[i] - xs[i - 1]) / segment, (ys[i] - ys[i - 1]) / segment};
    }

    /**
     * Slot {@code index} of {@code count} in a line abreast {@code distance} along the route, {@code spacing}
     * cells apart across the advance and centred on it: where one squad or machine of an element stands.
     */
    public int[] slot(float distance, int index, int count, float spacing) {
        float[] at = pointAt(distance), dir = directionAt(distance);
        float across = (index - (count - 1) / 2f) * spacing;
        return new int[]{(int) Math.floor(at[0] - dir[1] * across), (int) Math.floor(at[1] + dir[0] * across)};
    }

    private int segmentAt(float distance) {
        for (int i = 1; i < cum.length; i++) if (distance <= cum[i]) return i;
        return cum.length - 1;
    }
}
