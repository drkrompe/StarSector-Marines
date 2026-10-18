package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static com.dillon.starsectormarines.ops.HoloSpace.alpha;
import static com.dillon.starsectormarines.ops.HoloSpace.fraction;
import static com.dillon.starsectormarines.ops.HoloSpace.hash;

/**
 * The hologram's volume: what makes the board a projection standing over the table
 * rather than a decal on it. A light pool on the table top and a column up from the
 * emitter; a lat-long dome over the disc turning slowly; motes drifting up inside it; a
 * scan plane rising through it; the sector core as a small star; and hyperspace lanes
 * arcing between the bodies.
 *
 * <p>Every vertex is a deck point projected through {@link HoloSpace}, and everything is
 * a pure function of the time argument. The dome's turn carries the motes and rim ticks
 * round with it but never the bodies, which stay at their plot positions so the buttons
 * laid over them stay true.
 */
final class HoloVolume {
    /** Height of the dome over the disc, along the disc's normal, in metres. */
    static final float DOME = 0.56f;
    /** Seconds for one turn of the dome. */
    static final float YAW_SECONDS = 80f;
    private static final float SCAN_SECONDS = 7.5f;
    private static final int MERIDIANS = 12;
    private static final int MOTES = 64;
    private static final Color HOLO = new Color(150, 240, 214);
    private static final Color DEEP = new Color(70, 170, 160);
    private static final Color CORE = new Color(214, 255, 240);

    private HoloVolume() { }

    /** The turn of the dome and everything it carries, in radians, at this time. */
    static double yaw(float time) {
        return Math.PI * 2 * fraction(time / YAW_SECONDS);
    }

    /** Light on the table and the column from the emitter: drawn before anything in the volume. */
    static void light(CanvasContext context, HoloSpace space, float flicker) {
        float[] tc = space.layout.tableCentre();
        float[] mid = space.screen(tc[0], CommandDeckLayout.TABLE_TOP, tc[2]);
        float[] left = space.screen(tc[0] - 1.3f, CommandDeckLayout.TABLE_TOP, tc[2]);
        float[] near = space.screen(tc[0], CommandDeckLayout.TABLE_TOP, tc[2] - 1.0f);
        float[] far = space.screen(tc[0], CommandDeckLayout.TABLE_TOP, tc[2] + 1.0f);
        float w = (mid[0] - left[0]) * 2f;
        float h = Math.abs(near[1] - far[1]);
        HoloSpace.glow(context, mid[0], (near[1] + far[1]) * 0.5f, w * 1.15f, h * 1.3f, alpha(DEEP, 46 * flicker));
        HoloSpace.glow(context, mid[0], (near[1] + far[1]) * 0.5f, w * 0.5f, h * 0.6f, alpha(HOLO, 30 * flicker));
        // The column: soft light from the emitter up through the disc's core.
        float[] top = space.disc(0f, 0f, DOME * 0.9f);
        float length = mid[1] - top[1];
        if (length > 0f) {
            float width = 0.11f * space.pixelsPerMetre(tc[2]);
            HoloSpace.glow(context, mid[0], (mid[1] + top[1]) * 0.5f, width * 2.4f, length * 1.15f,
                    alpha(DEEP, 60 * flicker));
            HoloSpace.glow(context, mid[0], (mid[1] + top[1]) * 0.5f, width, length, alpha(HOLO, 46 * flicker));
        }
    }

    /** The dome's far half; {@link #globeFront} draws the rest over the bodies. */
    static void globeBack(CanvasContext context, HoloSpace space, float time, float flicker) {
        globe(context, space, time, flicker, false);
    }

    static void globeFront(CanvasContext context, HoloSpace space, float time, float flicker) {
        globe(context, space, time, flicker, true);
    }

