package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

/**
 * A toxic gorge's floor (`voids.md`, natural styles), drawn in the natural drop's painter's order in place of its
 * water: acid sludge creeping down the gorge along the channel's own flow ({@link ChannelFlow}), far slower than water,
 * scum drifting on it, crusted sulphur along the walls' feet where it has dried, bubbles swelling and bursting on it;
 * then the gorge's fumes lying over it. The walls and ledges are {@link DryVoidLook#TOXIC}'s.
 */
final class ToxicVoidFloor {

    /** The sludge: how fast it creeps, metres a second at the channel's own speed, and how long one pattern lasts. */
    static final float CREEP = .35f, PERIOD = 9f;
    /** Cells in from the walls' feet over which dried crust gives way to sludge. */
    static final float CRUST_CELLS = 1.1f;
    /** The sludge's own colour, its scum's, and the dried crust's along the walls. */
    static final DryVoidLook.Rgb SLUDGE = new DryVoidLook.Rgb(.22f, .26f, .08f),
            SCUM = new DryVoidLook.Rgb(.58f, .62f, .26f), CRUST = new DryVoidLook.Rgb(.68f, .58f, .22f);
    /** Bubbles: the grid they rise on, in cells, the share of its points that bubble, and how long one lives. */
    static final float BUBBLE_SPACING = 1.4f, BUBBLE_SHARE = .35f, BUBBLE_LIFE = 2.6f;
    /** The fumes: a low deck over the sludge and a thinner one rising above it, metres up, and their thickness. */
    static final float FUME_LOW = 3f, FUME_HIGH = 11f, FUME_PUFF = 3.4f;

    private final NaturalVoidRenderSystem drop;
    private final BattleSprites sprites;
    private NaturalVoidField builtFrom;
    private boolean[] overFloor;
    private final float[] rgb = new float[3];

    ToxicVoidFloor(NaturalVoidRenderSystem drop, BattleSprites sprites) {
        this.drop = drop;
        this.sprites = sprites;
    }

    /** How much of the floor {@code in} cells in from the walls' feet is dried crust rather than sludge, 0 to 1. */
    static float crust(float in) {
        return 1f - DryVoidLook.smooth(.3f, CRUST_CELLS, in);
    }

    /**
     * The floor at a point: sludge with scum where the creeping pattern runs high, crust along the walls, a bright
     * wet rim where the sludge meets it; lit by the strip of sky over it, the sludge never quite dark.
     *
     * @param in     cells in from the walls' feet
     * @param streak the creeping pattern, about -1..1
     * @param sky    how much sky the walls leave open over the piece
     */
    static void sludge(float in, float streak, float sky, float[] rgb) {
        float scum = DryVoidLook.smooth(-.1f, .7f, streak) * .45f;
        float r = DryVoidLook.lerp(SLUDGE.r(), SCUM.r(), scum), g = DryVoidLook.lerp(SLUDGE.g(), SCUM.g(), scum);
        float b = DryVoidLook.lerp(SLUDGE.b(), SCUM.b(), scum);
        // The wet rim, where the sludge laps at the crust.
        float c = crust(in), rim = c * (1f - c) * 4f;
        r += .10f * rim; g += .16f * rim; b += .02f * rim;
        r = DryVoidLook.lerp(r, CRUST.r(), c); g = DryVoidLook.lerp(g, CRUST.g(), c);
        b = DryVoidLook.lerp(b, CRUST.b(), c);
        float bright = .65f + .35f * sky;
        rgb[0] = r * bright;
        rgb[1] = g * bright;
        rgb[2] = b * bright;
    }

    /** Whether a bubble rises at a grid point with hash {@code hash} on a floor {@code in} cells from its walls. */
    static boolean bubbleAt(int hash, float in) {
        return (hash & 1023) / 1023f < BUBBLE_SHARE && crust(in) < .05f;
    }

