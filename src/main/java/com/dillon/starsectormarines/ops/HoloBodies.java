package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;

import static com.dillon.starsectormarines.ops.HoloSpace.alpha;
import static com.dillon.starsectormarines.ops.HoloSpace.hash;

/**
 * The bodies an action item becomes on the holo board, one shape per kind so the plot
 * reads without its labels: a story lead is a ringed world, an obligation a planet inside
 * a turning defence ring, a side job a small cratered moon with a derelict on its orbit,
 * and a letter a data card swaying in the air.
 *
 * <p>Each is a shaded sphere (an SVG of layered translucent discs, tinted) under geometry
 * built in deck metres and projected per vertex: latitude lines on the globe's own axis,
 * rings and orbits split at the body so their far half passes behind it and their near
 * half in front. Sizes come from depth, so a far body is smaller as well as dimmer.
 */
final class HoloBodies {
    private static final Color WHITE = new Color(235, 255, 248);

    private HoloBodies() { }

    /** Body radius in deck metres, before hover or rise scaling. */
    static float radius(Kind kind) {
        return switch (kind) {
            case STORY -> 0.1f;
            case OBLIGATION -> 0.086f;
            case SIDE_JOB -> 0.066f;
            case LETTER -> 0.085f;
        };
    }

    /** How far the body's drawing reaches from its centre, in body radii: rings, orbits, card. */
    static float reach(Kind kind) {
        return switch (kind) {
            case STORY -> 2.0f;
            case OBLIGATION -> 1.8f;
            case SIDE_JOB -> 2.1f;
            case LETTER -> 1.5f;
        };
    }

    /**
     * Draws one body centred on a deck point. {@code scale} grows it in as it rises;
     * {@code bright} is 1 for the selection and less for a resting marker.
     */
    static void draw(CanvasContext context, HoloSpace space, HoloSpace.Art art, Kind kind, float[] deck,
                     float scale, Color tone, float bright, float time, int seed) {
        float r = radius(kind) * scale;
        if (r <= 0f) return;
        float[] p = space.screen(deck);
        float px = r * space.pixelsPerMetre(deck[2]);
        float near = 0.75f + 0.25f * space.nearness(deck[2]);
        float light = bright * near;
        HoloSpace.glow(context, p[0], p[1], px * 6f, px * 6f, alpha(tone, 70 * light));
        float phase = hash(seed) * 6.283f;
        switch (kind) {
            case STORY -> {
                Frame f = new Frame(deck, 24f, -14f + 10f * hash(seed + 3));
                band(context, space, f, r * 1.4f, r * 1.95f, tone, light, false);
                sphere(context, space, art, "holo-sphere", f, p, px, r, tone, light, time, phase, 3);
                band(context, space, f, r * 1.4f, r * 1.95f, tone, light, true);
            }
            case OBLIGATION -> {
                Frame f = new Frame(deck, 38f, 12f);
                float spin = (float) (time * Math.PI * 2 / 16.0) + phase;
                fort(context, space, f, r * 1.7f, spin, tone, light, false);
                sphere(context, space, art, "holo-sphere", f, p, px, r, tone, light, time, phase, 2);
                fort(context, space, f, r * 1.7f, spin, tone, light, true);
            }
            case SIDE_JOB -> {
                Frame f = new Frame(deck, 62f, 22f);
                float run = (float) (time * Math.PI * 2 / 7.0) + phase;
                orbit(context, space, f, r * 2.05f, run, tone, light, false);
                sphere(context, space, art, "holo-moon", f, p, px, r, tone, light, time, phase, 0);
                orbit(context, space, f, r * 2.05f, run, tone, light, true);
            }
            case LETTER -> card(context, space, deck, r, tone, light, time, phase);
        }
    }

    /**
     * An orthonormal frame at a body: {@code u} and {@code v} span its equator (rings and
     * orbits lie in it), {@code w} is its pole. {@code tilt} raises the far side towards
     * the camera so a ring opens; {@code roll} leans it.
     */
    private record Frame(float[] c, float[] u, float[] v, float[] w) {
        Frame(float[] centre, float tiltDegrees, float rollDegrees) {
            this(centre, axes(tiltDegrees, rollDegrees));
        }

        private Frame(float[] centre, float[][] axes) {
            this(centre, axes[0], axes[1], axes[2]);
        }

        private static float[][] axes(float tiltDegrees, float rollDegrees) {
            float s = (float) Math.sin(Math.toRadians(tiltDegrees)), c = (float) Math.cos(Math.toRadians(tiltDegrees));
            float sr = (float) Math.sin(Math.toRadians(rollDegrees)), cr = (float) Math.cos(Math.toRadians(rollDegrees));
            float[] u = {cr, sr, 0f};
            float[] v = {-sr * s, cr * s, c};
            float[] w = {-sr * c, cr * c, -s};
            return new float[][]{u, v, w};
        }