    private static void globe(CanvasContext context, HoloSpace space, float time, float flicker, boolean front) {
        double yaw = yaw(time);
        float r = HqHoloBoard.RADIUS;
        float centreZ = space.centreDepth();
        int steps = 14;
        // Meridians from the rim to the crown, turning with the dome.
        for (int m = 0; m < MERIDIANS; m++) {
            double theta = yaw + Math.PI * 2 * m / MERIDIANS;
            float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
            float[] prevDeck = space.deck(r * ct, r * st, 0f);
            for (int i = 1; i <= steps; i++) {
                double phi = Math.PI * 0.5 * i / steps;
                float cp = (float) Math.cos(phi);
                float[] d = space.deck(r * cp * ct, r * cp * st, DOME * (float) Math.sin(phi));
                segment(context, space, prevDeck, d, centreZ, front, m % 3 == 0 ? 42f : 26f, flicker);
                prevDeck = d;
            }
        }
        // Parallels, the lowest brightest.
        int around = 48;
        float[] heights = {0.32f, 0.62f, 0.86f};
        for (int k = 0; k < heights.length; k++) {
            double phi = Math.asin(heights[k]);
            float ring = r * (float) Math.cos(phi);
            float c = DOME * heights[k];
            float[] prevDeck = null;
            for (int i = 0; i <= around; i++) {
                double theta = yaw * 0.5 + Math.PI * 2 * i / around;
                float[] d = space.deck(ring * (float) Math.cos(theta), ring * (float) Math.sin(theta), c);
                if (prevDeck != null) segment(context, space, prevDeck, d, centreZ, front, 40f - k * 8f, flicker);
                prevDeck = d;
            }
        }
    }

    private static void segment(CanvasContext context, HoloSpace space, float[] d0, float[] d1, float centreZ,
                                boolean front, float strength, float flicker) {
        float midZ = (d0[2] + d1[2]) * 0.5f;
        if ((midZ < centreZ) != front) return;
        float near = space.nearness(midZ);
        float[] p0 = space.screen(d0);
        float[] p1 = space.screen(d1);
        float a = strength * (front ? 0.55f + 0.6f * near : 0.35f + 0.5f * near) * flicker;
        context.line(p0[0], p0[1], p1[0], p1[1], alpha(HOLO, a), front ? 1.1f : 1f);
    }

    /** Rim ticks that turn with the dome: the parallax that says the plot is a solid. */
    static void ticks(CanvasContext context, HoloSpace space, float time, float flicker) {
        double yaw = yaw(time);
        float r = HqHoloBoard.RADIUS;
        for (int i = 0; i < 36; i++) {
            double angle = yaw + Math.PI * 2 * i / 36;
            boolean major = i % 9 == 0;
            float inner = major ? 0.9f : 0.96f;
            float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
            float[] d = space.deck(r * ca, r * sa, 0f);
            float near = space.nearness(d[2]);
            float[] p0 = space.disc(r * inner * ca, r * inner * sa, 0f);
            float[] p1 = space.screen(d);
            context.line(p0[0], p0[1], p1[0], p1[1], alpha(HOLO, (major ? 150 : 80) * (0.5f + 0.5f * near) * flicker),
                    major ? 1.4f : 1f);
        }
    }

