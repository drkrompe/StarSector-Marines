package com.dillon.starsectormarines.ops.battleview;

/**
 * The colour rules of a lava rift (`voids.md`, natural styles): a natural drop whose floor is molten rock. GL-free;
 * {@link MoltenVoidRenderSystem} and {@link NaturalVoidRenderSystem} draw with it.
 *
 * <p>A lava floor gives light rather than taking it: the battle's sun and the sky the walls leave open do not
 * shade it, and its light falls on everything round it, strongest on the rock just above the melt and fading
 * up the walls ({@link #wallGlow}), on the ledges by their height above it, and a little way onto the ground
 * beside the lip ({@link #lipGlow}). The crust that floats on the melt breaks up against the walls, so the
 * floor runs incandescent along its edges and crusted in its middle ({@link #crustCover}).
 */
final class LavaVoidLook {

    private LavaVoidLook() { }

    /** The light the melt throws: a deep orange. */
    static final float GLOW_R = 1f, GLOW_G = .42f, GLOW_B = .10f;
    /** How strongly the melt lights the rock just above it, as cover over the rock, and the metres it halves over. */
    static final float WALL_GLOW = .62f, WALL_GLOW_HALF_METERS = 7f;
    /** How strongly the ground at the lip is lit, and the cells the light halves over going out from the edge. */
    static final float LIP_GLOW = .42f, LIP_GLOW_HALF_CELLS = 1.6f;
    /** How far out from the lip the ground is lit at all, in cells. */
    static final float LIP_GLOW_CELLS = 4.5f;
    /** The depth of floor whose light reaches the lip at full strength; a deeper floor lights it less. */
    static final float LIP_GLOW_DEPTH = 20f;
    /** The crust: cells from the floor's edge where it starts to cover the melt, and where it covers it fully. */
    static final float CRUST_FROM = .35f, CRUST_FULL = 2.4f;
    /** The basalt plain's ground in daylight, the talus tiles' tone, which the lip takes back from the drop. */
    static final float PLAIN_R = .27f, PLAIN_G = .25f, PLAIN_B = .2f;
    /** Cells in from the floor's edge where the crust's own edge lies, before it wanders. */
    static final float CRUST_EDGE_CELLS = .95f;

    /** How much light the melt throws on rock {@code metresAbove} metres above it, 0 to {@link #WALL_GLOW}. */
    static float wallGlow(float metresAbove) {
        return WALL_GLOW * (float) Math.pow(.5, Math.max(0f, metresAbove) / WALL_GLOW_HALF_METERS);
    }

    /**
     * How much light from a floor {@code floorMeters} down falls on ground {@code cellsOut} cells out from the lip:
     * none past {@link #LIP_GLOW_CELLS}, and less the deeper the floor, which sees less of the open air above.
     */
    static float lipGlow(float cellsOut, float floorMeters) {
        if (cellsOut >= LIP_GLOW_CELLS) return 0f;
        float out = Math.max(0f, cellsOut);
        float fade = 1f - smooth(LIP_GLOW_CELLS * .6f, LIP_GLOW_CELLS, out);
        float depth = LIP_GLOW_DEPTH / Math.max(LIP_GLOW_DEPTH, floorMeters);
        return LIP_GLOW * depth * fade * (float) Math.pow(.5, out / LIP_GLOW_HALF_CELLS);
    }

    /** How much of the melt the crust covers {@code cellsIn} cells in from the floor's edge, 0 to 1. */
    static float crustCover(float cellsIn) {
        return smooth(CRUST_FROM, CRUST_FULL, cellsIn);
    }

    /**
     * The melt's own colour where its heat is {@code heat}, 0 (dull red, nearly crusted) to 1 (yellow-white,
     * freshly broken), into {@code rgb}.
     */
    static void molten(float heat, float[] rgb) {
        float h = clamp01(heat);
        if (h < .55f) {
            float t = h / .55f;
            rgb[0] = lerp(.42f, 1f, t);
            rgb[1] = lerp(.05f, .36f, t);
            rgb[2] = lerp(.02f, .05f, t);
        } else {
            float t = (h - .55f) / .45f;
            rgb[0] = 1f;
            rgb[1] = lerp(.36f, .86f, t);
            rgb[2] = lerp(.05f, .42f, t);
        }
    }

    /**
     * A lip or ledge piece of a lava rift: scorched basalt and ash on the lip, cooled crust and ash on the ledges,
     * lit as the field lit it, then lit from below by the melt {@code metresAbove} metres beneath it.
     *
     * @param n1    slow noise at the piece, about -1..1
     * @param n2    fine noise at the piece, about -1..1
     * @param light the sun and sky the field gave the piece
     * @param edge  for a ledge, cells to the face below it; for the lip, cells back from the first face
     */
    static void rock(boolean lip, float n1, float n2, float light, float edge, float metresAbove, float[] rgb) {
        float r, g, b;
        if (lip) {
            float ash = clamp01(.45f + n1);
            r = lerp(.15f, .27f, ash); g = lerp(.14f, .25f, ash); b = lerp(.13f, .22f, ash);
            // Scorched at the drop, the plain's own tone farther back, where the lip runs on under the ground's
            // tiles and shows only through their stepped edge.
            float plain = smooth(0f, .5f, edge);
            r = lerp(r, PLAIN_R, plain); g = lerp(g, PLAIN_G, plain); b = lerp(b, PLAIN_B, plain);
            n2 *= 1f - plain;
        } else {
            float ash = clamp01((n1 + .1f) * 1.8f - edge * .2f);
            r = lerp(.13f, .30f, ash); g = lerp(.11f, .27f, ash); b = lerp(.10f, .25f, ash);
            // Rust and sulphur where the ledge breaks over the face below.
            float rim = clamp01(1f - edge * 2.2f) * clamp01(.6f + n2);
            r = lerp(r, .38f, rim * .45f); g = lerp(g, .22f, rim * .45f); b = lerp(b, .12f, rim * .45f);
        }
        float grain = .93f + .14f * (n2 * .5f + .5f);
        float lit = Math.min(1.1f, light);
        r *= grain * lit; g *= grain * lit; b *= grain * lit;
        float glow = metresAbove < 0f ? 0f : wallGlow(metresAbove);
        rgb[0] = lerp(r, GLOW_R, glow * .55f);
        rgb[1] = lerp(g, GLOW_G, glow * .55f);
        rgb[2] = lerp(b, GLOW_B, glow * .55f);
    }

    /**
     * A lip, ledge or fray piece of a lava rift's {@code band} ({@code k == 0} the lip), from the piece's own place,
     * light and edge, under a melt {@code floorMeters} down, into {@code into} at the piece's slot.
     */
    static void rock(NaturalVoidField.Band band, int q, int k, int seed, float floorMeters, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1];
        float n1 = NaturalVoidField.Noise.fbm(x * .55f, y * .55f, seed * 7 + 11);
        float n2 = NaturalVoidField.Noise.value(x * 3.1f, y * 3.1f, seed * 7 + 13);
        float[] rgb = new float[3];
        rock(k == 0, n1, n2, a[o + 2], a[o + 3], floorMeters - band.depth, rgb);
        System.arraycopy(rgb, 0, into, q * 3, 3);
    }

    static float smooth(float from, float to, float v) {
        float t = clamp01((v - from) / (to - from));
        return t * t * (3f - 2f * t);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
