package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * A dry drop's floor (`voids.md`, natural styles), drawn in the natural drop's painter's order in place of its water:
 * talus along the walls' feet, a bed of fine stuff with channels braided along the drop between coarser bars, the
 * plain's own field stones fallen from the walls lying on it, each casting a short shadow away from the sun; then the
 * palette's haze lying low over it. Colour rules are {@link DryVoidLook}'s.
 */
final class DryVoidFloor {

    /** The haze over the floor: metres above it, puff size in cells, its drift along the drop, cells a second. */
    static final float HAZE_METERS = 4f, HAZE_PUFF = 3.6f, HAZE_DRIFT = .25f;
    /** Stones: the grid they are scattered on, in cells, and the share of its points that hold one. */
    static final float STONE_SPACING = 2.2f, STONE_SHARE = .3f;
    /** A stone's size in cells, smallest to largest, and how far its shadow falls from it, in its own sizes. */
    static final float STONE_MIN = .9f, STONE_MAX = 2.1f, STONE_SHADOW = .18f;
    /** The field stones in a set: two medium then three large, from the set's first frame. */
    static final int STONE_FRAMES = 5;

    private final NaturalVoidRenderSystem drop;
    private final BattleSprites sprites;
    private NaturalVoidField builtFrom;
    private boolean[] overFloor;
    private final float[] rgb = new float[3];

    DryVoidFloor(NaturalVoidRenderSystem drop, BattleSprites sprites) {
        this.drop = drop;
        this.sprites = sprites;
    }

    /**
     * Whether a stone lies at grid point {@code (i, j)} of a floor {@code in} cells from its walls and {@code channel}
     * in a channel: never in a channel, nor in the talus against a wall, where the fall would have buried it.
     */
    static boolean stoneAt(int hash, float in, float channel) {
        return (hash & 1023) / 1023f < STONE_SHARE && in > 1f && channel < .3f;
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
        PolyMesh mesh = drop.poly();
        float[] qd = band.quads, at = band.attrs;
        float floorDepth = Math.max(1f, band.depth);
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            float zc = band.z(q, 4);
            if (!drop.inView(at[o], at[o + 1], drop.scale(zc), margin)) continue;
            float x = at[o], y = at[o + 1], light = at[o + 2];
            float sky = at[o + 6];
            // Cells in from the walls, from the smooth field: the piece's own figure steps and would speckle.
            float in = field.sample(field.reach[field.steps()], x, y);
            float channel = channel(x, y, in, seed);
            float mottle = NaturalVoidField.Noise.fbm(x * .3f, y * .3f, seed + 87);
            float grain = NaturalVoidField.Noise.value(x * 2.6f, y * 2.6f, seed + 89);
            DryVoidLook.floor(p, in, channel, mottle, grain, sky, light, rgb);
            float tint = p.deepMix() * Math.min(1f, zc / floorDepth);
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
        if (p.stones() >= 0) stones(p, field, floorDepth);
        DryVoidLook.Rgb haze = p.haze();
        VoidHaze.draw(drop, sprites, overFloor, HAZE_METERS, HAZE_PUFF, p.hazeAlpha(), .87f, .49f, HAZE_DRIFT,
                haze.r(), haze.g(), haze.b(), time);
    }

    /**
     * How much a channel of the bed runs at a point, 0 to 1: two meandering threads crossing and parting, away from the
     * walls. From the point alone: the floor pieces' own flow bearings step between pieces and would stair-step them.
     */
    static float channel(float x, float y, float in, int seed) {
        float braid = Math.min(Math.abs(NaturalVoidField.Noise.fbm(x * .07f, y * .07f, seed + 83)),
                Math.abs(NaturalVoidField.Noise.fbm(x * .09f + 31f, y * .09f, seed + 85)) + .04f);
        return (1f - DryVoidLook.smooth(.03f, .2f, braid)) * DryVoidLook.smooth(1.2f, 2.6f, in);
    }