    /** The scan plane: a slice of the dome rising from the disc to the crown, fading as it goes. */
    static void scan(CanvasContext context, HoloSpace space, float time, float flicker) {
        float s = fraction(time / SCAN_SECONDS);
        float fade = (float) Math.sin(Math.PI * s);
        if (fade <= 0.02f) return;
        float ring = HqHoloBoard.RADIUS * (float) Math.sqrt(Math.max(0f, 1f - s * s));
        float c = DOME * s;
        int n = 48;
        float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double theta = Math.PI * 2 * i / n;
            float[] p = space.disc(ring * (float) Math.cos(theta), ring * (float) Math.sin(theta), c);
            xy[i * 2] = p[0];
            xy[i * 2 + 1] = p[1];
        }
        HoloSpace.fill(context, xy, alpha(HOLO, 16 * fade * flicker));
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            context.line(xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1], alpha(HOLO, 120 * fade * flicker), 1.2f);
        }
    }

    /** Motes drifting up through the dome, carried round by its turn; nearer ones larger and brighter. */
    static void motes(CanvasContext context, HoloSpace space, float time, float flicker) {
        double yaw = yaw(time);
        for (int i = 0; i < MOTES; i++) {
            float radial = HqHoloBoard.RADIUS * 0.94f * (float) Math.sqrt(hash(i * 7 + 1));
            double theta = yaw + Math.PI * 2 * hash(i * 7 + 2);
            float period = 9f + 9f * hash(i * 7 + 3);
            float rise = fraction(hash(i * 7 + 4) + time / period);
            float ceiling = DOME * (1f - (radial / HqHoloBoard.RADIUS) * (radial / HqHoloBoard.RADIUS));
            float c = ceiling * rise;
            float[] d = space.deck(radial * (float) Math.cos(theta), radial * (float) Math.sin(theta), c);
            float near = space.nearness(d[2]);
            float twinkle = 0.55f + 0.45f * (float) Math.sin(time * (1.3f + 2f * hash(i * 7 + 5)) + i);
            float edge = (float) Math.sin(Math.PI * rise);
            float a = (40f + 120f * near) * twinkle * edge * flicker;
            if (a < 4f) continue;
            float[] p = space.screen(d);
            float size = 1f + 1.4f * near;
            context.fillRect(p[0] - size * 0.5f, p[1] - size * 0.5f, size, size, alpha(i % 5 == 0 ? CORE : HOLO, a));
        }
    }

    /** The sector core: a small star just over the disc's centre, where the plot's distances start. */
    static void core(CanvasContext context, HoloSpace space, SvgAsset star, float time, float flicker) {
        float[] d = space.deck(0f, 0f, 0.05f);
        float[] p = space.screen(d);
        float px = space.pixelsPerMetre(d[2]);
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 2.2);
        float size = 0.07f * px * pulse;
        HoloSpace.glow(context, p[0], p[1], size * 5f, size * 5f, alpha(HOLO, 110 * flicker));
        if (star != null) {
            context.svg(star, p[0] - size, p[1] - size, size * 2f, size * 2f, alpha(CORE, 235 * flicker));
        } else {
            context.fillRect(p[0] - 2f, p[1] - 2f, 4f, 4f, alpha(CORE, 220 * flicker));
        }
    }

    /**
     * Hyperspace lanes as arcs between bodies: the shortest network joining every
     * positioned body, each lane bowing up over the disc, with a pulse running along it.
     */
    static void lanes(CanvasContext context, HoloSpace space, List<float[]> bodies, float time, float flicker) {
        for (int[] lane : network(bodies)) {
            float[] from = bodies.get(lane[0]);
            float[] to = bodies.get(lane[1]);
            float span = (float) Math.hypot(to[0] - from[0], to[2] - from[2]);
            float[] control = {(from[0] + to[0]) * 0.5f, Math.max(from[1], to[1]) + 0.1f + span * 0.22f,
                    (from[2] + to[2]) * 0.5f};
            int steps = 22;
            float[] prev = space.screen(from);
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float[] d = bezier(from, control, to, t);
                float[] p = space.screen(d);
                float near = space.nearness(d[2]);
                if (i % 2 == 1 || i == steps) {
                    context.line(prev[0], prev[1], p[0], p[1], alpha(HOLO, (90 + 90 * near) * flicker), 1.3f);
                }
                prev = p;
            }
            float run = fraction(time / 3.6f + lane[0] * 0.31f + lane[1] * 0.17f);
            float[] pulse = space.screen(bezier(from, control, to, run));
            float fade = (float) Math.sin(Math.PI * run);
            HoloSpace.glow(context, pulse[0], pulse[1], 9f, 9f, alpha(HOLO, 150 * fade * flicker));
            context.fillRect(pulse[0] - 1f, pulse[1] - 1f, 2f, 2f, alpha(CORE, 220 * fade * flicker));
        }
    }

    /** Prim's minimum spanning tree over deck positions (ground distance), as index pairs. */
    static List<int[]> network(List<float[]> points) {
        List<int[]> out = new ArrayList<>();
        int n = points.size();
        if (n < 2) return out;
        boolean[] in = new boolean[n];
        in[0] = true;
        for (int added = 1; added < n; added++) {
            int bestFrom = -1, bestTo = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!in[i]) continue;
                for (int j = 0; j < n; j++) {
                    if (in[j]) continue;
                    double d = Math.hypot(points.get(i)[0] - points.get(j)[0], points.get(i)[2] - points.get(j)[2]);
                    if (d < best) {
                        best = d;
                        bestFrom = i;
                        bestTo = j;
                    }
                }
            }
            in[bestTo] = true;
            out.add(new int[]{bestFrom, bestTo});
        }
        return out;
    }

    private static float[] bezier(float[] a, float[] b, float[] c, float t) {
        float u = 1f - t;
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) out[k] = u * u * a[k] + 2f * u * t * b[k] + t * t * c[k];
        return out;
    }
}
