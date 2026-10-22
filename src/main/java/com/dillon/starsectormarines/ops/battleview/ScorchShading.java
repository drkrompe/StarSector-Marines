package com.dillon.starsectormarines.ops.battleview;

/**
 * What one point of burnt ground looks like (`fire-nouns.md`, Look): a CPU model
 * over the fire field's per-cell picture, so burnt ground is shaded per pixel
 * rather than filled per cell.
 *
 * <p>Scorch never paints over the ground. It returns a <em>multiplier</em> that
 * darkens and browns the ground's own albedo, so the texture under it always
 * reads, and an <em>overlay</em> (colour and alpha) for what lies on top of the
 * ground: ash, a charred creep crust, embers.
 *
 * <p>The burn's outline is the cells' burn amount, sampled between cell centres,
 * pushed about by two scales of noise, so a burn's edge wanders through cells
 * instead of following them; just short of that outline a band of singed ground
 * browns the grass. Inside, char varies at three scales with the fuel that stood
 * there: grass leaves a light singe and a sprinkling of ash, hedges and the
 * ground under trees burn darker and leave grey-white ash piles, and a few tufts
 * survive where light fuel burnt patchily. Creep burns to a dark crust of its
 * own; pavement next to a fire takes only soot smudges.
 */
final class ScorchShading {

    /** The burn picture, per cell. */
    interface Field {
        int width();
        int height();
        /** 0..1: how far the cell has burnt. */
        float amount(int x, int y);
        /** 0..1: how heavy the fuel that burnt there was. */
        float heavy(int x, int y);
        /** What the cell is underfoot: {@link #SOIL}, {@link #PAVED}, {@link #CREEP} or {@link #NONE}. */
        int ground(int x, int y);
        /** Seconds since the cell burnt out; 0 while it burns. */
        float ageSeconds(int x, int y);
    }

    static final int SOIL = 0, PAVED = 1, CREEP = 2, NONE = 3;

    /** The looks on offer: the owner's pick decides which ships. */
    enum Style {
        /** Dark, patchy char with ash piles where fuel was heavy; stays as it was left. */
        MOTTLE(0.30f, 0.50f, 1.0f, 0.0f),
        /** A field burnt days ago: mostly singed brown, little black, pale ash drifting over heavy fuel. */
        SINGE(-0.05f, 0.45f, 1.35f, 0.0f),
        /** Fresh black char with dying embers that weathers toward grey ash over three minutes. */
        WEATHER(0.45f, 0.50f, 0.9f, 180f);

        /** Char at the lightest fuel, and how much more the heaviest adds. */
        final float charBase, charHeavy;
        /** Ash weight. */
        final float ash;
        /** Seconds over which fresh char weathers; 0 never. */
        final float weatherSeconds;

        Style(float charBase, float charHeavy, float ash, float weatherSeconds) {
            this.charBase = charBase;
            this.charHeavy = charHeavy;
            this.ash = ash;
            this.weatherSeconds = weatherSeconds;
        }
    }

    // Multipliers on the ground's albedo.
    static final float[] SINGE_MULT = {0.86f, 0.70f, 0.50f};
    static final float[] SCORCH_MULT = {0.60f, 0.48f, 0.35f};
    static final float[] CHAR_MULT = {0.19f, 0.17f, 0.15f};
    static final float[] WEATHERED_CHAR_MULT = {0.44f, 0.42f, 0.39f};
    static final float SOOT_DARKEN = 0.45f;
    // Overlays.
    static final float[] ASH = {0.74f, 0.72f, 0.68f};
    static final float[] WEATHERED_ASH = {0.80f, 0.79f, 0.76f};
    static final float[] CRUST = {0.16f, 0.10f, 0.11f};
    static final float[] CRUST_CRACK = {0.36f, 0.25f, 0.20f};
    static final float[] EMBER = {1.0f, 0.42f, 0.10f};
    static final float[] STRAW = {0.55f, 0.42f, 0.24f};
    static final float[] CHARCOAL = {0.07f, 0.065f, 0.06f};
    static final float[] WEATHERED_CHARCOAL = {0.33f, 0.32f, 0.30f};
    /** Seconds after burning out that embers still glow, where a style has them. */
    static final float EMBER_SECONDS = 20f;