        /** Deck point at angle {@code theta} on a circle of {@code radius} in the equator, lifted {@code up} along the pole. */
        float[] at(float radius, double theta, float up) {
            float ct = (float) Math.cos(theta), st = (float) Math.sin(theta);
            return new float[]{c[0] + radius * (ct * u[0] + st * v[0]) + up * w[0],
                    c[1] + radius * (ct * u[1] + st * v[1]) + up * w[1],
                    c[2] + radius * (ct * u[2] + st * v[2]) + up * w[2]};
        }
    }

    /** True when a deck point on a body's surface or ring faces the camera (is in front of the body). */
    private static boolean facing(HoloSpace space, float[] centre, float[] point) {
        float height = space.layout.camera().height();
        float tx = -centre[0], ty = height - centre[1], tz = -centre[2];
        return (point[0] - centre[0]) * tx + (point[1] - centre[1]) * ty + (point[2] - centre[2]) * tz > 0f;
    }

    private static void sphere(CanvasContext context, HoloSpace space, HoloSpace.Art art, String stem, Frame f,
                               float[] p, float px, float r, Color tone, float light, float time, float phase,
                               int parallels) {
        SvgAsset shade = art.get(stem);
        if (shade != null) {
            context.svg(shade, p[0] - px, p[1] - px, px * 2f, px * 2f, alpha(tone, 255 * light));
        } else {
            int n = 20;
            float[] xy = new float[n * 2];
            for (int i = 0; i < n; i++) {
                xy[i * 2] = p[0] + px * (float) Math.cos(Math.PI * 2 * i / n);
                xy[i * 2 + 1] = p[1] + px * (float) Math.sin(Math.PI * 2 * i / n);
            }
            HoloSpace.fill(context, xy, alpha(tone, 70 * light));
            HoloSpace.ring(context, p[0], p[1], px, alpha(tone, 200 * light), 1.2f);
        }
        if (parallels <= 0 || px < 5f) return;
        // Latitude lines on the globe's own axis, and two meridians turning with it.
        Color line = alpha(tone, 110 * light);
        int n = 20;
        for (int k = 0; k < parallels; k++) {
            float lat = parallels == 1 ? 0f : (float) Math.toRadians(-40 + 80.0 * k / (parallels - 1));
            float ring = r * (float) Math.cos(lat);
            float up = r * (float) Math.sin(lat);
            float[] prev = f.at(ring, 0, up);
            for (int i = 1; i <= n; i++) {
                float[] d = f.at(ring, Math.PI * 2 * i / n, up);
                if (facing(space, f.c(), prev) && facing(space, f.c(), d)) {
                    float[] a = space.screen(prev), b = space.screen(d);
                    context.line(a[0], a[1], b[0], b[1], line, 1f);
                }
                prev = d;
            }
        }
        double spin = time * Math.PI * 2 / 11.0 + phase;
        for (int m = 0; m < 2; m++) {
            double lon = spin + Math.PI * m;
            float[] prev = null;
            for (int i = 0; i <= 12; i++) {
                double lat = -Math.PI * 0.5 + Math.PI * i / 12;
                float[] d = f.at(r * (float) Math.cos(lat), lon, r * (float) Math.sin(lat));
                if (prev != null && facing(space, f.c(), prev) && facing(space, f.c(), d)) {
                    float[] a = space.screen(prev), b = space.screen(d);
                    context.line(a[0], a[1], b[0], b[1], alpha(tone, 80 * light), 1f);
                }
                prev = d;
            }
        }
    }

