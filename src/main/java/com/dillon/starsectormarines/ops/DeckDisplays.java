package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live readouts on the command deck's painted monitors: sector charts with drifting
 * contacts, radar sweeps, scrolling telemetry, oscilloscope traces, bar meters, a
 * flagship schematic under a scan line, comms traffic and the quartermaster's ledger.
 *
 * <p>Each {@link CommandDeckLayout.Display} is a quad measured on the plate. Content is
 * authored in the screen's own unit square and carried onto the quad through the
 * square-to-quad projective map, so it follows each monitor's perspective; a screen on
 * a side bank leans with its bezel. Everything is a pure function of the time argument
 * (no state survives a frame) so snapshots repeat, and every screen runs on its own
 * rate and phase, hashed from its id, so the room never pulses in unison.
 *
 * <p>Drawing is phosphor on smoked glass: the painted face is dimmed, then amber and
 * warm-white strokes are laid on it with faint scanlines, a rolling refresh band and a
 * soft additive bloom, kept low enough to read as screens in a painting rather than an
 * interface laid over one. The vector art that is better authored than computed (the
 * flagship's plan and the sector chart's borders and lanes) is SVG, whose compiled
 * triangles are mapped through the same projection; lines, rings, bars and blips are
 * canvas geometry. All triangles are batched per colour into a few meshes per pass.
 */
final class DeckDisplays {
    static final String SHIP_SVG = "graphics/ui/hq-command-deck/deck-ship.svg";
    static final String SECTOR_SVG = "graphics/ui/hq-command-deck/deck-sector.svg";
    static final String GLOW_SPRITE = "graphics/battle/fx/aircraft-engine-glow.png";

    /** What a screen shows; the layout names it in lower case. Unknown names show telemetry. */
    enum Kind {
        SECTOR, SWEEP, TELEMETRY, WAVEFORM, BARS, SCHEMATIC, COMMS, LEDGER;

