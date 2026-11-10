package com.dillon.starsectormarines.ops.battleview;

/**
 * The colour rules of a glacial crevasse (`voids.md`, natural styles): a natural drop whose walls are ice. GL-free;
 * {@link GlacialVoidRenderSystem} and {@link NaturalVoidRenderSystem} draw with it.
 *
 * <p>Ice is lit through, not only on: where a rock face's shade is grey, an ice face's is blue, and the deeper the
 * light has gone into the crevasse the bluer and darker everything is ({@link #deepTint}), so the walls run from
 * blue-white at the lip to a near-black blue at the floor. Snow lies drifted on every ledge, thickest back against
 * the wall above and thinning to bare ice where the ledge breaks over the face below ({@link #snowCover}). The lip
 * is a hard snow cornice at the drop, taking the snowfield's own tone back from it, so the ground tiles' stepped
 * edge does not show. The floor is old ice with pools of meltwater frozen glassy in it.
 */
final class IceVoidLook {

    private IceVoidLook() { }

    /** The blue the crevasse falls to, and how much of it the floor takes; walls and ledges take it by depth. */
    static final float DEEP_R = .03f, DEEP_G = .09f, DEEP_B = .22f, DEEP_MIX = .6f;
    /** How the tint gathers with depth: above 1 the upper walls keep their own blue-white and the depths go dark. */
    static final float DEEP_CURVE = 1.3f;
    /** The colour of shade on ice and snow, where rock's is black: the sky's blue through the ice. */
    static final float SHADE_R = .05f, SHADE_G = .13f, SHADE_B = .28f;
    /** Snow on a ledge: cells back from the break where it starts, and where it lies full. */
    static final float SNOW_FROM = .12f, SNOW_FULL = 1.1f;
    /** The snowfield's own tone in daylight, the snow tiles', which the lip takes back from the drop. */
    static final float PLAIN_R = .75f, PLAIN_G = .785f, PLAIN_B = .825f;
    /** Cells back from the first face over which the cornice gives way to the snowfield. */
    static final float CORNICE_CELLS = .2f;
    /** The bridge's edge over the drop: packed snow. */
    static final float BRIDGE_R = .74f, BRIDGE_G = .80f, BRIDGE_B = .88f;
    /** The cold mist in the crevasse: its colour. */
    static final float MIST_R = .80f, MIST_G = .87f, MIST_B = .94f;

    /** How much of the deep blue a piece {@code depth} metres down takes, over a floor {@code floorMeters} down. */
    static float deepTint(float depth, float floorMeters) {
        float t = clamp01(depth / Math.max(1f, floorMeters));
        return DEEP_MIX * (float) Math.pow(t, DEEP_CURVE);
    }

    /** How much snow lies on a ledge {@code cellsBack} cells back from where it breaks over the face, 0 to 1. */
    static float snowCover(float cellsBack) {
        return smooth(SNOW_FROM, SNOW_FULL, cellsBack);
    }

    /**
     * A lip or ledge piece of a crevasse: a snow cornice on the lip going over to the snowfield's tone, snow drifted
     * on the ledges over blue ice, shaded blue where the field left it dark.
     *
     * @param n1    slow noise at the piece, about -1..1
     * @param n2    fine noise at the piece, about -1..1
     * @param light the sun and sky the field gave the piece
     * @param edge  for a ledge, cells to the face below it; for the lip, cells back from the first face
     */
    static void ice(boolean lip, float n1, float n2, float light, float edge, float[] rgb) {
        float r, g, b;
        float snow;
        if (lip) {
            // The cornice: wind-hardened snow with blue ice showing at its very edge, the snowfield behind it.
            float bare = clamp01(1f - edge * 8f + n2 * .3f) * .55f;
            r = lerp(.82f, .58f, bare); g = lerp(.86f, .76f, bare); b = lerp(.91f, .88f, bare);
            float plain = smooth(0f, CORNICE_CELLS, edge);
            r = lerp(r, PLAIN_R, plain); g = lerp(g, PLAIN_G, plain); b = lerp(b, PLAIN_B, plain);
            n2 *= 1f - plain;
            // Back from the drop the lip is the snowfield, which the battle's own sun lights as it lights the tiles:
            // the field's shade there would draw a grey seam where the lip frays onto them.
            light = lerp(light, 1f, plain);
            snow = 1f;
        } else {
            // Blue ice, milky in places, under a drift of snow that thins to nothing at the break.
            float milk = clamp01(.5f + n1);
            float ir = lerp(.42f, .66f, milk), ig = lerp(.66f, .80f, milk), ib = lerp(.80f, .88f, milk);
            snow = clamp01(snowCover(edge) * (.8f + .4f * n1));
            r = lerp(ir, .90f, snow); g = lerp(ig, .93f, snow); b = lerp(ib, .97f, snow);
        }
        float grain = .95f + .08f * (n2 * .5f + .5f);
        // Snow in shade is the sky's blue, not grey: lerp towards the shade colour rather than towards black.
        float lit = clamp01(light);
        float over = Math.max(0f, Math.min(1.1f, light) - 1f);
        rgb[0] = lerp(SHADE_R + .25f * snow * .3f, r, lit) * grain + over * .1f;
        rgb[1] = lerp(SHADE_G + .25f * snow * .4f, g, lit) * grain + over * .1f;
        rgb[2] = lerp(SHADE_B + .25f * snow * .6f, b, lit) * grain + over * .1f;
    }