    /** A ringed world's ring: a translucent band with bright edges, its near half over the globe. */
    private static void band(CanvasContext context, HoloSpace space, Frame f, float inner, float outer,
                             Color tone, float light, boolean front) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            float[] i0 = f.at(inner, t0, 0f), i1 = f.at(inner, t1, 0f);
            float[] o0 = f.at(outer, t0, 0f), o1 = f.at(outer, t1, 0f);
            float midZ = (o0[2] + o1[2]) * 0.5f;
            if ((midZ < f.c()[2]) != front) continue;
            float[] a = space.screen(i0), b = space.screen(i1), c = space.screen(o1), d = space.screen(o0);
            HoloSpace.fill(context, new float[]{a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]},
                    alpha(tone, 44 * light));
            context.line(d[0], d[1], c[0], c[1], alpha(tone, 210 * light), 1.2f);
            context.line(a[0], a[1], b[0], b[1], alpha(tone, 120 * light), 1f);
        }
    }

    /** An obligation's defence ring: a turning hexagon of picket nodes round the world. */
    private static void fort(CanvasContext context, HoloSpace space, Frame f, float radius, float spin,
                             Color tone, float light, boolean front) {
        int sides = 6;
        for (int i = 0; i < sides; i++) {
            float[] d0 = f.at(radius, spin + Math.PI * 2 * i / sides, 0f);
            float[] d1 = f.at(radius, spin + Math.PI * 2 * (i + 1) / sides, 0f);
            float midZ = (d0[2] + d1[2]) * 0.5f;
            if ((midZ < f.c()[2]) == front) {
                float[] a = space.screen(d0), b = space.screen(d1);
                context.line(a[0], a[1], b[0], b[1], alpha(tone, (front ? 190 : 110) * light), 1.2f);
            }
            if ((d0[2] < f.c()[2]) == front) {
                float[] a = space.screen(d0);
                float s = front ? 2.6f : 2f;
                context.fillRect(a[0] - s * 0.5f, a[1] - s * 0.5f, s, s, alpha(WHITE, (front ? 230 : 140) * light));
                // A short spoke from the picket in towards the world.
                float[] in = space.screen(f.at(radius * 0.72f, spin + Math.PI * 2 * i / sides, 0f));
                context.line(a[0], a[1], in[0], in[1], alpha(tone, 90 * light), 1f);
            }
        }
    }

    /** A side job's orbit: a dashed ellipse with a derelict fragment tumbling along it. */
    private static void orbit(CanvasContext context, HoloSpace space, Frame f, float radius, float run,
                              Color tone, float light, boolean front) {
        int n = 32;
        for (int i = 0; i < n; i += 2) {
            float[] d0 = f.at(radius, Math.PI * 2 * i / n, 0f);
            float[] d1 = f.at(radius, Math.PI * 2 * (i + 1) / n, 0f);
            if (((d0[2] + d1[2]) * 0.5f < f.c()[2]) != front) continue;
            float[] a = space.screen(d0), b = space.screen(d1);
            context.line(a[0], a[1], b[0], b[1], alpha(tone, (front ? 170 : 90) * light), 1f);
        }
        float[] d = f.at(radius, run, 0f);
        if ((d[2] < f.c()[2]) != front) return;
        float[] p = space.screen(d);
        float s = front ? 3f : 2.2f;
        double turn = run * 2.7;
        float[] xy = new float[6];
        for (int k = 0; k < 3; k++) {
            double a = turn + Math.PI * 2 * k / 3;
            float len = k == 0 ? s * 1.3f : s * 0.8f;
            xy[k * 2] = p[0] + len * (float) Math.cos(a);
            xy[k * 2 + 1] = p[1] + len * (float) Math.sin(a);
        }
        HoloSpace.fill(context, xy, alpha(WHITE, (front ? 220 : 130) * light));
    }

    /** A letter: a data card standing in the air, swaying about its vertical axis. */
    private static void card(CanvasContext context, HoloSpace space, float[] deck, float r, Color tone,
                             float light, float time, float phase) {
        float hw = r * 1.4f, hh = r * 0.92f;
        float sway = 0.55f * (float) Math.sin(time * 0.7 + phase);
        float cx = (float) Math.cos(sway), sz = (float) Math.sin(sway);
        // Lean the card back a little so its face catches the camera from above.
        float lean = 0.28f;
        float[][] corners = new float[4][];
        float[][] local = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < 4; i++) {
            float x = local[i][0] * hw, y = local[i][1] * hh;
            corners[i] = space.screen(deck[0] + x * cx, deck[1] + y, deck[2] + x * sz + y * lean);
        }
        float[] p = space.screen(deck);
        float px = r * space.pixelsPerMetre(deck[2]);
        HoloSpace.glow(context, p[0], p[1], px * 4.5f, px * 3.5f, alpha(tone, 90 * light));
        float[] xy = new float[8];
        for (int i = 0; i < 4; i++) {
            xy[i * 2] = corners[i][0];
            xy[i * 2 + 1] = corners[i][1];
        }
        HoloSpace.fill(context, xy, alpha(tone, 85 * light));
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            context.line(a[0], a[1], b[0], b[1], alpha(tone, 230 * light), 1.2f);
        }
        // Header bar and three lines of text on the face, in the card's own perspective.
        float[][] rows = {{0.62f, 0.9f, 0.55f}, {0.2f, 0.85f, 0.32f}, {-0.15f, 0.85f, 0.26f}, {-0.5f, 0.55f, 0.26f}};
        for (float[] row : rows) {
            float y = row[0] * hh;
            float x0 = -0.8f * hw, x1 = (-0.8f + 1.6f * row[1]) * hw;
            float[] a = space.screen(deck[0] + x0 * cx, deck[1] + y, deck[2] + x0 * sz + y * lean);
            float[] b = space.screen(deck[0] + x1 * cx, deck[1] + y, deck[2] + x1 * sz + y * lean);
            context.line(a[0], a[1], b[0], b[1], alpha(WHITE, 255 * row[2] * light), row == rows[0] ? 1.6f : 1f);
        }
    }

    /**
     * A ring round a body in its own horizontal plane, drawn as turning dashes: the
     * selection's bracket. Projected, so it foreshortens like the rest of the room.
     */
    static void bracket(CanvasContext context, HoloSpace space, float[] deck, float radius, Color color,
                        float time) {
        Frame f = new Frame(deck, 8f, 0f);
        int n = 36;
        double turn = time * 0.9;
        for (int i = 0; i < n; i++) {
            if (i % 3 == 2) continue;
            float[] a = space.screen(f.at(radius, turn + Math.PI * 2 * i / n, 0f));
            float[] b = space.screen(f.at(radius, turn + Math.PI * 2 * (i + 1) / n, 0f));
            context.line(a[0], a[1], b[0], b[1], color, 1.4f);
        }
    }
}