        static Kind of(String name) {
            if (name == null) return TELEMETRY;
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException unknown) {
                return TELEMETRY;
            }
        }
    }

    private static final int GLASS = rgb(40, 19, 5);
    private static final int SCAN = rgb(18, 8, 2);
    private static final int AMBER = rgb(255, 168, 60);
    private static final int PHOSPHOR = rgb(255, 200, 112);
    private static final int HOT = rgb(255, 238, 206);

    private final Ink glass = new Ink();
    private final Ink ink = new Ink();
    private SvgAsset ship;
    private SvgAsset sector;
    private boolean svgFailed;

    /**
     * Draws every display of the layout. {@code arrival} (0..1) is how strongly a report is
     * arriving; the comms screens brighten and flash with it.
     */
    void draw(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit,
              float time, float arrival) {
        List<CommandDeckLayout.Display> displays = layout.displays();
        if (displays.isEmpty()) return;
        loadSvgs();
        float now = Float.isFinite(time) ? time : 0f;
        float news = Float.isFinite(arrival) ? clamp01(arrival) : 0f;
        Screen[] screens = new Screen[displays.size()];
        for (int i = 0; i < screens.length; i++) screens[i] = new Screen(displays.get(i), fit, now, news);
        for (Screen screen : screens) screen.glass(glass);
        glass.flush(context);
        for (Screen screen : screens) screen.content(ink, this);
        ink.flush(context);
        for (Screen screen : screens) screen.bloom(context);
    }

    private void loadSvgs() {
        if (svgFailed || ship != null) return;
        try {
            ship = SvgAsset.load(SHIP_SVG);
            sector = SvgAsset.load(SECTOR_SVG);
        } catch (RuntimeException unavailable) {
            // A host with no asset source (a bare unit test) draws the canvas-only fallbacks.
            ship = null;
            sector = null;
            svgFailed = true;
        }
    }

    // ---- one screen -------------------------------------------------------------

    /** One monitor face for one frame: its projection, its clock and its content. */
    static final class Screen {
        final Kind kind;
        final boolean comms;
        final int seed;
        final float t;
        final float arrival;
        final float widthPx;
        final float heightPx;
        final float aspect;
        final float flicker;
        private final float a, b, c, d, e, f, g, h;
        private final float centreX, centreY;

        Screen(CommandDeckLayout.Display display, CommandDeckLayout.Fit fit, float time, float arrival) {
            kind = Kind.of(display.kind());
            comms = kind == Kind.COMMS || display.id().startsWith("comms");
            seed = display.id().hashCode();
            float rate = 0.8f + 0.45f * hash(seed, 1);
            t = time * rate + 40f * hash(seed, 2);
            this.arrival = comms ? arrival : 0f;
            float[] x = new float[4];
            float[] y = new float[4];
            for (int i = 0; i < 4; i++) {
                x[i] = fit.x(display.x(i));
                y[i] = fit.y(display.y(i));
            }
            widthPx = (dist(x[0], y[0], x[1], y[1]) + dist(x[3], y[3], x[2], y[2])) * 0.5f;
            heightPx = (dist(x[0], y[0], x[3], y[3]) + dist(x[1], y[1], x[2], y[2])) * 0.5f;
            aspect = heightPx > 0f ? widthPx / heightPx : 1f;
            centreX = (x[0] + x[1] + x[2] + x[3]) * 0.25f;
            centreY = (y[0] + y[1] + y[2] + y[3]) * 0.25f;
            flicker = 0.9f + 0.1f * noise(time * 9f, seed) - (hash((int) Math.floor(time * 12f), seed ^ 77) > 0.97f ? 0.25f : 0f);
            // Square-to-quad projective map (Heckbert): (0,0) (1,0) (1,1) (0,1) to the corners.
            float dx1 = x[1] - x[2], dx2 = x[3] - x[2], dx3 = x[0] - x[1] + x[2] - x[3];
            float dy1 = y[1] - y[2], dy2 = y[3] - y[2], dy3 = y[0] - y[1] + y[2] - y[3];
            float det = dx1 * dy2 - dx2 * dy1;
            if ((Math.abs(dx3) < 1e-4f && Math.abs(dy3) < 1e-4f) || Math.abs(det) < 1e-6f) {
                g = 0f;
                h = 0f;
            } else {
                g = (dx3 * dy2 - dx2 * dy3) / det;
                h = (dx1 * dy3 - dx3 * dy1) / det;
            }
            a = x[1] - x[0] + g * x[1];
            b = x[3] - x[0] + h * x[3];
            c = x[0];
            d = y[1] - y[0] + g * y[1];
            e = y[3] - y[0] + h * y[3];
            f = y[0];
        }

        float mapX(float u, float v) { return (a * u + b * v + c) / (g * u + h * v + 1f); }

        float mapY(float u, float v) { return (d * u + e * v + f) / (g * u + h * v + 1f); }

        // ---- passes ----

        void glass(Ink out) {
            out.rect(this, 0f, 0f, 1f, 1f, GLASS, 136);
            int rows = Math.max(4, Math.min(22, Math.round(heightPx / 3f)));
            for (int i = 0; i < rows; i++) {
                float v = (i + 0.55f) / rows;
                out.rect(this, 0f, v, 1f, Math.min(1f, v + 0.4f / rows), SCAN, 34);
            }
            float roll = frac(t * 0.11f);
            out.rect(this, 0f, Math.max(0f, roll - 0.12f), 1f, roll, AMBER, 16);
        }

        void content(Ink out, DeckDisplays owner) {
            switch (kind) {
                case SECTOR -> sector(out, owner.sector);
                case SWEEP -> sweep(out);
                case TELEMETRY -> telemetry(out, 0.07f, 0.93f);
                case WAVEFORM -> waveform(out);
                case BARS -> bars(out);
                case SCHEMATIC -> schematic(out, owner.ship);
                case COMMS -> comms(out);
                case LEDGER -> ledger(out);
            }
        }

        void bloom(CanvasContext context) {
            int alpha = quantize(Math.round(255 * (0.09f + 0.04f * flicker + 0.22f * arrival)));
            if (alpha <= 0) return;
            context.sprite(GLOW_SPRITE, null, centreX, centreY, widthPx * 1.7f, heightPx * 1.9f, 0f,
                    new Color(255, 176, 84, alpha), CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
        }

        /** Stroke alpha with the screen's flicker and any arriving report applied. */
        int lit(float base) {
            return Math.max(0, Math.min(255, Math.round(base * flicker * (1f + 0.6f * arrival))));
        }

        /** Line width in canvas pixels, thinner on small screens. */
        float px(float width) {
            return Math.max(0.7f, width * Math.min(1f, heightPx / 40f + 0.35f));
        }

        // ---- content kinds ----

        /** Chart with borders and lanes, a grid, range rings round own ship and drifting contacts. */
        private void sector(Ink out, SvgAsset chart) {
            for (int i = 1; i < 6; i++) out.line(this, i / 6f, 0.05f, i / 6f, 0.95f, px(0.8f), AMBER, lit(34));
            for (int i = 1; i < 4; i++) out.line(this, 0.04f, i / 4f, 0.96f, i / 4f, px(0.8f), AMBER, lit(34));
            if (chart != null) out.svg(this, chart, 0.05f, 0.08f, 0.95f, 0.92f, AMBER, lit(150));
            float ou = 0.42f + 0.04f * (float) Math.sin(t * 0.07f);
            float ov = 0.55f;
            for (int r = 1; r <= 3; r++) out.ring(this, ou, ov, 0.16f * r, px(0.9f), PHOSPHOR, lit(70 - r * 12));
            out.dot(this, ou, ov, px(2.4f), HOT, lit(230));
            int contacts = heightPx > 30f ? 6 : 4;
            for (int i = 0; i < contacts; i++) {
                float w1 = 0.05f + 0.09f * hash(seed, 10 + i), w2 = 0.04f + 0.08f * hash(seed, 20 + i);
                float p1 = 6.28f * hash(seed, 30 + i), p2 = 6.28f * hash(seed, 40 + i);
                boolean hostile = i == 0;
                for (int k = 3; k >= 0; k--) {
                    float tk = t - k * 0.9f;
                    float u = 0.5f + 0.4f * (float) Math.sin(tk * w1 + p1);
                    float v = 0.5f + 0.36f * (float) Math.sin(tk * w2 + p2);
                    out.dot(this, u, v, px(k == 0 ? 2.6f : 1.5f), hostile ? HOT : PHOSPHOR,
                            lit(k == 0 ? 235 : 90 - k * 20));
                    if (k == 0 && hostile) {
                        float blink = 0.5f + 0.5f * (float) Math.sin(t * 5.2f);
                        out.ring(this, u, v, 0.07f + 0.02f * blink, px(1f), HOT, lit(80 + 140 * blink));
                    }
                }
            }
        }

        /** Radar sweep with a fading wake; blips light as the beam passes and decay after. */
        private void sweep(Ink out) {
            float radius = 0.42f;
            boolean wide = aspect > 1.7f;
            float cu = wide ? 0.05f + radius / aspect : 0.5f;
            float cv = 0.5f;
            out.ring(this, cu, cv, radius, px(1.1f), PHOSPHOR, lit(120));
            out.ring(this, cu, cv, radius * 0.5f, px(0.8f), AMBER, lit(60));
            out.line(this, cu - radius / aspect, cv, cu + radius / aspect, cv, px(0.7f), AMBER, lit(45));
            out.line(this, cu, cv - radius, cu, cv + radius, px(0.7f), AMBER, lit(45));
            float period = 3.2f + 1.6f * hash(seed, 3);
            float theta = (float) (frac(t / period) * Math.PI * 2);
            for (int k = 14; k >= 0; k--) {
                float th = theta - k * 0.06f;
                int alpha = k == 0 ? 240 : Math.round(120f * (1f - k / 15f));
                out.line(this, cu, cv, cu + (float) Math.cos(th) * radius / aspect,
                        cv + (float) Math.sin(th) * radius, px(k == 0 ? 1.4f : 1.8f), k == 0 ? HOT : AMBER, lit(alpha));
            }
            for (int i = 0; i < 6; i++) {
                float phi = (float) (hash(seed, 50 + i) * Math.PI * 2 + t * 0.02f * (hash(seed, 60 + i) - 0.5f));
                float r = radius * (0.25f + 0.7f * hash(seed, 70 + i));
                float age = (float) (((theta - phi) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2) / (Math.PI * 2));
                float glow = (1f - age) * (1f - age);
                if (glow < 0.04f) continue;
                out.dot(this, cu + (float) Math.cos(phi) * r / aspect, cv + (float) Math.sin(phi) * r,
                        px(2.4f), HOT, lit(250 * glow));
            }
            if (wide) telemetry(out, 0.1f + 2f * radius / aspect, 0.95f);
        }

        /** Text columns that scroll a line at a time, the last line typing behind a cursor. */
        private void telemetry(Ink out, float u0, float u1) {
            int rows = Math.max(3, Math.min(12, Math.round(heightPx / 5.5f)));
            int columns = (u1 - u0) * aspect > 2.4f ? 2 : 1;
            float speed = 0.9f + 1.4f * hash(seed, 4);
            float scroll = t * speed;
            int top = (int) Math.floor(scroll);
            float typed = frac(scroll);
            float rowH = 0.86f / rows;
            float colW = (u1 - u0) / columns;
            for (int col = 0; col < columns; col++) {
                float cu0 = u0 + col * colW + (col > 0 ? 0.03f : 0f);
                float cu1 = u0 + (col + 1) * colW - 0.02f;
                for (int r = 0; r < rows; r++) {
                    int line = top + r + col * 97;
                    float v = 0.07f + r * rowH;
                    boolean last = r == rows - 1;
                    float limit = last ? cu0 + (cu1 - cu0) * typed : cu1;
                    boolean header = Math.floorMod(line, 7) == 0;
                    float u = cu0 + (hash(line, seed ^ 5) < 0.3f ? 0.06f : 0f);
                    for (int w = 0; w < 6 && u < limit; w++) {
                        float len = (0.05f + 0.16f * hash(line * 7 + w, seed)) * (cu1 - cu0);
                        float end = Math.min(limit, u + len);
                        out.rect(this, u, v + rowH * 0.22f, end, v + rowH * 0.72f, header ? HOT : AMBER,
                                lit(header ? 210 : 150 + 50 * hash(line, w)));
                        u = end + 0.035f * (cu1 - cu0) + 0.02f;
                    }
                    if (last && frac(t * 1.7f) < 0.55f) {
                        out.rect(this, Math.min(cu1 - 0.03f, limit + 0.01f), v + rowH * 0.12f,
                                Math.min(cu1, limit + 0.01f + 0.03f), v + rowH * 0.82f, HOT, lit(230));
                    }
                }
            }
        }

        /** Oscilloscope: graticule and two travelling traces, the bright one with a soft halo. */
        private void waveform(Ink out) {
            for (int i = 1; i < 8; i++) out.line(this, i / 8f, 0.06f, i / 8f, 0.94f, px(0.6f), AMBER, lit(26));
            for (int i = 1; i < 4; i++) out.line(this, 0.03f, i / 4f, 0.97f, i / 4f, px(0.6f), AMBER, lit(i == 2 ? 55 : 26));
            float f1 = 1.5f + 2f * hash(seed, 6), f2 = 4f + 5f * hash(seed, 7);
            int segments = Math.max(16, Math.min(48, Math.round(widthPx / 2.5f)));
            float pu = 0f, pv = 0f, qv = 0f;
            for (int s = 0; s <= segments; s++) {
                float u = 0.04f + 0.92f * s / segments;
                float env = 0.6f + 0.4f * (float) Math.sin(t * 0.7f + u * 3f);
                float v = 0.5f + 0.27f * env * (float) Math.sin(6.283f * f1 * u - t * 2.4f)
                        + 0.07f * (float) Math.sin(6.283f * f2 * u + t * 5.1f);
                float q = 0.62f + 0.1f * (float) Math.sin(6.283f * (f1 * 0.5f) * u + t * 1.3f)
                        + (noise(u * 18f + t * 3f, seed) - 0.5f) * 0.12f;
                if (s > 0) {
                    out.line(this, pu, pv, u, v, px(3.2f), AMBER, lit(34));
                    out.line(this, pu, pv, u, v, px(1.3f), HOT, lit(225));
                    out.line(this, pu, qv, u, q, px(1f), AMBER, lit(110));
                }
                pu = u;
                pv = v;
                qv = q;
            }
        }

        /** Meters that rise and fall with held peak ticks over a baseline. */
        private void bars(Ink out) {
            int n = Math.max(5, Math.min(14, Math.round(widthPx / 7f)));
            float span = 0.88f / n;
            float rate = 0.9f + 1.1f * hash(seed, 8);
            out.line(this, 0.05f, 0.9f, 0.95f, 0.9f, px(0.9f), PHOSPHOR, lit(120));
            for (int i = 0; i < n; i++) {
                float u0 = 0.06f + i * span + span * 0.18f;
                float u1 = u0 + span * 0.64f;
                float level = level(t * rate, i);
                float peak = level;
                for (int k = 1; k <= 4; k++) peak = Math.max(peak, level(t * rate - k * 0.35f, i) - k * 0.03f);
                float top = 0.9f - 0.74f * level;
                out.rect(this, u0, top, u1, 0.9f, AMBER, lit(165));
                out.rect(this, u0, top, u1, Math.min(0.9f, top + 0.05f), HOT, lit(200));
                float peakV = 0.9f - 0.74f * peak;
                out.rect(this, u0, peakV - 0.025f, u1, peakV, HOT, lit(170));
            }
            for (int i = 0; i < 3; i++) {
                out.rect(this, 0.06f + i * 0.2f, 0.04f, 0.06f + i * 0.2f + 0.12f, 0.09f, AMBER, lit(110));
            }
        }

        private float level(float time, int bar) {
            float n = noise(time + bar * 1.37f, seed + bar * 31);
            return 0.12f + 0.82f * n * n * (3f - 2f * n);
        }

        /** The flagship's plan under a travelling scan line; one compartment blinks at a time. */
        private void schematic(Ink out, SvgAsset plan) {
            float shipW = Math.min(0.9f, 0.62f * 3f / Math.max(0.1f, aspect));
            float u0 = 0.5f - shipW * 0.5f, u1 = 0.5f + shipW * 0.5f;
            float shipH = shipW * aspect / 3f;
            float v0 = 0.44f - shipH * 0.5f, v1 = 0.44f + shipH * 0.5f;
            if (plan != null) {
                out.svg(this, plan, u0, v0, u1, v1, PHOSPHOR, lit(175));
            } else {
                float[] hull = {0.05f, 0.5f, 0.15f, 0.2f, 0.7f, 0.1f, 0.97f, 0.5f, 0.7f, 0.9f, 0.15f, 0.8f};
                for (int i = 0; i < hull.length; i += 2) {
                    int j = (i + 2) % hull.length;
                    out.line(this, u0 + hull[i] * shipW, v0 + hull[i + 1] * shipH,
                            u0 + hull[j] * shipW, v0 + hull[j + 1] * shipH, px(1.2f), PHOSPHOR, lit(175));
                }
            }
            float[] bulkheads = {18f, 30f, 46f, 64f, 84f, 98f};
            int beat = (int) Math.floor(t / 1.3f);
            int section = Math.floorMod(Math.round(hash(beat, seed) * 100f), bulkheads.length - 1);
            boolean upper = hash(beat, seed ^ 9) < 0.5f;
            float blink = 0.5f + 0.5f * (float) Math.sin(t * 9f);
            out.rect(this, u0 + shipW * bulkheads[section] / 120f, upper ? v0 + shipH * 0.2f : v0 + shipH * 0.5f,
                    u0 + shipW * bulkheads[section + 1] / 120f, upper ? v0 + shipH * 0.5f : v0 + shipH * 0.8f,
                    HOT, lit(60 + 90 * blink));
            float scan = frac(t / 3.4f);
            float su = u0 + shipW * scan;
            out.rect(this, Math.max(u0, su - 0.05f), v0 - 0.04f, su, v1 + 0.04f, AMBER, lit(40));
            out.line(this, su, v0 - 0.06f, su, v1 + 0.06f, px(1.2f), HOT, lit(230));
            float barV = Math.min(0.9f, v1 + 0.12f);
            out.rect(this, 0.08f, barV, 0.92f, barV + 0.05f, AMBER, lit(50));
            out.rect(this, 0.08f, barV, 0.08f + 0.84f * scan, barV + 0.05f, PHOSPHOR, lit(170));
            if (heightPx > 26f) {
                for (int i = 0; i < 4; i++) {
                    float len = 0.08f + 0.12f * hash(beat + i, seed ^ 3);
                    float u = 0.08f + i * 0.22f;
                    out.rect(this, u, 0.06f, u + len, 0.1f, AMBER, lit(130));
                }
            }
        }

        /** Channels of traffic sliding across, a level per channel, and the arrival flash. */
        private void comms(Ink out) {
            int rows = Math.max(3, Math.min(6, Math.round(heightPx / 6f)));
            float rowH = 0.84f / rows;
            for (int r = 0; r < rows; r++) {
                float v = 0.1f + r * rowH;
                float vc = v + rowH * 0.5f;
                out.rect(this, 0.04f, v + rowH * 0.25f, 0.04f + 0.1f + 0.06f * hash(r, seed), v + rowH * 0.7f,
                        AMBER, lit(140));
                out.line(this, 0.24f, vc, 0.96f, vc, px(0.6f), AMBER, lit(40));
                float speed = 0.12f + 0.2f * hash(seed, 80 + r);
                for (int k = 0; k < 3; k++) {
                    float pos = 1f - frac(t * speed + k / 3f + hash(seed, 90 + r));
                    float len = 0.04f + 0.08f * hash((int) Math.floor(t * speed + k / 3f) * 3 + k, seed + r);
                    float s0 = 0.24f + 0.72f * pos;
                    float s1 = Math.min(0.96f, s0 + len);
                    if (s1 - s0 < 0.005f) continue;
                    out.rect(this, s0, v + rowH * 0.25f, s1, v + rowH * 0.75f, k == 0 ? HOT : PHOSPHOR, lit(190));
                }
                float levelV = noise(t * 2.2f + r * 3.1f, seed);
                out.rect(this, 0.17f, v + rowH * (0.85f - 0.7f * levelV), 0.21f, v + rowH * 0.85f, PHOSPHOR, lit(170));
            }
            if (arrival > 0.02f) {
                float flash = frac(t * 2.4f) < 0.5f ? 1f : 0.35f;
                out.rect(this, 0.03f, 0.03f, 0.97f, 0.1f, HOT, Math.round(220 * arrival * flash));
            }
        }

        /** Ledger rows ticking in from the bottom, newest bright, with a flickering total. */
        private void ledger(Ink out) {
            int rows = Math.max(3, Math.min(8, Math.round(heightPx / 5f)));
            float rowH = 0.74f / rows;
            float period = 2.4f;
            int newest = (int) Math.floor(t / period);
            float age = t - newest * period;
            float slide = age < 0.3f ? (1f - age / 0.3f) * rowH : 0f;
            for (int r = 0; r < rows; r++) {
                int entry = newest - (rows - 1 - r);
                float v = 0.05f + r * rowH + slide;
                if (v + rowH > 0.8f) continue;
                boolean fresh = entry == newest;
                int tone = fresh ? HOT : AMBER;
                int alpha = fresh ? lit(age < 0.6f ? 255 : 210) : lit(140);
                out.rect(this, 0.05f, v + rowH * 0.25f, 0.05f + 0.18f + 0.25f * hash(entry, seed), v + rowH * 0.7f, tone, alpha);
                int digits = 3 + Math.floorMod(entry * 7 + seed, 3);
                float right = 0.95f;
                for (int k = 0; k < digits; k++) {
                    float u1 = right - k * 0.065f;
                    out.rect(this, u1 - 0.045f, v + rowH * 0.2f, u1, v + rowH * 0.75f, tone, alpha);
                }
            }
            out.line(this, 0.05f, 0.83f, 0.95f, 0.83f, px(0.9f), PHOSPHOR, lit(150));
            for (int k = 0; k < 6; k++) {
                float u1 = 0.95f - k * 0.065f;
                boolean changing = age < 0.5f && k < 2 && frac(t * 8f) < 0.5f;
                out.rect(this, u1 - 0.045f, 0.87f, u1, 0.96f, HOT, lit(changing ? 120 : 220));
            }
        }
    }

    // ---- batching ---------------------------------------------------------------

    /** Triangles grouped by colour, so a pass costs a few mesh draws rather than hundreds. */
    static final class Ink {
        private final Map<Integer, float[]> buffers = new LinkedHashMap<>();
        private final Map<Integer, Integer> sizes = new LinkedHashMap<>();

        void flush(CanvasContext context) {
            for (Map.Entry<Integer, float[]> entry : buffers.entrySet()) {
                int key = entry.getKey();
                int size = sizes.getOrDefault(key, 0);
                if (size == 0) continue;
                int alpha = key & 0xff;
                int rgb = key >>> 8;
                context.mesh(new CanvasMesh(Arrays.copyOf(entry.getValue(), size)), 0f, 0f, 1f,
                        new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha));
                sizes.put(key, 0);
            }
        }

        /** Triangles queued but not yet flushed, counted in vertices. */
        int pendingVertices() {
            int total = 0;
            for (int size : sizes.values()) total += size / 2;
            return total;
        }

        private void triangle(int rgb, int alpha, float x0, float y0, float x1, float y1, float x2, float y2) {
            int q = quantize(alpha);
            if (q <= 0) return;
            int key = (rgb << 8) | q;
            float[] buffer = buffers.get(key);
            int size = sizes.getOrDefault(key, 0);
            if (buffer == null) {
                buffer = new float[96];
                buffers.put(key, buffer);
            } else if (size + 6 > buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
                buffers.put(key, buffer);
            }
            buffer[size] = x0;
            buffer[size + 1] = y0;
            buffer[size + 2] = x1;
            buffer[size + 3] = y1;
            buffer[size + 4] = x2;
            buffer[size + 5] = y2;
            sizes.put(key, size + 6);
        }

        private void quad(int rgb, int alpha, float x0, float y0, float x1, float y1,
                          float x2, float y2, float x3, float y3) {
            triangle(rgb, alpha, x0, y0, x1, y1, x2, y2);
            triangle(rgb, alpha, x0, y0, x2, y2, x3, y3);
        }

        /** A rectangle of the screen's unit square, in perspective. */
        void rect(Screen s, float u0, float v0, float u1, float v1, int rgb, int alpha) {
            u0 = clamp01(u0);
            u1 = clamp01(u1);
            v0 = clamp01(v0);
            v1 = clamp01(v1);
            if (u1 <= u0 || v1 <= v0) return;
            quad(rgb, alpha, s.mapX(u0, v0), s.mapY(u0, v0), s.mapX(u1, v0), s.mapY(u1, v0),
                    s.mapX(u1, v1), s.mapY(u1, v1), s.mapX(u0, v1), s.mapY(u0, v1));
        }

        /** A stroke between two unit-square points, {@code width} canvas pixels thick. */
        void line(Screen s, float u0, float v0, float u1, float v1, float width, int rgb, int alpha) {
            float x0 = s.mapX(u0, v0), y0 = s.mapY(u0, v0);
            float x1 = s.mapX(u1, v1), y1 = s.mapY(u1, v1);
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.hypot(dx, dy);
            if (!(len > 1e-4f)) return;
            float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
            quad(rgb, alpha, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny);
        }

        /** A circle {@code radius} screen-heights across, kept round on a wide screen. */
        void ring(Screen s, float cu, float cv, float radius, float width, int rgb, int alpha) {
            int segments = 28;
            float ru = radius / Math.max(0.05f, s.aspect);
            float pu = cu + ru, pv = cv;
            for (int i = 1; i <= segments; i++) {
                double angle = Math.PI * 2 * i / segments;
                float u = cu + (float) Math.cos(angle) * ru;
                float v = cv + (float) Math.sin(angle) * radius;
                if (inside(pu, pv) && inside(u, v)) line(s, pu, pv, u, v, width, rgb, alpha);
                pu = u;
                pv = v;
            }
        }

        /** A square blip {@code size} canvas pixels across at a unit-square point. */
        void dot(Screen s, float u, float v, float size, int rgb, int alpha) {
            if (!inside(u, v)) return;
            float x = s.mapX(u, v), y = s.mapY(u, v), r = size * 0.5f;
            quad(rgb, alpha, x - r, y - r, x + r, y - r, x + r, y + r, x - r, y + r);
        }

        /** An SVG stretched over a unit-square box and carried through the screen's projection. */
        void svg(Screen s, SvgAsset asset, float u0, float v0, float u1, float v1, int rgb, int alpha) {
            float[] box = asset.viewBox();
            CanvasMeshBatch batch = asset.geometry();
            float su = (u1 - u0) / box[2], sv = (v1 - v0) / box[3];
            for (int i = 0; i < batch.layerCount(); i++) {
                CanvasMeshBatch.Layer layer = batch.layer(i);
                Color color = layer.color();
                int layerRgb = rgb((color.getRed() * ((rgb >> 16) & 0xff)) / 255,
                        (color.getGreen() * ((rgb >> 8) & 0xff)) / 255, (color.getBlue() * (rgb & 0xff)) / 255);
                int layerAlpha = color.getAlpha() * alpha / 255;
                CanvasMesh mesh = layer.mesh();
                float[] p = new float[6];
                for (int vtx = 0; vtx + 2 < mesh.vertexCount(); vtx += 3) {
                    for (int k = 0; k < 3; k++) {
                        float u = u0 + (mesh.x(vtx + k) - box[0]) * su;
                        float v = v0 + (mesh.y(vtx + k) - box[1]) * sv;
                        p[k * 2] = s.mapX(u, v);
                        p[k * 2 + 1] = s.mapY(u, v);
                    }
                    triangle(layerRgb, layerAlpha, p[0], p[1], p[2], p[3], p[4], p[5]);
                }
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------

    private static boolean inside(float u, float v) {
        return u >= 0f && u <= 1f && v >= 0f && v <= 1f;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    /** Alpha in steps of eight, so a frame's colours fall into a handful of batches. */
    static int quantize(int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return clamped < 4 ? 0 : Math.min(255, (clamped + 4) & ~7);
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    /** A stable pseudo-random value in [0, 1) for an integer pair. */
    static float hash(int value, int salt) {
        int x = value * 0x9E3779B1 ^ salt * 0x85EBCA6B;
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return (x >>> 8) / (float) (1 << 24);
    }

    /** Smooth value noise in [0, 1]. */
    static float noise(float x, int salt) {
        float floor = (float) Math.floor(x);
        int i = (int) floor;
        float f = x - floor;
        float s = f * f * (3f - 2f * f);
        float a = hash(i, salt), b = hash(i + 1, salt);
        return a + (b - a) * s;
    }
}