    /** Noise scales, in samples per cell: large patches, mid mottling, fine grain. */
    static final float LARGE = 0.55f, MID = 1.7f, FINE = 4.3f;
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
    static boolean shade(Field f, float cx, float cy, Style style, Scratch s, float[] out) {
        // Bilinear burn amount and fuel weight between cell centres.
        float u = cx - 0.5f, v = cy - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        float a00 = amount(f, x0, y0), a10 = amount(f, x0 + 1, y0), a01 = amount(f, x0, y0 + 1), a11 = amount(f, x0 + 1, y0 + 1);
        float a = lerp(lerp(a00, a10, fx), lerp(a01, a11, fx), fy);
        if (a <= 0.002f) return identity(out);
        float w00 = a00 * (1 - fx) * (1 - fy), w10 = a10 * fx * (1 - fy), w01 = a01 * (1 - fx) * fy, w11 = a11 * fx * fy;
        float wsum = w00 + w10 + w01 + w11;
        float h = wsum <= 0f ? 0f : (w00 * heavy(f, x0, y0) + w10 * heavy(f, x0 + 1, y0)
                + w01 * heavy(f, x0, y0 + 1) + w11 * heavy(f, x0 + 1, y0 + 1)) / wsum;
        int gx = (int) Math.floor(cx), gy = (int) Math.floor(cy);
        int ground = inside(f, gx, gy) ? f.ground(gx, gy) : NONE;
        if (ground == NONE) return identity(out);
        float age = inside(f, gx, gy) ? f.ageSeconds(gx, gy) : 0f;

        CreepNoise.sample(cx * LARGE, cy * LARGE, s.n1);
        CreepNoise.sample(cx * MID + 3.1f, cy * MID + 1.9f, s.n2);
        CreepNoise.sample(cx * FINE + 7.7f, cy * FINE + 5.3f, s.n3);
        // The same mid and fine noise turned and rescaled, averaged in: hides the value-noise lattice's squares.
        float rx = cx * 0.8f - cy * 0.6f, ry = cx * 0.6f + cy * 0.8f;
        CreepNoise.sample(rx * MID * 0.83f + 5.7f, ry * MID * 0.83f + 2.3f, s.t2);
        CreepNoise.sample(rx * FINE * 0.77f + 1.3f, ry * FINE * 0.77f + 8.9f, s.t3);
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

        // Contrast-stretched noise: large burn severity, mid mottling, fine grain.
        // Clumps come from the fine cloud (smooth at any zoom); the texel grain only adds a little tooth.
        float sev = stretch(s.n1[0], 2.6f), mot = stretch(s.n2[0], 3.4f), clump = stretch(s.n3[0], 3.1f), grain = s.n3[3];

        // A few tufts survive where light fuel burnt patchily.
        float tuft = ground == SOIL ? smoothstep(0.78f, 0.86f, clump * 0.6f + s.n2[2] * 0.4f) * (1f - h) : 0f;
        float survived = burnt * tuft * 0.7f;
        burnt -= survived;
        singe = Math.max(singe, survived);

        float weather = style.weatherSeconds <= 0f ? 0f : clamp(age / style.weatherSeconds);
        float charLevel = clamp(style.charBase + style.charHeavy * h
                + (sev - 0.5f) * 0.7f + (mot - 0.5f) * 0.45f + (clump - 0.5f) * 0.25f + (grain - 0.5f) * 0.08f);
        float[] charMult = mix3(CHAR_MULT, WEATHERED_CHAR_MULT, weather);
        // Albedo: singed grass browned, burnt ground scorched through to char; the texture always carries through.
        float mr = lerp(1f, SINGE_MULT[0], singe * 0.85f);
        float mg = lerp(1f, SINGE_MULT[1], singe * 0.85f);
        float mb = lerp(1f, SINGE_MULT[2], singe * 0.85f);
        mr = lerp(mr, lerp(SCORCH_MULT[0], charMult[0], charLevel), burnt);
        mg = lerp(mg, lerp(SCORCH_MULT[1], charMult[1], charLevel), burnt);
        mb = lerp(mb, lerp(SCORCH_MULT[2], charMult[2], charLevel), burnt);

        // Overlays, composited in order: straw over singe and light scorch, charcoal over char (what takes the
        // green out of it), then ash or crust, then embers.
        float[] o = s.over;
        o[0] = 0; o[1] = 0; o[2] = 0; o[3] = 0;
        over(o, STRAW, singe * 0.32f + burnt * (1f - charLevel) * (0.22f + 0.3f * mot));
        float[] charcoal = mix3(CHARCOAL, WEATHERED_CHARCOAL, weather);
        over(o, charcoal, burnt * charLevel * 0.62f);
        if (ground == CREEP) {
            float crack = smoothstep(0.78f, 0.86f, s.n3[1]);
            over(o, mix3(CRUST, CRUST_CRACK, crack), burnt * (0.55f + 0.35f * mot));
        } else {
            float piles = smoothstep(0.55f, 0.85f, stretch(s.n1[2], 2f) * 0.45f + clump * 0.55f + h * 0.3f - 0.15f)
                    * (0.25f + 0.75f * h) * (0.8f + 0.2f * grain);
            float sprinkle = smoothstep(0.80f, 0.92f, clump) * smoothstep(0.4f, 0.8f, mot) * 0.45f * (1f - h);
            float ash = clamp((piles + sprinkle) * style.ash) * burnt;
            over(o, mix3(ASH, WEATHERED_ASH, weather), ash * (0.8f + 0.15f * weather));
        }
        if (style.weatherSeconds > 0f && burnt > 0f && age < EMBER_SECONDS) {
            over(o, EMBER, smoothstep(0.86f, 0.94f, clump) * (1f - age / EMBER_SECONDS) * burnt * 0.85f);
        }
        out[0] = mr; out[1] = mg; out[2] = mb;
        out[3] = o[0]; out[4] = o[1]; out[5] = o[2]; out[6] = clamp(o[3]);
        return true;
    }

    /** Straight-alpha "over" of a colour at {@code a} onto {@code acc} (r, g, b, alpha). */
    private static void over(float[] acc, float[] c, float a) {
        a = clamp(a);
        if (a <= 0f) return;
        float outA = a + acc[3] * (1f - a);
        for (int i = 0; i < 3; i++) acc[i] = (c[i] * a + acc[i] * acc[3] * (1f - a)) / outA;
        acc[3] = outA;
    }

    private static float stretch(float n, float k) { return clamp((n - 0.5f) * k + 0.5f); }

    private static boolean identity(float[] out) {
        out[0] = 1; out[1] = 1; out[2] = 1;
        out[3] = 0; out[4] = 0; out[5] = 0; out[6] = 0;
        return false;
    }

    private static boolean inside(Field f, int x, int y) {
        return x >= 0 && y >= 0 && x < f.width() && y < f.height();
    }

    private static float amount(Field f, int x, int y) { return inside(f, x, y) ? f.amount(x, y) : 0f; }

    private static float heavy(Field f, int x, int y) { return inside(f, x, y) ? f.heavy(x, y) : 0f; }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float clamp(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float[] mix3(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }
}
