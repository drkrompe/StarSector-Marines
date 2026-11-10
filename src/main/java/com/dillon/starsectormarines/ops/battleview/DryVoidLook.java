package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

/**
 * The colour rules of a dry natural drop (`voids.md`, natural styles): a cut through bedded rock with no water in
 * its walls, its strata in the walls and on the ledges, loose sediment lying back on each ledge, and a dry floor of
 * fine stuff and stones. The toxic gorge shares its walls and ledges and has a floor of sludge instead. GL-free; {@link DryVoidFloor} and {@link NaturalVoidRenderSystem} draw with it. Each dry style
 * is one {@link Palette}.
 *
 * <p>Two rules every dry style shares. Shade in the drop is the rock's own light thrown back across it, not black,
 * so a palette names the colour its shade falls toward. And the lip gives way to the plain's own tone a little back
 * from the edge, lit as the battle's sun lights the tiles, so the ground tiles' stepped edge does not show and no
 * partly covering piece is needed to fade it (one would thin the ground target's alpha, which reads as shade).
 */
final class DryVoidLook {

    private DryVoidLook() { }

    /** A colour. */
    record Rgb(float r, float g, float b) { }

    /**
     * One dry style's colours.
     *
     * @param cap      the hard rock at the lip's very edge
     * @param plain    the ground tiles' own tone, which the lip gives way to back from the drop
     * @param upper    a ledge's rock, one of the two strata the ledges alternate between
     * @param lower    the other stratum
     * @param drift    loose sediment lying back on a ledge
     * @param scree    pale broken stone where a ledge breaks over the face below
     * @param shade    what shade falls toward, and {@code shadeMix} how far a wall in full shade goes
     * @param deep     the tint the drop takes with depth, and {@code deepMix} how much the floor takes
     * @param bed      the floor's fine bed (a wash's sand, a gorge's dust)
     * @param bar      the coarser stuff between its channels (gravel bars, rubble)
     * @param stones   the first of the five medium and large field stones in the nature sheet's frames that lie on
     *                 the floor, fallen from the walls (the same set the plain's ground scatters), or -1 for none;
     *                 {@code stoneTint} multiplies their colour into the drop's light, and {@code stoneShare} is the
     *                 share of the floor's scatter points that hold one
     * @param bridge   a bridge's edge over the drop
     * @param haze     the air low in the drop, and {@code hazeAlpha} how thick (0 for none)
     */
    record Palette(Rgb cap, Rgb plain, Rgb upper, Rgb lower, Rgb drift, Rgb scree, Rgb shade, float shadeMix,
                   Rgb deep, float deepMix, Rgb bed, Rgb bar, int stones, Rgb stoneTint,
                   float stoneShare, Rgb bridge, Rgb haze, float hazeAlpha) { }

    /**
     * A desert canyon (ARID worlds): banded red and ochre sandstone, wind-blown sand lying on the ledges, a dry wash
     * of pale sand braided between gravel bars, boulders fallen from the walls; warm light thrown back into the shade
     * and a dust haze in the depths.
     */
    static final Palette CANYON = new Palette(
            new Rgb(.70f, .56f, .36f), new Rgb(.633f, .528f, .321f),
            new Rgb(.70f, .40f, .24f), new Rgb(.76f, .56f, .34f),
            new Rgb(.74f, .60f, .38f), new Rgb(.80f, .67f, .48f),
            new Rgb(.22f, .12f, .10f), .75f,
            new Rgb(.36f, .22f, .18f), .2f,
            new Rgb(.80f, .66f, .45f), new Rgb(.66f, .50f, .35f), 34, new Rgb(1.2f, .98f, .8f), .3f,
            new Rgb(.62f, .40f, .25f), new Rgb(.88f, .72f, .52f), .07f);