    void draw(DryVoidLook.Palette p, NaturalVoidField.Band band, float time) {
        NaturalVoidField field = drop.field();
        if (builtFrom != field) {
            builtFrom = field;
            overFloor = VoidHaze.overFloor(field);
        }
        BattleCamera cam = drop.camera();
        float alpha = drop.alpha();
        float margin = cam.cellPxSize() * 2f;
        int seed = drop.seed();
        float p1 = frac(time / PERIOD), p2 = frac(time / PERIOD + .5f);
        float w1 = 1f - Math.abs(2f * p1 - 1f);
        PolyMesh mesh = drop.poly();
        float[] qd = band.quads, at = band.attrs, fl = band.flow;
        float floorDepth = Math.max(1f, band.depth);
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            float zc = band.z(q, 4);
            if (!drop.inView(at[o], at[o + 1], drop.scale(zc), margin)) continue;
            float x = at[o], y = at[o + 1], sky = at[o + 6];
            float in = field.sample(field.reach[field.steps()], x, y);
            float streak;
            if (fl != null) {
                float along = fl[q * 5], across = fl[q * 5 + 1], speed = Math.min(2f, fl[q * 5 + 4]);
                float run = CREEP * PERIOD * speed;
                float s1 = NaturalVoidField.Noise.fbm((along - p1 * run) * .22f, across * .9f, seed + 91);
                float s2 = NaturalVoidField.Noise.fbm((along - p2 * run) * .22f + 17f, across * .9f, seed + 93);
                streak = w1 * s1 + (1f - w1) * s2;
            } else {
                streak = NaturalVoidField.Noise.fbm(x * .25f, y * .25f, seed + 91);
            }
            sludge(in, streak, sky, rgb);
            float tint = p.deepMix() * Math.min(1f, zc / floorDepth) * .5f;
            float r = DryVoidLook.lerp(rgb[0], p.deep().r(), tint);
            float g = DryVoidLook.lerp(rgb[1], p.deep().g(), tint);
            float b = DryVoidLook.lerp(rgb[2], p.deep().b(), tint);
            float s0 = drop.scale(band.z(q, 0)), s1 = drop.scale(band.z(q, 1));
            float s2 = drop.scale(band.z(q, 2)), s3 = drop.scale(band.z(q, 3));
            mesh.appendQuad(drop.sx(qd[o], s0), drop.sy(qd[o + 1], s0), drop.sx(qd[o + 2], s1), drop.sy(qd[o + 3], s1),
                    drop.sx(qd[o + 4], s2), drop.sy(qd[o + 5], s2), drop.sx(qd[o + 6], s3), drop.sy(qd[o + 7], s3),
                    r, g, b, alpha);
        }
        if (!mesh.isEmpty()) drop.drawList().addPoly(RenderLayer.GROUND, mesh);
        bubbles(field, time);
        DryVoidLook.Rgb haze = p.haze();
        VoidHaze.draw(drop, sprites, overFloor, FUME_LOW, FUME_PUFF, p.hazeAlpha(), .87f, .49f, .15f,
                haze.r(), haze.g(), haze.b(), time);
        VoidHaze.draw(drop, sprites, overFloor, FUME_HIGH, FUME_PUFF * 1.4f, p.hazeAlpha() * .5f, -.49f, .87f, .1f,
                haze.r(), haze.g(), haze.b(), time + 37f);
    }

    /** Bubbles on the sludge in view: each swells, bursts, and rises again a while later. */
    private void bubbles(NaturalVoidField field, float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(GlacialVoidRenderSystem.DOT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        float cellPx = cam.cellPxSize();
        int n = field.steps();
        float s = drop.scale(field.depth[n] * field.mostGain);
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + 2f, halfY = cam.vpH() / cellPx / s * .5f + 2f;
        int i0 = (int) Math.floor((ccx - half) / BUBBLE_SPACING), i1 = (int) Math.ceil((ccx + half) / BUBBLE_SPACING);
        int j0 = (int) Math.floor((ccy - halfY) / BUBBLE_SPACING), j1 = (int) Math.ceil((ccy + halfY) / BUBBLE_SPACING);
        if ((long) (i1 - i0) * (j1 - j0) > 30000) return;
        int seed = drop.seed() * 13 + 5;
        float alpha = drop.alpha();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = VoidHaze.hash(i, j, seed);
                float x = (i + .1f + .8f * ((hsh >>> 10) & 255) / 255f) * BUBBLE_SPACING;
                float y = (j + .1f + .8f * ((hsh >>> 18) & 255) / 255f) * BUBBLE_SPACING;
                int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
                if (ix < 0 || iy < 0 || ix >= field.w || iy >= field.h || !overFloor[iy * field.w + ix]) continue;
                if (!bubbleAt(hsh, field.sample(field.reach[n], x, y))) continue;
                // Each bubble's own cycle: it swells for most of it and bursts at the end, then rests.
                float cycle = BUBBLE_LIFE * (1.5f + ((hsh >>> 26) & 63) / 63f);
                float t = frac(time / cycle + ((hsh >>> 4) & 63) / 63f) * cycle / BUBBLE_LIFE;
                if (t >= 1f) continue;
                float z = field.stepDepth(n, x, y);
                float zs = drop.scale(z);
                if (!drop.inView(x, y, zs, cellPx * 2f)) continue;
                float size = (.2f + .45f * t) * cellPx * zs * (.7f + .6f * ((hsh >>> 2) & 3) / 3f);
                float a = (t < .85f ? .7f : .7f * (1f - (t - .85f) / .15f)) * alpha;
                drop.drawList().addSheetQuad(RenderLayer.GROUND, dot.sprite, 0, 0, dot.pxW, dot.pxH,
                        drop.sx(x, zs), drop.sy(y, zs), size, size, 0f, .62f, .70f, .30f, a);
            }
        }
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
