package com.dillon.starsectormarines.ops.battleview;

/**
 * What one point of burnt ground looks like (`fire-nouns.md`, Look): the CPU
 * model of {@link ScorchShader}'s fragment program, line for line, so a review
 * frame drawn through Java2D shows the burnt ground the game draws.
 *
 * <p>Burnt ground is a field burnt days ago: singed straw-brown, black only where
 * heavy fuel stood, pale ash drifting over it. Scorch never paints over the
 * ground. It returns a <em>multiplier</em> that darkens and browns the ground's
 * own albedo, so the texture under it always reads, and an <em>overlay</em>
 * (colour and alpha) for what lies on top: straw, charcoal, ash, a creep's crust.
 *
 * <p>The burn's outline is the cells' burn amount, sampled between cell centres,
 * pushed about by two scales of noise, so it wanders through cells instead of
 * following them, with a band of singed ground just short of it. Inside, char
 * and ash vary at three scales with the fuel that stood there: grass leaves a
 * light singe and a sprinkling of ash and a few surviving tufts, hedges and the
 * ground under trees burn darker and leave ash piles. Creep burns to a cracked
 * crust of its own; pavement beside a fire takes only soot smudges.
 *
 * <p>The per-cell picture it reads is {@link ScorchLayer}'s mirror, the texture
 * the shader samples: R the burn amount, G the fuel's heaviness times that
 * amount (so a bilinear G over a bilinear R is the amount-weighted heaviness),
 * B what lies underfoot.
 */
final class ScorchShading {

    /** The burn picture, one texel per cell, clamped at the map's edge as the texture is. */
    interface Field {
        int width();
        int height();
        /** Texel channel {@code c} (0 R, 1 G, 2 B) of the cell, 0..255. */
        int texel(int x, int y, int c);
    }

    /** What lies underfoot, as the B channel holds it ({@code code * GROUND_STEP}). */
    static final int SOIL = 0, PAVED = 1, CREEP = 2, NONE = 3;
    static final int GROUND_STEP = 85;

    /** Char at the lightest fuel, and how much more the heaviest adds. */
    static final float CHAR_BASE = -0.05f, CHAR_HEAVY = 0.45f;
    /** Ash weight. */
    static final float ASH_WEIGHT = 1.35f;

    // Multipliers on the ground's albedo.
    static final float[] SINGE_MULT = {0.86f, 0.70f, 0.50f};
    static final float[] SCORCH_MULT = {0.60f, 0.48f, 0.35f};
    static final float[] CHAR_MULT = {0.19f, 0.17f, 0.15f};
    static final float SOOT_DARKEN = 0.45f;
    // Overlays.
    static final float[] STRAW = {0.55f, 0.42f, 0.24f};
    static final float[] CHARCOAL = {0.07f, 0.065f, 0.06f};
    static final float[] ASH = {0.74f, 0.72f, 0.68f};
    static final float[] CRUST = {0.16f, 0.10f, 0.11f};
    static final float[] CRUST_CRACK = {0.36f, 0.25f, 0.20f};

    /** Noise scales, in samples per cell: large patches, mid mottling, fine grain. */
    static final float LARGE = 0.55f, MID = 1.7f, FINE = 4.3f;
    /** Scale of the turned second mid and fine samples against the first. */
    static final float TURN_MID = 0.83f, TURN_FINE = 0.77f;
    /** Burn-amount level the outline sits at, and its antialiasing half-width. */
    static final float EDGE = 0.47f, EDGE_SOFT = 0.05f;
    /** How far the noise moves the outline, large and mid. */
    static final float EDGE_LARGE = 0.55f, EDGE_MID = 0.25f;
    /** Burn amount where the singe band begins, short of the outline. */
    static final float SINGE_FROM = 0.10f;

    private ScorchShading() { }

    /** Scratch for one thread. */
    static final class Scratch {
        final float[] n1 = new float[4], n2 = new float[4], n3 = new float[4], t2 = new float[4], t3 = new float[4],
                over = new float[4];
    }