    /**
     * A dry rock gorge (ROCK barren worlds): grey regolith and bedrock in strata, grit lying on the ledges, a floor of
     * dust and rubble thick with fallen basalt blocks; no water and, on an airless world, no haze.
     */
    static final Palette REGOLITH = new Palette(
            new Rgb(.60f, .56f, .52f), new Rgb(.619f, .543f, .489f),
            new Rgb(.50f, .48f, .46f), new Rgb(.38f, .37f, .37f),
            new Rgb(.58f, .53f, .48f), new Rgb(.66f, .62f, .58f),
            new Rgb(.08f, .08f, .09f), .85f,
            new Rgb(.16f, .17f, .19f), .25f,
            new Rgb(.56f, .51f, .46f), new Rgb(.42f, .40f, .38f), 41, new Rgb(1.1f, 1.04f, 1f), .55f,
            new Rgb(.40f, .38f, .36f), new Rgb(.6f, .58f, .56f), 0f);

    /**
     * A toxic gorge (TOXIC worlds): corroded olive and khaki rock in strata, sulphur crusted on the ledges, pale
     * efflorescence where they break; its floor is acid sludge ({@link ToxicVoidFloor}, not the dry floor), its air
     * yellow-green fumes. The toxic ground's own field stones are not drawn on the sludge.
     */
    static final Palette TOXIC = new Palette(
            new Rgb(.56f, .52f, .32f), new Rgb(.485f, .453f, .284f),
            new Rgb(.42f, .42f, .30f), new Rgb(.50f, .44f, .26f),
            new Rgb(.66f, .58f, .24f), new Rgb(.70f, .67f, .48f),
            new Rgb(.06f, .08f, .03f), .8f,
            new Rgb(.14f, .18f, .06f), .3f,
            new Rgb(.22f, .26f, .08f), new Rgb(.22f, .26f, .08f), -1, new Rgb(1f, 1f, 1f), 0f,
            new Rgb(.40f, .38f, .24f), new Rgb(.72f, .80f, .36f), .14f);

    /** Cells back from the drop over which the lip gives way to the plain. */
    static final float PLAIN_CELLS = .3f;
    /** Sediment on a ledge: cells back from the break where it starts, and where it lies full. */
    static final float DRIFT_FROM = .1f, DRIFT_FULL = .8f;
    /** Cells in from the walls' feet over which the floor's talus gives way to its bed. */
    static final float TALUS_CELLS = 1.3f;

    /** The palette of a drop's style, or null for a drop that is not dry. */
    static Palette of(NaturalVoid look) {
        if (look == null) return null;
        return switch (look.style()) {
            case CANYON -> CANYON;
            case DRY_GORGE -> REGOLITH;
            case TOXIC -> TOXIC;
            default -> null;
        };
    }

    /** How far the lip at {@code edge} cells back from the drop has given way to the plain, 0 to 1. */
    static float plain(float edge) {
        return smooth(0f, PLAIN_CELLS, edge);
    }

    /** How much sediment lies on a ledge {@code cellsBack} cells back from where it breaks over the face, 0 to 1. */
    static float drift(float cellsBack) {
        return smooth(DRIFT_FROM, DRIFT_FULL, cellsBack);
    }

    /** How much of the floor {@code in} cells in from the walls' feet is talus fallen from them, 0 to 1. */
    static float talus(float in) {
        return 1f - smooth(.2f, TALUS_CELLS, in);
    }