    /**
     * A lip, ledge or fray piece of a crevasse's {@code band} ({@code k == 0} the lip), from the piece's own place,
     * light and edge, into {@code into} at the piece's slot.
     */
    static void ice(NaturalVoidField.Band band, int q, int k, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1];
        float n1 = NaturalVoidField.Noise.fbm(x * .55f, y * .55f, seed * 7 + 11);
        float n2 = NaturalVoidField.Noise.value(x * 3.1f, y * 3.1f, seed * 7 + 13);
        float[] rgb = new float[3];
        ice(k == 0, n1, n2, a[o + 2], a[o + 3], rgb);
        System.arraycopy(rgb, 0, into, q * 3, 3);
    }

    /**
     * The top of a crevasse's ice wall seen from its floor: the snowfield, scoured by the gale into long drifts
     * across the wind, blue ice blown bare along the rim. {@code attrs[3]} is cells in from the rim.
     */
    static void snowfield(NaturalVoidField.Band band, int q, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1], light = a[o + 2], inside = a[o + 3];
        // Sastrugi: ridges across the wind, so drawn along the wind's normal.
        float across = x * GlacialVoidRenderSystem.WIND_Y - y * GlacialVoidRenderSystem.WIND_X;
        float along = x * GlacialVoidRenderSystem.WIND_X + y * GlacialVoidRenderSystem.WIND_Y;
        float drift = NaturalVoidField.Noise.fbm(along * .9f, across * .12f, seed * 7 + 41);
        float n2 = NaturalVoidField.Noise.value(x * 2.1f, y * 2.1f, seed * 7 + 43);
        float r = PLAIN_R + .03f * drift, g = PLAIN_G + .03f * drift, b = PLAIN_B + .025f * drift;
        float bare = clamp01((1f - inside * 1.2f + n2 * .35f) * .9f);
        r = lerp(r, .55f, bare * .6f); g = lerp(g, .72f, bare * .6f); b = lerp(b, .86f, bare * .6f);
        float lit = clamp01(light);
        into[q * 3] = lerp(SHADE_R + .2f, r, lit);
        into[q * 3 + 1] = lerp(SHADE_G + .2f, g, lit);
        into[q * 3 + 2] = lerp(SHADE_B + .2f, b, lit);
    }

    /**
     * The crevasse's floor: old dark ice, milky in places and pale where snow has drifted against the walls, with
     * pools of meltwater frozen glassy and dark, a glint moving on them.
     *
     * @param in    cells in from the floor's edge
     * @param pool  slow noise, about -1..1: a pool where it runs high, away from the walls
     * @param crack slow noise, about -1..1: milky old ice where it runs low
     * @param glint the moving shimmer on a pool, about -1..1
     * @param sky   how much sky the walls leave open over the piece
     * @param light the sun and sky the field gave the piece
     */
    static void floor(float in, float pool, float crack, float glint, float sky, float light, float[] rgb) {
        float r = .20f, g = .38f, b = .50f;
        // Snow drifted against the foot of the walls.
        float drift = clamp01(1f - in * 1.6f + pool * .2f);
        r = lerp(r, .70f, drift); g = lerp(g, .80f, drift); b = lerp(b, .90f, drift);
        // Old ice, milky where the slow noise runs low and clear where it runs high.
        float milk = clamp01(.5f - crack) * (1f - drift);
        r = lerp(r, .42f, milk * .5f); g = lerp(g, .58f, milk * .5f); b = lerp(b, .68f, milk * .5f);
        // Meltwater pools, frozen glassy: darker and clearer than the ice round them, a glint moving on them.
        float melt = smooth(.15f, .35f, pool) * smooth(.6f, 1.4f, in);
        r = lerp(r, .06f, melt * .8f); g = lerp(g, .20f, melt * .8f); b = lerp(b, .30f, melt * .8f);
        float shine = melt * smooth(.2f, .8f, glint) * .25f * (.4f + .6f * sky);
        // Deep in a crevasse the floor is lit by the strip of sky over it; the sun's patch, which the field works out
        // piece by piece and so cuts jagged, counts for little, and not at all on the glassy pools.
        float bright = .5f + .5f * sky + .08f * Math.min(1f, light);
        rgb[0] = r * bright + .55f * shine;
        rgb[1] = g * bright + .62f * shine;
        rgb[2] = b * bright + .66f * shine;
    }

    static float smooth(float from, float to, float v) {
        float t = clamp01((v - from) / (to - from));
        return t * t * (3f - 2f * t);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
