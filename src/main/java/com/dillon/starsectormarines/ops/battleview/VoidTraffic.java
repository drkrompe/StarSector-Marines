package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;

/**
 * The traffic flying a void backdrop's drop: smooth paths through control points, each flown now and then by a
 * craft on an irregular schedule. Nothing here is periodic: when a craft sets off, whether a slot is flown at all,
 * how fast it goes and how far it weaves off the path's line all come from a hash of the slot, so the sky never
 * settles into a repeated pattern. Pure: a craft's place is a function of the time alone, so a frame can be drawn
 * at any moment without having stepped to it (`voids.md`).
 */
final class VoidTraffic {

    /** How a craft's pace changes along its path. */
    enum Pace {
        /** Steady, weaving gently off the line: through traffic. */
        CRUISE,
        /** Slowing to a stop at the path's end: arriving at a bay. */
        ARRIVE,
        /** Gathering speed from the path's start: leaving a bay. */
        DEPART
    }

    /** How far apart the points sampled along a smoothed path lie, in metres. */
    static final float SAMPLE_METERS = 4f;
    /** A cruising craft weaves this far either side of its path, over this many metres. */
    static final float WEAVE_METERS = 2.2f, WEAVE_WAVELENGTH = 140f;
    /** The share of a path's slots actually flown. */
    static final float PRESENCE = .7f;
    /** How far a craft fades in and out at a path's ends, in metres. */
    static final float FADE_METERS = 30f;

    /** One path: its sampled points {@code x, y, z} and the distance along it to each, and its schedule. */
    private record Path(float[] points, float[] along, float speed, float interval, Pace pace, int seed) {
        float length() { return along[along.length - 1]; }
    }

    private final List<Path> paths = new ArrayList<>();

    void clear() {
        paths.clear();
    }

    int size() {
        return paths.size();
    }

    /**
     * A path through {@code controls} ({@code {x, y, z}} each, cells and metres down), smoothed into a curve that
     * passes through every one, flown at about {@code speed} metres a second by a craft about every
     * {@code interval} seconds.
     */
    void add(float[][] controls, float speed, float interval, Pace pace, int seed) {
        if (controls.length < 2) return;
        List<float[]> pts = new ArrayList<>();
        for (int k = 0; k + 1 < controls.length; k++) {
            float[] p0 = controls[Math.max(0, k - 1)], p1 = controls[k], p2 = controls[k + 1];
            float[] p3 = controls[Math.min(controls.length - 1, k + 2)];
            float span = dist(p1, p2);
            int steps = Math.max(1, (int) Math.ceil(span / SAMPLE_METERS));
            for (int i = 0; i < steps; i++) pts.add(catmullRom(p0, p1, p2, p3, i / (float) steps));
        }
        pts.add(controls[controls.length - 1].clone());
        float[] flat = new float[pts.size() * 3];
        float[] along = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            System.arraycopy(pts.get(i), 0, flat, i * 3, 3);
            if (i > 0) along[i] = along[i - 1] + dist(pts.get(i - 1), pts.get(i));
        }
        if (along[along.length - 1] <= 0f) return;
        paths.add(new Path(flat, along, speed, interval, pace, seed));
    }

    /**
     * Every craft in the air at {@code time}, each {@code {x, y, z, heading in radians, hull hash, fade}}, into
     * {@code out}.
     */
    void place(float time, List<float[]> out) {
        for (Path path : paths) {
            float length = path.length();
            // The slowest a craft on this path flies, so every slot still in the air is visited.
            float longest = length / (path.speed() * .7f);
            long first = (long) Math.floor((time - longest) / path.interval()) - 1;
            long last = (long) Math.floor(time / path.interval()) + 1;
            for (long n = first; n <= last; n++) {
                int h = hash(n, path.seed());
                if ((h & 1023) / 1023f >= PRESENCE) continue;
                float launch = (n + ((h >>> 10) & 1023) / 1023f * .9f) * path.interval();
                float speed = path.speed() * (.7f + ((h >>> 20) & 1023) / 1023f * .6f);
                float duration = length / speed;
                float f = (time - launch) / duration;
                if (f < 0f || f > 1f) continue;
                float d = switch (path.pace()) {
                    case CRUISE -> f;
                    case ARRIVE -> 1f - (1f - f) * (1f - f);
                    case DEPART -> f * f;
                } * length;
                float[] at = at(path, d);
                float x = at[0], y = at[1];
                if (path.pace() == Pace.CRUISE) {
                    // Off the line a little, each craft on its own phase.
                    float phase = (h >>> 3 & 255) / 255f * 6.2832f;
                    float w = WEAVE_METERS * (float) Math.sin(d / WEAVE_WAVELENGTH * 6.2832f + phase);
                    x += -(float) Math.sin(at[3]) * w;
                    y += (float) Math.cos(at[3]) * w;
                }
                // In from the haze or out of a bay's dark at the start, and back into one at the end.
                float fade = Math.min(1f, Math.min(d, length - d) / FADE_METERS);
                out.add(new float[]{x, y, at[2], at[3], Math.floorMod(hash(n, path.seed() * 31 + 7), 1 << 20),
                        Math.max(0f, fade)});
            }
        }
    }

    /** The point {@code d} metres along a path, and its heading: {@code {x, y, z, heading}}. */
    private static float[] at(Path path, float d) {
        float[] along = path.along();
        float[] p = path.points();
        int lo = 0, hi = along.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (along[mid] <= d) lo = mid;
            else hi = mid;
        }
        float span = along[hi] - along[lo];
        float t = span <= 0f ? 0f : Math.max(0f, Math.min(1f, (d - along[lo]) / span));
        int a = lo * 3, b = hi * 3;
        float x = p[a] + (p[b] - p[a]) * t, y = p[a + 1] + (p[b + 1] - p[a + 1]) * t;
        float z = p[a + 2] + (p[b + 2] - p[a + 2]) * t;
        return new float[]{x, y, z, (float) Math.atan2(p[b + 1] - p[a + 1], p[b] - p[a])};
    }

    private static float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = .5f * (2f * p1[k] + (-p0[k] + p2[k]) * t + (2f * p0[k] - 5f * p1[k] + 4f * p2[k] - p3[k]) * t2
                    + (-p0[k] + 3f * p1[k] - 3f * p2[k] + p3[k]) * t3);
        }
        return out;
    }

    private static float dist(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static int hash(long n, int seed) {
        long h = n * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h & 0x7fffffff;
    }
}
