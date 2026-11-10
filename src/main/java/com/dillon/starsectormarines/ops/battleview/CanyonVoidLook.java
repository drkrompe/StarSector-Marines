package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

/**
 * The canyon's palette ({@code voids.md}, canyon): the gorge river's dry sister on barren and desert worlds. A dusty
 * lip of broken rock, bare ledges with scree where they break, and a dry wash on the floor: pale sand down the
 * middle, darker gravel and fallen boulders toward the walls. GL-free; {@link NaturalVoidRenderSystem} draws with it.
 */
final class CanyonVoidLook {

    /** The depth tint: a warm dusk rather than the gorge's blue water-shade. */
    static final float DEEP_R = .20f, DEEP_G = .16f, DEEP_B = .14f;

    private CanyonVoidLook() { }

    static boolean canyon(NaturalVoid look) {
        return look != null && look.style() == NaturalVoid.Style.CANYON;
    }

    /** A lip or ledge piece's colour: dusty broken rock, scree where the ledge breaks over the face below. */
    static void rock(NaturalVoidField.Band band, int q, int k, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1], light = a[o + 2], edge = a[o + 3];
        float n1 = NaturalVoidField.Noise.fbm(x * .45f, y * .45f, seed * 7 + 51);
        float n2 = NaturalVoidField.Noise.value(x * 3.1f, y * 3.1f, seed * 7 + 53);
        float iron = clamp01(.5f + n1 * 1.3f);
        float r = lerp(.47f, .50f, iron), g = lerp(.45f, .40f, iron), b = lerp(.41f, .32f, iron);
        if (k > 0) {
            float scree = clamp01(1f - edge * 2.2f) * clamp01(.6f + n2);
            r = lerp(r, .62f, scree * .5f); g = lerp(g, .58f, scree * .5f); b = lerp(b, .52f, scree * .5f);
        }
        float grain = .86f + .28f * (n2 * .5f + .5f);
        float lit = Math.min(1.15f, light);
        into[q * 3] = r * grain * lit;
        into[q * 3 + 1] = g * grain * lit;
        into[q * 3 + 2] = b * grain * lit;
    }

    /** The floor: a dry wash, pale sand where the floor lies farthest from its walls, gravel and boulders by them. */
    static float[] floor(float x, float y, float light, float edge, int seed) {
        float n = NaturalVoidField.Noise.fbm(x * .16f, y * .16f, seed * 7 + 57);
        float grain = NaturalVoidField.Noise.value(x * 2.6f, y * 2.6f, seed * 7 + 59) * .5f + .5f;
        float boulder = clamp01((NaturalVoidField.Noise.value(x * .9f, y * .9f, seed * 7 + 61) - .55f) * 5f);
        float sand = clamp01((edge - .9f) * .55f + n * .8f);
        float bright = .62f + .45f * light;
        float r = lerp(.38f, .62f, sand), g = lerp(.35f, .55f, sand), b = lerp(.31f, .43f, sand);
        float rough = .84f + .32f * grain;
        r *= rough; g *= rough; b *= rough;
        // Fallen blocks, thickest away from the wash's middle.
        float block = boulder * (1f - sand * .7f);
        r = lerp(r, .30f, block * .8f); g = lerp(g, .28f, block * .8f); b = lerp(b, .26f, block * .8f);
        return new float[]{r * bright, g * bright, b * bright};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