    /**
     * A lip or ledge piece: hard rock at the lip's edge going over to the plain, the strata on the ledges under
     * drifted sediment, scree at the break, falling toward the palette's shade where the field left it dark.
     *
     * @param n1    slow noise at the piece, about -1..1
     * @param n2    fine noise at the piece, about -1..1
     * @param k     the band: 0 the lip, then each ledge down
     * @param light the sun and sky the field gave the piece
     * @param edge  for a ledge, cells to the face below it; for the lip, cells back from the first face
     */
    static void rock(Palette p, int k, float n1, float n2, float light, float edge, float[] rgb) {
        float r, g, b;
        if (k == 0) {
            float hard = clamp01(.5f + n1 * .8f);
            r = lerp(p.cap.r, p.upper.r, hard * .35f);
            g = lerp(p.cap.g, p.upper.g, hard * .35f);
            b = lerp(p.cap.b, p.upper.b, hard * .35f);
            float plain = plain(edge);
            r = lerp(r, p.plain.r, plain); g = lerp(g, p.plain.g, plain); b = lerp(b, p.plain.b, plain);
            n2 *= 1f - plain;
            // Back from the drop the lip is the plain, which the battle's own sun lights as it lights the tiles.
            light = lerp(light, 1f, plain);
        } else {
            // Ledge by ledge the strata change, and wander within each ledge.
            float band = clamp01(.5f + n1 * 1.1f + (k % 2 == 0 ? .3f : -.3f));
            r = lerp(p.upper.r, p.lower.r, band);
            g = lerp(p.upper.g, p.lower.g, band);
            b = lerp(p.upper.b, p.lower.b, band);
            float drift = clamp01(drift(edge) * (.75f + .45f * n1)) * .8f;
            r = lerp(r, p.drift.r, drift); g = lerp(g, p.drift.g, drift); b = lerp(b, p.drift.b, drift);
            float scree = (1f - smooth(0f, .45f, edge)) * clamp01(.6f + n2);
            r = lerp(r, p.scree.r, scree * .45f); g = lerp(g, p.scree.g, scree * .45f);
            b = lerp(b, p.scree.b, scree * .45f);
        }
        lit(p, r, g, b, n2, light, rgb);
    }

    /**
     * A lip, ledge or fray piece of a dry drop's {@code band} ({@code k == 0} the lip), from the piece's own place,
     * light and edge, into {@code into} at the piece's slot.
     */
    static void rock(Palette p, NaturalVoidField.Band band, int q, int k, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1];
        float n1 = NaturalVoidField.Noise.fbm(x * .45f, y * .45f, seed * 7 + 11);
        float n2 = NaturalVoidField.Noise.value(x * 3.1f, y * 3.1f, seed * 7 + 13);
        float[] rgb = new float[3];
        rock(p, k, n1, n2, a[o + 2], a[o + 3], rgb);
        System.arraycopy(rgb, 0, into, q * 3, 3);
    }

    /**
     * The floor: talus fallen from the walls along their feet, then the bed with its channels running along the
     * drop between coarser bars, lit by the strip of sky over it. Stones are drawn on it, not into it.
     *
     * @param in      cells in from the walls' feet
     * @param channel how much a channel runs here, 0 to 1
     * @param mottle  slow noise, about -1..1, for the bars
     * @param grain   fine noise, about -1..1
     * @param sky     how much sky the walls leave open over the piece
     * @param light   the sun and sky the field gave the piece
     */
    static void floor(Palette p, float in, float channel, float mottle, float grain,
                      float sky, float light, float[] rgb) {
        float bar = clamp01(.5f + mottle * 1.3f) * (1f - channel);
        float r = lerp(p.bed.r, p.bar.r, bar), g = lerp(p.bed.g, p.bar.g, bar), b = lerp(p.bed.b, p.bar.b, bar);
        float talus = talus(in);
        r = lerp(r, p.scree.r * .9f, talus * .7f); g = lerp(g, p.scree.g * .9f, talus * .7f);
        b = lerp(b, p.scree.b * .9f, talus * .7f);
        float fine = .96f + .06f * (grain * .5f + .5f) * (1f - channel * .6f);
        r *= fine; g *= fine; b *= fine;
        float bright = .7f + .3f * sky + .06f * Math.min(1f, light);
        rgb[0] = lerp(p.shade.r, r, bright);
        rgb[1] = lerp(p.shade.g, g, bright);
        rgb[2] = lerp(p.shade.b, b, bright);
    }

    /** A colour under the field's light: toward the palette's shade where it is dark, a touch over where it is bright. */
    private static void lit(Palette p, float r, float g, float b, float n2, float light, float[] rgb) {
        float grain = 1f + .09f * n2;
        float lit = clamp01(light), over = Math.max(0f, Math.min(1.12f, light) - 1f);
        rgb[0] = lerp(p.shade.r, r, lit) * grain * (1f + over);
        rgb[1] = lerp(p.shade.g, g, lit) * grain * (1f + over);
        rgb[2] = lerp(p.shade.b, b, lit) * grain * (1f + over);
    }

    static float smooth(float from, float to, float v) {
        float t = clamp01((v - from) / (to - from));
        return t * t * (3f - 2f * t);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
