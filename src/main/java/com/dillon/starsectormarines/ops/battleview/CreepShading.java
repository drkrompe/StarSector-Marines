package com.dillon.starsectormarines.ops.battleview;

/**
 * What one point of creep looks like: the CPU model of {@link CreepShader}'s
 * fragment program, line for line, so a review frame drawn through Java2D shows
 * the creep the game draws.
 *
 * <p>Inputs are the field at that point, sampled bilinearly between cell
 * centres (coverage, vitality, pulse distance in cells, climbed wall), the map
 * position in cells, the presentation clock and the painted material's
 * luminance there ({@link CreepMaterial}). The material carries the surface:
 * its dark values are veins, its mid values the film, its bright values the
 * nodules, and the palette colours each. Its relief and a coarse cloud from
 * {@link CreepNoise} break the coverage edge into a lumpy lip. A pulse runs
 * along the veins toward the source. A wall carries only a patchy climb,
 * swollen into a dark ridge, so the building still reads under it; thin film
 * lets the ground show through, so a town under creep is still a town; dead
 * growth loses its colour and its pulse.
 */
final class CreepShading {

    /** How far the coarse cloud and the material relief move the coverage edge. */
    static final float EDGE_NOISE = 0.45f, EDGE_RELIEF = 0.35f;
    static final float EDGE_LO = 0.30f, EDGE_HI = 0.50f;
    /** Pulse phase per cell of path distance and per second; positive both, so a crest walks toward the source. */
    static final float PULSE_PER_CELL = 0.9f, PULSE_PER_SECOND = 2.6f;
    /** Material luminance below which a point is vein, and above which it is nodule. */
    static final float VEIN_LO = 0.20f, VEIN_HI = 0.38f, NODULE_LO = 0.55f, NODULE_HI = 0.80f;
    /** How hard the pulse lights the veins. */
    static final float GLOW = 0.35f;
    /** Opacity of thin film between nodules and veins: the town still shows through it. */
    static final float FILM_ALPHA = 0.78f;
    /** Offset, in cells, of the four extra coverage taps that soften the edge off the cell grid. */
    static final float TAP = 0.9f;

    private CreepShading() { }

    /**
     * Shades one point into straight (not premultiplied) RGBA in {@code out}.
     *
     * @param cov      coverage softened over neighbouring cells ({@link #softCoverage})
     * @param material the surface luminance here ({@link CreepMaterial#surface})
     * @param noise    scratch of at least four floats
     */
    static void shade(float cov, float vit, float pulseCells, float wall, float cellX, float cellY,
                      float time, float material, CreepPalette pal, float[] noise, float[] out) {
        CreepNoise.sample(cellX, cellY, noise);
        float nr = noise[0];
        float l = material;
        float m = smoothstep(EDGE_LO, EDGE_HI, cov + (nr - 0.5f) * EDGE_NOISE + (l - 0.5f) * EDGE_RELIEF);
        float climb = smoothstep(0.35f, 0.60f, l * 0.8f + nr * 0.4f);
        m *= 1f + (climb - 1f) * wall;
        if (m <= 0.002f) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        float rim = smoothstep(0f, 0.35f, m) * (1f - smoothstep(0.35f, 1f, m));
        float vein = 1f - smoothstep(VEIN_LO, VEIN_HI, l);
        float fleshy = smoothstep(NODULE_LO, NODULE_HI, l);
        float thick = smoothstep(0.3f, 0.7f, nr);
        float value = (0.62f + 0.8f * (l - 0.46f)) * (0.85f + 0.3f * thick);
        float wave = 0.5f + 0.5f * (float) Math.sin(pulseCells * PULSE_PER_CELL + time * PULSE_PER_SECOND);
        float glow = vein * (0.25f + 0.75f * wave * wave) * vit * GLOW;
        float speck = smoothstep(0.92f, 0.99f, l) * vit;
        float mottle = 0.7f + 0.5f * nr;
        for (int c = 0; c < 3; c++) {
            float body = mix(pal.base()[c], pal.flesh()[c], fleshy) * value;
            float col = mix(body, pal.vein()[c], vein * 0.8f) + pal.glow()[c] * glow;
            col = mix(col, pal.rim()[c], rim * 0.35f) + pal.glow()[c] * speck * 0.6f;
            float ridge = pal.vein()[c] * 1.3f + pal.flesh()[c] * 0.4f + pal.glow()[c] * glow;
            col = mix(col, ridge, wall * 0.7f);
            col = mix(pal.dead()[c] * mottle * value, col, vit);
            out[c] = clamp01(col);
        }
        float film = mix(FILM_ALPHA * mix(0.8f, 1.15f, thick), 1f, Math.max(fleshy, vein));
        float a = m * film * mix(0.75f, 1f, vit);
        out[3] = clamp01(mix(a, m * 0.95f, wall));
    }

    /** Coverage at a point and four taps {@link #TAP} cells away, so the edge is a ramp a few cells wide. */
    static float softCoverage(float centre, float east, float west, float north, float south) {
        return 0.4f * centre + 0.15f * (east + west + north + south);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float mix(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
