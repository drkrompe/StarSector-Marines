package com.dillon.starsectormarines.ops.battleview;

/**
 * The rectangular strip light seen from above: a steel housing with a bezel, a
 * recessed diffuser lens that carries the lamp's colour, a hotter core down the
 * middle of the lens, and a cap at each end. Drawn as flat rectangles so it
 * stays crisp at any zoom; the glow it throws is the caller's.
 */
final class StripFixtureArt {
    private static final float LENGTH = .8f, THICKNESS = .2f;

    private StripFixtureArt() {}

    /**
     * @param gain how lit the lens is, 0 (dead glass) to 1 (full)
     */
    static void draw(DrawList out, RenderLayer layer, float cx, float cy, float px, boolean vertical,
                     float red, float green, float blue, float gain, float alpha) {
        float half = LENGTH * px * .5f, thick = Math.max(3f, THICKNESS * px) * .5f;
        float bezel = Math.max(1f, px * .03f), lens = bezel + Math.max(1f, px * .025f);
        // Housing and bezel.
        rect(out, layer, cx, cy, half, thick, vertical, .06f, .07f, .08f, alpha);
        rect(out, layer, cx, cy, half - bezel, thick - bezel, vertical, .24f, .26f, .27f, alpha);
        // Lens: dull glass when dark, the lamp's own colour when lit.
        float glow = .12f + .88f * gain;
        rect(out, layer, cx, cy, half - lens, thick - lens, vertical,
                .1f + (red * .9f - .1f) * glow, .11f + (green * .9f - .11f) * glow,
                .12f + (blue * .9f - .12f) * glow, alpha);
        // Hot core: the tube behind the diffuser.
        float white = .55f * gain;
        rect(out, layer, cx, cy, (half - lens) * .72f, Math.max(.5f, (thick - lens) * .42f), vertical,
                red + (1f - red) * white, green + (1f - green) * white, blue + (1f - blue) * white,
                alpha * (.25f + .75f * gain));
        // End caps, proud of the housing.
        float cap = Math.max(1.5f, px * .05f);
        for (int side = -1; side <= 1; side += 2) {
            float ox = vertical ? 0f : side * (half + cap * .3f), oy = vertical ? side * (half + cap * .3f) : 0f;
            rect(out, layer, cx + ox, cy + oy, vertical ? thick + bezel : cap, vertical ? cap : thick + bezel,
                    false, .14f, .15f, .16f, alpha);
        }
    }

    /** A rectangle about its centre, {@code along} half-length on the fixture's axis and {@code across} half-width. */
    private static void rect(DrawList out, RenderLayer layer, float cx, float cy, float along, float across,
                             boolean vertical, float r, float g, float b, float a) {
        if (along <= 0f || across <= 0f) return;
        float hx = vertical ? across : along, hy = vertical ? along : across;
        out.addSolidRect(layer, cx - hx, cy - hy, cx + hx, cy + hy, r, g, b, a);
    }
}