    /**
     * Shades the point at map cell ({@code cx}, {@code cy}). Writes the albedo multiplier to {@code out[0..2]}
     * and the overlay's colour and alpha to {@code out[3..6]}; returns false, leaving the ground untouched,
     * where nothing near has burnt.
     */
    static boolean shade(Field f, float cx, float cy, Scratch s, float[] out) {
        float a = bilinear(f, cx, cy, 0);
        if (a <= 0.002f) return identity(out);
        float h = clamp(bilinear(f, cx, cy, 1) / a);
        int gx = clampX(f, (int) Math.floor(cx)), gy = clampY(f, (int) Math.floor(cy));
        int ground = Math.round(f.texel(gx, gy, 2) / (float) GROUND_STEP);
        if (ground == NONE) return identity(out);

        CreepNoise.sample(cx * LARGE, cy * LARGE, s.n1);
        CreepNoise.sample(cx * MID + 3.1f, cy * MID + 1.9f, s.n2);
        CreepNoise.sample(cx * FINE + 7.7f, cy * FINE + 5.3f, s.n3);
        // The same mid and fine noise turned and rescaled, averaged in: hides the value-noise lattice's squares.
        float rx = cx * 0.8f - cy * 0.6f, ry = cx * 0.6f + cy * 0.8f;
        CreepNoise.sample(rx * MID * TURN_MID + 5.7f, ry * MID * TURN_MID + 2.3f, s.t2);
        CreepNoise.sample(rx * FINE * TURN_FINE + 1.3f, ry * FINE * TURN_FINE + 8.9f, s.t3);
        for (int c = 0; c < 3; c++) {
            s.n2[c] = (s.n2[c] + s.t2[c]) * 0.5f;
            s.n3[c] = (s.n3[c] + s.t3[c]) * 0.5f;
        }
        float e = a + (s.n1[0] - 0.5f) * EDGE_LARGE + (s.n2[0] - 0.5f) * EDGE_MID;
        float burnt = smoothstep(EDGE - EDGE_SOFT, EDGE + EDGE_SOFT, e);
        float singe = smoothstep(SINGE_FROM, EDGE, e) * (1f - burnt);

        if (ground == PAVED) {
            // Pavement does not burn: soot smudges where the fire stood beside it.
            float smudge = smoothstep(0.15f, 0.65f, e) * smoothstep(0.35f, 0.75f, s.n2[2]);
            float m = 1f - SOOT_DARKEN * smudge;
            out[0] = m; out[1] = m; out[2] = m * 0.98f;
            out[3] = 0; out[4] = 0; out[5] = 0; out[6] = 0;
            return smudge > 0.002f;
        }

        // Contrast-stretched noise: large burn severity, mid mottling, fine clumps (smooth at any zoom), texel grain.
        float sev = stretch(s.n1[0], 2.6f), mot = stretch(s.n2[0], 3.4f), clump = stretch(s.n3[0], 3.1f), grain = s.n3[3];

        // A few tufts survive where light fuel burnt patchily.
        float tuft = ground == SOIL ? smoothstep(0.78f, 0.86f, clump * 0.6f + s.n2[2] * 0.4f) * (1f - h) : 0f;
        float survived = burnt * tuft * 0.7f;
        burnt -= survived;
        singe = Math.max(singe, survived);

        float charLevel = clamp(CHAR_BASE + CHAR_HEAVY * h
                + (sev - 0.5f) * 0.7f + (mot - 0.5f) * 0.45f + (clump - 0.5f) * 0.25f + (grain - 0.5f) * 0.08f);
        // Albedo: singed grass browned, burnt ground scorched through to char; the texture always carries through.
        float mr = lerp(1f, SINGE_MULT[0], singe * 0.85f);
        float mg = lerp(1f, SINGE_MULT[1], singe * 0.85f);
        float mb = lerp(1f, SINGE_MULT[2], singe * 0.85f);
        mr = lerp(mr, lerp(SCORCH_MULT[0], CHAR_MULT[0], charLevel), burnt);
        mg = lerp(mg, lerp(SCORCH_MULT[1], CHAR_MULT[1], charLevel), burnt);
        mb = lerp(mb, lerp(SCORCH_MULT[2], CHAR_MULT[2], charLevel), burnt);

        // Overlays, composited in order: straw over singe and light scorch, charcoal over char (what takes the
        // green out of it), then ash or crust.
        float[] o = s.over;
        o[0] = 0; o[1] = 0; o[2] = 0; o[3] = 0;
        over(o, STRAW[0], STRAW[1], STRAW[2], singe * 0.32f + burnt * (1f - charLevel) * (0.22f + 0.3f * mot));
        over(o, CHARCOAL[0], CHARCOAL[1], CHARCOAL[2], burnt * charLevel * 0.62f);
        if (ground == CREEP) {
            float crack = smoothstep(0.78f, 0.86f, s.n3[1]);
            over(o, lerp(CRUST[0], CRUST_CRACK[0], crack), lerp(CRUST[1], CRUST_CRACK[1], crack),
                    lerp(CRUST[2], CRUST_CRACK[2], crack), burnt * (0.55f + 0.35f * mot));
        } else {
            float piles = smoothstep(0.55f, 0.85f, stretch(s.n1[2], 2f) * 0.45f + clump * 0.55f + h * 0.3f - 0.15f)
                    * (0.25f + 0.75f * h) * (0.8f + 0.2f * grain);
            float sprinkle = smoothstep(0.80f, 0.92f, clump) * smoothstep(0.4f, 0.8f, mot) * 0.45f * (1f - h);
            over(o, ASH[0], ASH[1], ASH[2], clamp((piles + sprinkle) * ASH_WEIGHT) * burnt * 0.8f);
        }
        out[0] = mr; out[1] = mg; out[2] = mb;
        out[3] = o[0]; out[4] = o[1]; out[5] = o[2]; out[6] = clamp(o[3]);
        return true;
    }