    /** The field stones on the floor in view, each at its depth's scale, its shadow first. */
    private void stones(DryVoidLook.Palette p, NaturalVoidField field, float floorDepth) {
        SpriteAPI sheet = sprites.natureSheet();
        SpriteSheetFrames frames = sprites.natureFrames();
        ShuttleSpriteCache dot = sprites.spriteByPath(GlacialVoidRenderSystem.DOT_SPRITE);
        if (sheet == null || frames == null || frames.frames.length < p.stones() + STONE_FRAMES) return;
        BattleCamera cam = drop.camera();
        float cellPx = cam.cellPxSize();
        int n = field.steps();
        float s = drop.scale(field.depth[n] * field.mostGain);
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + 3f, halfY = cam.vpH() / cellPx / s * .5f + 3f;
        int i0 = (int) Math.floor((ccx - half) / STONE_SPACING), i1 = (int) Math.ceil((ccx + half) / STONE_SPACING);
        int j0 = (int) Math.floor((ccy - halfY) / STONE_SPACING), j1 = (int) Math.ceil((ccy + halfY) / STONE_SPACING);
        if ((long) (i1 - i0) * (j1 - j0) > 20000) return;
        SunLight sun = drop.sun();
        // Away from the sun across the floor, where a stone's shadow falls.
        float ax = sun == null ? .7f : -sun.dirX(), ay = sun == null ? .7f : -sun.dirY();
        float al = (float) Math.hypot(ax, ay);
        if (al > 1e-4f) { ax /= al; ay /= al; }
        float lit = sun == null ? .9f : .8f + .2f * Math.min(1f, sun.risePerCell());
        int seed = drop.seed() * 31 + 7;
        float alpha = drop.alpha();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = VoidHaze.hash(i, j, seed);
                float x = (i + .15f + .7f * ((hsh >>> 10) & 255) / 255f) * STONE_SPACING;
                float y = (j + .15f + .7f * ((hsh >>> 18) & 255) / 255f) * STONE_SPACING;
                int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
                if (ix < 0 || iy < 0 || ix >= field.w || iy >= field.h || !overFloor[iy * field.w + ix]) continue;
                float in = field.sample(field.reach[n], x, y);
                if (!stoneAt(hsh, in, channel(x, y, in, drop.seed()))) continue;
                if (field.nearBridge(x, y, 1.5f)) continue;
                float z = field.stepDepth(n, x, y);
                float zs = drop.scale(z);
                if (!drop.inView(x, y, zs, cellPx * 4f)) continue;
                float big = ((hsh >>> 26) & 63) / 63f;
                float size = (STONE_MIN + (STONE_MAX - STONE_MIN) * big * big) * cellPx * zs;
                SpriteSheetFrames.Frame f = frames.frames[p.stones() + (big > .45f ? 2 + (hsh & 3) % 3 : hsh & 1)];
                float w = size, h = size * f.h / (float) Math.max(1, f.w);
                float px = drop.sx(x, zs), py = drop.sy(y, zs);
                float tint = p.deepMix() * Math.min(1f, z / floorDepth);
                float shade = lit * (1f - tint);
                if (dot != null) {
                    float off = STONE_SHADOW * size / (cellPx * zs);
                    drop.drawList().addSheetQuad(RenderLayer.GROUND, dot.sprite, 0, 0, dot.pxW, dot.pxH,
                            drop.sx(x + ax * off, zs), drop.sy(y + ay * off, zs), size * 1.1f, size * .85f, 0f,
                            .05f, .03f, .03f, .45f * alpha);
                }
                // Upright, as the plain's own stones lie: their light is drawn into them.
                DryVoidLook.Rgb t = p.stoneTint();
                drop.drawList().addSheetQuad(RenderLayer.GROUND, sheet, f.x, f.y, f.w, f.h, px, py, w, h, 0f,
                        shade * t.r(), shade * t.g(), shade * t.b(), alpha);
            }
        }
    }
}
