package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Rise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which cells a sketch's paths claim: GL-free and map-free, so the shape of a drop, its crossings and a rise is
 * decided here and proved on a small grid. A path is a polyline in world cells (cell {@code (x, y)} spans
 * {@code x..x+1}); a cell belongs to it when its centre lies within half the path's width of the line.
 */
public final class SketchRaster {

    private SketchRaster() { }

    /** One crossing's cells over its drop: the deck or land bridge, row-major. */
    public record CrossingCells(String drop, int index, Crossing crossing, boolean[] cells, Pt centre, Pt across) { }

    /**
     * The cells of every drop (crossings left out), every crossing, and every rise, {@code w} by {@code h}.
     * A rise drawn over a drop leaves it a drop.
     */
    public record Cut(int w, int h, boolean[] drop, boolean[] rise, List<CrossingCells> crossings) {
        public boolean bridged(int i) {
            for (CrossingCells c : crossings) if (c.cells()[i]) return true;
            return false;
        }
    }

    public static Cut cut(int w, int h, MapSketch sketch) {
        boolean[] drop = new boolean[w * h], rise = new boolean[w * h];
        List<CrossingCells> crossings = new ArrayList<>();
        for (Rise r : sketch.rises()) or(rise, band(w, h, r.path(), r.width()));
        for (Drop d : sketch.drops()) {
            boolean[] cells = band(w, h, d.path(), d.width());
            float[] along = along(w, h, d.path());
            float length = length(d.path());
            for (int k = 0; k < d.crossings().size(); k++) {
                Crossing c = d.crossings().get(k);
                float at = c.at() * length, half = c.width() / 2f;
                boolean[] deck = new boolean[w * h];
                for (int i = 0; i < cells.length; i++) if (cells[i] && Math.abs(along[i] - at) <= half) deck[i] = true;
                crossings.add(new CrossingCells(d.id(), k, c, deck, pointAt(d.path(), at), normalAt(d.path(), at)));
            }
            or(drop, cells);
        }
        for (CrossingCells c : crossings) for (int i = 0; i < drop.length; i++) if (c.cells()[i]) drop[i] = false;
        for (int i = 0; i < drop.length; i++) if (drop[i]) rise[i] = false;
        for (CrossingCells c : crossings) for (int i = 0; i < rise.length; i++) if (c.cells()[i]) rise[i] = false;
        return new Cut(w, h, drop, rise, crossings);
    }

    /** The cells whose centres lie within {@code width / 2} of {@code path}. */
    public static boolean[] band(int w, int h, List<Pt> path, float width) {
        boolean[] out = new boolean[w * h];
        float r = width / 2f;
        for (int s = 1; s < path.size(); s++) {
            Pt a = path.get(s - 1), b = path.get(s);
            int x0 = Math.max(0, (int) Math.floor(Math.min(a.x(), b.x()) - r - 1));
            int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(a.x(), b.x()) + r + 1));
            int y0 = Math.max(0, (int) Math.floor(Math.min(a.y(), b.y()) - r - 1));
            int y1 = Math.min(h - 1, (int) Math.ceil(Math.max(a.y(), b.y()) + r + 1));
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                if (segment(x + .5f, y + .5f, a, b)[0] <= r) out[y * w + x] = true;
            }
        }
        return out;
    }

    /** How far along {@code path} each cell's nearest point lies, in cells from its start. */
    static float[] along(int w, int h, List<Pt> path) {
        float[] out = new float[w * h], best = new float[w * h];
        Arrays.fill(best, Float.MAX_VALUE);
        float start = 0f;
        for (int s = 1; s < path.size(); s++) {
            Pt a = path.get(s - 1), b = path.get(s);
            float len = a.distance(b);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                float[] d = segment(x + .5f, y + .5f, a, b);
                int i = y * w + x;
                if (d[0] < best[i]) { best[i] = d[0]; out[i] = start + d[1] * len; }
            }
            start += len;
        }
        return out;
    }

    public static float length(List<Pt> path) {
        float sum = 0f;
        for (int s = 1; s < path.size(); s++) sum += path.get(s - 1).distance(path.get(s));
        return sum;
    }

    /** The point {@code at} cells along {@code path}. */
    public static Pt pointAt(List<Pt> path, float at) {
        float left = at;
        for (int s = 1; s < path.size(); s++) {
            Pt a = path.get(s - 1), b = path.get(s);
            float len = a.distance(b);
            if (left <= len || s == path.size() - 1) {
                float t = len == 0f ? 0f : Math.max(0f, Math.min(1f, left / len));
                return new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            left -= len;
        }
        return path.get(0);
    }

    /** The unit direction across {@code path} at {@code at} cells along it. */
    public static Pt normalAt(List<Pt> path, float at) {
        float left = at;
        for (int s = 1; s < path.size(); s++) {
            Pt a = path.get(s - 1), b = path.get(s);
            float len = a.distance(b);
            if (left <= len || s == path.size() - 1) {
                float dx = b.x() - a.x(), dy = b.y() - a.y(), n = (float) Math.hypot(dx, dy);
                return n == 0f ? new Pt(0f, 1f) : new Pt(-dy / n, dx / n);
            }
            left -= len;
        }
        return new Pt(0f, 1f);
    }

    /** The share along {@code path} of the point on it nearest {@code (x, y)}, and how far that point is. */
    public static float[] nearest(List<Pt> path, float x, float y) {
        float length = length(path), best = Float.MAX_VALUE, at = 0f, start = 0f;
        for (int s = 1; s < path.size(); s++) {
            Pt a = path.get(s - 1), b = path.get(s);
            float len = a.distance(b);
            float[] d = segment(x, y, a, b);
            if (d[0] < best) { best = d[0]; at = start + d[1] * len; }
            start += len;
        }
        return new float[]{length == 0f ? 0f : at / length, best};
    }

    /** Distance from {@code (px, py)} to segment {@code ab}, and the share along it of the nearest point. */
    private static float[] segment(float px, float py, Pt a, Pt b) {
        float dx = b.x() - a.x(), dy = b.y() - a.y(), den = dx * dx + dy * dy;
        float t = den == 0f ? 0f : Math.max(0f, Math.min(1f, ((px - a.x()) * dx + (py - a.y()) * dy) / den));
        return new float[]{(float) Math.hypot(px - a.x() - t * dx, py - a.y() - t * dy), t};
    }

    private static void or(boolean[] into, boolean[] from) {
        for (int i = 0; i < into.length; i++) into[i] |= from[i];
    }

    /** Whether any crossing here is a land bridge rather than a deck. */
    static boolean landBridge(Cut cut, int i) {
        for (CrossingCells c : cut.crossings()) if (c.cells()[i] && c.crossing().kind() == CrossingKind.LAND_BRIDGE) return true;
        return false;
    }
}