    /** Bilinear channel {@code c} between cell centres, 0..1, clamped at the edge as {@code GL_CLAMP_TO_EDGE} is. */
    static float bilinear(Field f, float cx, float cy, int c) {
        float u = cx - 0.5f, v = cy - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int ax = clampX(f, x0), bx = clampX(f, x0 + 1), ay = clampY(f, y0), by = clampY(f, y0 + 1);
        float p = f.texel(ax, ay, c), q = f.texel(bx, ay, c), r = f.texel(ax, by, c), t = f.texel(bx, by, c);
        return lerp(lerp(p, q, fx), lerp(r, t, fx), fy) / 255f;
    }

    /** Straight-alpha "over" of a colour at {@code a} onto {@code acc} (r, g, b, alpha). */
    private static void over(float[] acc, float r, float g, float b, float a) {
        a = clamp(a);
        if (a <= 0f) return;
        float outA = a + acc[3] * (1f - a);
        acc[0] = (r * a + acc[0] * acc[3] * (1f - a)) / outA;
        acc[1] = (g * a + acc[1] * acc[3] * (1f - a)) / outA;
        acc[2] = (b * a + acc[2] * acc[3] * (1f - a)) / outA;
        acc[3] = outA;
    }

    private static boolean identity(float[] out) {
        out[0] = 1; out[1] = 1; out[2] = 1;
        out[3] = 0; out[4] = 0; out[5] = 0; out[6] = 0;
        return false;
    }

    private static int clampX(Field f, int x) { return Math.max(0, Math.min(f.width() - 1, x)); }

    private static int clampY(Field f, int y) { return Math.max(0, Math.min(f.height() - 1, y)); }

    private static float stretch(float n, float k) { return clamp((n - 0.5f) * k + 0.5f); }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float clamp(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
