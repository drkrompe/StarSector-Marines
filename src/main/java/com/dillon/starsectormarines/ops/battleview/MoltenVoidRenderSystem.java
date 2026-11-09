package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.Arrays;

/**
 * The lava rift's own drawing (`voids.md`, natural styles): what a molten floor adds to the natural drop that
 * {@link NaturalVoidRenderSystem} draws. Its shape, walls and ledges are the drop's; this is the light and the
 * air. Colour rules are {@link LavaVoidLook}'s.
 *
 * <ul>
 *   <li><b>The floor</b> ({@link #drawFloor}), in the drop's painter's order in place of its water: the melt, its
 *       heat drifting downstream and incandescent along the walls; the crust floating on it, a seamless image
 *       laid twice and fading one into the other as it drifts, thinning to nothing where it breaks against the
 *       walls; a bloom of light over it; and a low smoke lit orange from below. Floor and crust ripple a little
 *       in the heat.</li>
 *   <li><b>Over the ground</b> ({@link #collect}), after the tiles and the lip's fray: the ground beside the lip
 *       lit orange and flickering; embers rising out of the rift on the wind, each hidden where the rock stands
 *       between it and the eye; smoke boiling up out of the rift and drifting off; ash falling over all of it.</li>
 * </ul>
 *
 * <p>Presentation only, in the ground layer, so units draw over all of it and nothing here hides one.
 */
final class MoltenVoidRenderSystem implements RenderSystem {

    /** The crust floating on the melt, seen straight down: a seamless image, and the cells one copy covers. */
    static final String CRUST_TEXTURE = "graphics/battle/voids/lava-crust.png";
    static final float CRUST_TILE_CELLS = 22f;
    /** How fast the crust drifts, cells a second, and how long one layer drifts before it fades into the other. */
    static final float CRUST_SPEED = .22f, CRUST_PERIOD = 14f;
    /** How fast the heat under the crust runs, cells a second. */
    static final float MELT_SPEED = .5f;
    /** How far the floor ripples in the heat, in cells. */
    static final float SHIMMER_CELLS = .045f;
    /** A soft round light, white, as the city drop's lamps. */
    static final String LIGHT_SPRITE = VoidBackdropRenderSystem.LIGHT_SPRITE;
    /** Vanilla's fog sheet, four puffs across and down, as the city drop's fog. */
    static final String SMOKE_SHEET = "graphics/fx/nebula_colorless.png";
    static final int SMOKE_SHEET_CELLS = 4;
    /** The wind over the rift: its bearing and how fast it carries smoke, embers and ash, cells a second. */
    static final float WIND_X = .94f, WIND_Y = .34f, WIND_SPEED = 1.3f;
    /** Embers: one in this many floor cells sends them up, how fast they climb, metres a second, and how high. */
    static final int EMBER_EVERY = 3;
    static final float EMBER_RISE = 5f, EMBER_ABOVE_METERS = 14f;
    /** Smoke out of the rift: the heights it is drawn at, metres above the surface, and how thick. */
    static final float[] SMOKE_ABOVE = {3f, 10f};
    static final float SMOKE_ALPHA = .22f;
    /** How far downwind of the rift smoke above it is carried before it thins away, in cells. */
    static final float SMOKE_LAG_CELLS = 2.5f;
    /** Ash: the spacing of falling flakes, in cells. */
    static final float ASH_SPACING = 1.7f;

    private final NaturalVoidRenderSystem drop;
    private final BattleSprites sprites;

    private NaturalVoidField builtFrom;
    /** Floor cells: {@code {x, y, crust cover, flowX, flowY}} in the field's cells. */
    private float[] floor = new float[0];
    private int floorCount;
    /** Field cells over the floor, for the smoke's footing. */
    private boolean[] overFloor;
    /** The ground's light beside the lip: {@code {x0, y0, x1, y1, strength}} in the field's cells. */
    private float[] lip = new float[0];
    private int lipCount;

    private final float[] rgb = new float[3];

    MoltenVoidRenderSystem(NaturalVoidRenderSystem drop, BattleSprites sprites) {
        this.drop = drop;
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    static boolean molten(NaturalVoid look) {
        return look != null && look.style() == NaturalVoid.Style.LAVA;
    }

    // ----- shape -----

    private void build(NaturalVoidField field) {
        builtFrom = field;
        int w = field.w, h = field.h, n = field.steps();
        float floorMeters = field.depth[n];
        floorCount = 0;
        lipCount = 0;
        overFloor = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float cx = x + .5f, cy = y + .5f;
                // Under a bridge too: the melt runs on beneath the deck and shows past its edge, scaled toward the eye.
                if (field.open[i]) {
                    float in = field.sample(field.reach[n], cx, cy);
                    if (in <= 0f) continue;
                    overFloor[i] = true;
                    float least = Math.min(Math.min(field.sample(field.reach[n], x, y),
                                    field.sample(field.reach[n], x + 1f, y)),
                            Math.min(field.sample(field.reach[n], x, y + 1f),
                                    field.sample(field.reach[n], x + 1f, y + 1f)));
                    float most = Math.max(in, least);
                    float cover = most > LavaVoidLook.CRUST_EDGE_CELLS - .7f ? 1f : 0f;
                    float gx = field.sample(field.edge, cx + .5f, cy) - field.sample(field.edge, cx - .5f, cy);
                    float gy = field.sample(field.edge, cx, cy + .5f) - field.sample(field.edge, cx, cy - .5f);
                    float len = (float) Math.hypot(gx, gy);
                    float fx = len > 1e-4f ? -gy / len : 1f, fy = len > 1e-4f ? gx / len : 0f;
                    if (Math.abs(fx) >= Math.abs(fy) ? fx < 0 : fy < 0) { fx = -fx; fy = -fy; }
                    floor = grow(floor, floorCount * 5 + 5);
                    int o = floorCount++ * 5;
                    floor[o] = x; floor[o + 1] = y; floor[o + 2] = cover; floor[o + 3] = fx; floor[o + 4] = fy;
                } else {
                    // A pool of light on each cell of ground at the lip; the pools overlap into one band.
                    float out = -field.sample(field.edge, cx, cy);
                    if (out < 0f || out > LIP_POOL_ROW_CELLS) continue;
                    // A bridge's end is road onto the deck, not the lip: no pool of the melt's light there.
                    if (field.byBridge(cx, cy)) continue;
                    lip = grow(lip, lipCount * 3 + 3);
                    int o = lipCount++ * 3;
                    lip[o] = cx; lip[o + 1] = cy; lip[o + 2] = LavaVoidLook.lipGlow(out, floorMeters);
                }
            }
        }
    }

    private static float[] grow(float[] a, int need) {
        return need <= a.length ? a : Arrays.copyOf(a, Math.max(need, a.length * 2 + 64));
    }

    // ----- the floor, in the drop's painter's order -----

    /** The molten floor at its depth, in place of the drop's own floor. */
    void drawFloor(NaturalVoidField.Band band, float time) {
        NaturalVoidField field = drop.field();
        if (builtFrom != field) build(field);
        BattleCamera cam = drop.camera();
        DrawList out = drop.drawList();
        float alpha = drop.alpha();
        float s = drop.scale(band.depth);
        float px = cam.cellPxSize() * s;
        float margin = cam.cellPxSize() * 2f;
        int seed = drop.seed();
        // The melt.
        PolyMesh mesh = drop.poly();
        float[] qd = band.quads, at = band.attrs;
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            if (!drop.inView(at[o], at[o + 1], s, margin)) continue;
            float x = at[o], y = at[o + 1], in = at[o + 3], fx = at[o + 4], fy = at[o + 5];
            float along = x * fx + y * fy, across = -x * fy + y * fx;
            float run = NaturalVoidField.Noise.fbm((along - time * MELT_SPEED) * .22f, across * .55f, seed + 21);
            float pulse = NaturalVoidField.Noise.value(x * .15f, y * .15f + time * .12f, seed + 23);
            float heat = .42f + .3f * run + .14f * pulse + .4f * (1f - LavaVoidLook.crustCover(in));
            LavaVoidLook.molten(heat, rgb);
            mesh.appendQuad(wx(qd[o], qd[o + 1], s, px, time), wy(qd[o], qd[o + 1], s, px, time),
                    wx(qd[o + 2], qd[o + 3], s, px, time), wy(qd[o + 2], qd[o + 3], s, px, time),
                    wx(qd[o + 4], qd[o + 5], s, px, time), wy(qd[o + 4], qd[o + 5], s, px, time),
                    wx(qd[o + 6], qd[o + 7], s, px, time), wy(qd[o + 6], qd[o + 7], s, px, time),
                    rgb[0], rgb[1], rgb[2], alpha);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
        // The crust, two layers drifting downstream, each fading out as the other comes in.
        ShuttleSpriteCache crust = sprites.spriteByPath(CRUST_TEXTURE);
        if (crust != null && crust.pxW > 4) {
            float p1 = frac(time / CRUST_PERIOD), p2 = frac(time / CRUST_PERIOD + .5f);
            float w1 = 1f - Math.abs(2f * p1 - 1f);
            float pa = w1 >= .5f ? p1 : p2, pb = w1 >= .5f ? p2 : p1;
            float wb = Math.min(w1, 1f - w1);
            float shiftA = w1 >= .5f ? 0f : 9.3f, shiftB = w1 >= .5f ? 9.3f : 0f;
            for (int c = 0; c < floorCount; c++) {
                int o = c * 5;
                float x = floor[o], y = floor[o + 1], cover = floor[o + 2];
                if (cover <= 0f || !drop.inView(x + .5f, y + .5f, s, margin)) continue;
                float fx = floor[o + 3], fy = floor[o + 4];
                float run = CRUST_SPEED * CRUST_PERIOD;
                crustCell(crust, field, x, y, fx * run * pa + shiftA, fy * run * pa + shiftA * .6f, s, px, time,
                        alpha);
                if (wb > .01f) {
                    crustCell(crust, field, x, y, fx * run * pb + shiftB, fy * run * pb + shiftB * .6f, s, px,
                            time, wb * alpha);
                }
            }
        }
        // Light over the melt, and a low smoke lit from below it.
        ShuttleSpriteCache dot = sprites.spriteByPath(LIGHT_SPRITE);
        if (dot != null) {
            for (int c = 0; c < floorCount; c++) {
                int o = c * 5;
                int x = (int) floor[o], y = (int) floor[o + 1];
                if (Math.floorMod(x, 4) != 0 || Math.floorMod(y, 4) != 0) continue;
                if (!drop.inView(x + .5f, y + .5f, s, margin * 4f)) continue;
                float flick = .75f + .25f * NaturalVoidField.Noise.value(x * .3f, y * .3f + time * .8f, seed + 29);
                float size = 11f * px;
                out.addAdditiveSprite(RenderLayer.GROUND, dot.sprite, drop.sx(x + .5f, s), drop.sy(y + .5f, s),
                        size, size, 0f, LavaVoidLook.GLOW_R, LavaVoidLook.GLOW_G, LavaVoidLook.GLOW_B,
                        .16f * flick * alpha);
            }
        }
        smoke(field, band.depth - 4f, 2.6f, .38f, .17f, .08f, .34f, true, time, 0);
    }

    /** One floor cell of crust, its image shifted by {@code (ox, oy)} cells, cut where the image repeats. */
    private void crustCell(ShuttleSpriteCache crust, NaturalVoidField field, float x, float y, float ox, float oy,
                           float s, float px, float time, float a) {
        int pw = crust.pxW, ph = crust.pxH;
        float perCellX = pw / CRUST_TILE_CELLS, perCellY = ph / CRUST_TILE_CELLS;
        float u = frac((x - ox) / CRUST_TILE_CELLS) * pw, v = frac((y - oy) / CRUST_TILE_CELLS) * ph;
        float splitX = u + perCellX > pw ? (pw - u) / perCellX : 1f;
        float splitY = v + perCellY > ph ? (ph - v) / perCellY : 1f;
        for (int j = 0; j < (splitY < 1f ? 2 : 1); j++) {
            float ya = j == 0 ? 0f : splitY, yb = j == 0 ? splitY : 1f;
            float sv = j == 0 ? v : 0f;
            for (int i = 0; i < (splitX < 1f ? 2 : 1); i++) {
                float xa = i == 0 ? 0f : splitX, xb = i == 0 ? splitX : 1f;
                float su = i == 0 ? u : 0f;
                int srcX = Math.min(pw - 1, Math.round(su)), srcY = Math.min(ph - 1, Math.round(sv));
                int srcW = Math.max(1, Math.min(pw - srcX, Math.round((xb - xa) * perCellX)));
                int srcH = Math.max(1, Math.min(ph - srcY, Math.round((yb - ya) * perCellY)));
                crustPiece(crust, field, x + xa, y + ya, x + xb, y + yb, srcX, srcY, srcW, srcH, s, px, time, a);
            }
        }
    }

    /**
     * One rectangle of a crust cell, within one repeat of the image: whole where the crust covers all of it, else
     * cut along the crust's line, each of its two triangles clipped to the side the crust is on.
     */
    private void crustPiece(ShuttleSpriteCache crust, NaturalVoidField field, float x0, float y0, float x1, float y1,
                            int srcX, int srcY, int srcW, int srcH, float s, float px, float time, float a) {
        float[] cx = {x0, x1, x1, x0}, cy = {y0, y0, y1, y1}, cr = new float[4];
        boolean all = true, none = true;
        for (int k = 0; k < 4; k++) {
            cr[k] = crustLine(field, cx[k], cy[k]);
            all &= cr[k] >= 0f;
            none &= cr[k] < 0f;
        }
        if (none) return;
        if (all) {
            crustPatch(crust, cx, cy, x0, y0, x1, y1, srcX, srcY, srcW, srcH, s, px, time, a);
            return;
        }
        float[] qx = new float[5], qy = new float[5];
        int[][] tris = {{0, 1, 2}, {0, 2, 3}};
        for (int[] t : tris) {
            int m = 0;
            for (int v = 0; v < 3; v++) {
                int i = t[v], j = t[(v + 1) % 3];
                if (cr[i] >= 0f) { qx[m] = cx[i]; qy[m] = cy[i]; m++; }
                if ((cr[i] >= 0f) != (cr[j] >= 0f)) {
                    float f = cr[i] / (cr[i] - cr[j]);
                    qx[m] = cx[i] + (cx[j] - cx[i]) * f;
                    qy[m] = cy[i] + (cy[j] - cy[i]) * f;
                    m++;
                }
            }
            if (m < 3) continue;
            if (m == 3) { qx[3] = qx[2]; qy[3] = qy[2]; }
            crustPatch(crust, qx, qy, x0, y0, x1, y1, srcX, srcY, srcW, srcH, s, px, time, a);
        }
    }

    /** Where the crust lies: positive where it covers the melt, its edge wandering a little off the walls' line. */
    private static float crustLine(NaturalVoidField field, float x, float y) {
        float in = field.sample(field.reach[field.steps()], x, y);
        return in - LavaVoidLook.CRUST_EDGE_CELLS - .55f * NaturalVoidField.Noise.fbm(x * .45f, y * .45f, 41);
    }

    /**
     * A convex piece of four corners {@code (qx, qy)} in cells, inside the rectangle {@code (x0, y0)..(x1, y1)} that
     * the image's source rectangle covers, so each corner's place in the image follows from its place in it.
     */
    private void crustPatch(ShuttleSpriteCache crust, float[] qx, float[] qy, float x0, float y0, float x1,
                            float y1, int srcX, int srcY, int srcW, int srcH, float s, float px, float time, float a) {
        float ax = wx(qx[0], qy[0], s, px, time), ay = wy(qx[0], qy[0], s, px, time);
        float bx = wx(qx[1], qy[1], s, px, time), by = wy(qx[1], qy[1], s, px, time);
        float cx = wx(qx[2], qy[2], s, px, time), cy = wy(qx[2], qy[2], s, px, time);
        float dx = wx(qx[3], qy[3], s, px, time), dy = wy(qx[3], qy[3], s, px, time);
        float minX = Math.min(Math.min(ax, bx), Math.min(cx, dx)), maxX = Math.max(Math.max(ax, bx), Math.max(cx, dx));
        float minY = Math.min(Math.min(ay, by), Math.min(cy, dy)), maxY = Math.max(Math.max(ay, by), Math.max(cy, dy));
        float w = maxX - minX, h = maxY - minY;
        if (w < .2f || h < .2f) return;
        float[] patch = {
                (ax - minX) / w, (ay - minY) / h, (bx - minX) / w, (by - minY) / h,
                (cx - minX) / w, (cy - minY) / h, (dx - minX) / w, (dy - minY) / h,
                u(qx[0], x0, x1), v(qy[0], y0, y1), u(qx[1], x0, x1), v(qy[1], y0, y1),
                u(qx[2], x0, x1), v(qy[2], y0, y1), u(qx[3], x0, x1), v(qy[3], y0, y1)};
        drop.drawList().addSheetPatch(RenderLayer.GROUND, crust.sprite, srcX, srcY, srcW, srcH,
                minX + w / 2f, minY + h / 2f, w, h, a, patch);
    }

    /** A corner's place across the piece's image, 0 to 1. */
    private static float u(float x, float x0, float x1) { return (x - x0) / (x1 - x0); }

    /** A corner's place down the piece's image, 1 at its first row, as the drop's rock patches run. */
    private static float v(float y, float y0, float y1) { return 1f - (y - y0) / (y1 - y0); }

    /** A floor point's screen x at scale {@code s}, rippled by the heat; the ripple is the point's own, so pieces meet. */
    private float wx(float x, float y, float s, float px, float time) {
        return drop.sx(x, s) + SHIMMER_CELLS * px * (float) Math.sin(y * 2.7f + x * .9f + time * 3.1f);
    }

    private float wy(float x, float y, float s, float px, float time) {
        return drop.sy(y, s) + SHIMMER_CELLS * px * (float) Math.cos(x * 2.3f - y * .7f + time * 2.6f);
    }

    // ----- over the ground -----

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!molten(ctx.sim.getTopology().naturalVoid()) || !drop.begin(ctx, out)) return;
        NaturalVoidField field = drop.field();
        if (builtFrom != field) build(field);
        float time = FireLight.clock(ctx.animationTimeSeconds);
        lipLight(field, time);
        embers(field, time);
        for (int k = 0; k < SMOKE_ABOVE.length; k++) {
            smoke(field, -SMOKE_ABOVE[k], 3.4f + k * 1.6f, .46f, .43f, .40f, SMOTHER[k], false, time, k + 1);
        }
        ash(time);
    }

    /** How thick each deck of smoke over the rift is, against {@link #SMOKE_ALPHA}. */
    private static final float[] SMOTHER = {SMOKE_ALPHA, SMOKE_ALPHA * .6f};

    /**
     * The ground beside the lip, lit orange from the rift and flickering with it: a soft pool of added light on each
     * cell at the lip, reaching {@link LavaVoidLook#LIP_GLOW_CELLS} out, so the pools run together into one band.
     */
    private void lipLight(NaturalVoidField field, float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(LIGHT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        float cellPx = cam.cellPxSize();
        float size = LavaVoidLook.LIP_GLOW_CELLS * 2.2f * cellPx;
        int seed = drop.seed();
        float alpha = drop.alpha();
        for (int q = 0; q < lipCount; q++) {
            int o = q * 3;
            float x = lip[o], y = lip[o + 1];
            if (!drop.onMap(x, y) || !drop.inView(x, y, 1f, size * .5f)) continue;
            float flick = .78f + .22f * NaturalVoidField.Noise.value(x * .35f + time * .9f, y * .35f, seed + 31);
            drop.drawList().addAdditiveSprite(RenderLayer.GROUND, dot.sprite, drop.sx(x, 1f), drop.sy(y, 1f), size,
                    size, 0f, LavaVoidLook.GLOW_R, LavaVoidLook.GLOW_G, LavaVoidLook.GLOW_B,
                    LIP_POOL * lip[o + 2] * flick * alpha);
        }
    }

    /** The lip's pools of light: the band of ground cells they stand on, in cells out, and each one's share. */
    static final float LIP_POOL_ROW_CELLS = 1f, LIP_POOL = .13f;

    /**
     * Embers off the melt, climbing on the wind, cooling from yellow to red as they go. Each is hidden where the rock
     * stands between it and the eye, so one deep in the rift shows only through the open drop.
     */
    private void embers(NaturalVoidField field, float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(LIGHT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        DrawList out = drop.drawList();
        float floorMeters = field.depth[field.steps()];
        float alpha = drop.alpha();
        float cellPx = cam.cellPxSize();
        int seed = drop.seed();
        for (int c = 0; c < floorCount; c++) {
            int o = c * 5;
            int x = (int) floor[o], y = (int) floor[o + 1];
            int hsh = hash(x, y, seed);
            if (Math.floorMod(hsh, EMBER_EVERY) != 0) continue;
            float rise = EMBER_RISE * (.7f + .6f * ((hsh >>> 8) & 255) / 255f);
            float life = (floorMeters + EMBER_ABOVE_METERS) / rise;
            float t = frac(time / life + ((hsh >>> 16) & 255) / 255f);
            float secs = t * life;
            float z = floorMeters - .5f - secs * rise;
            float drift = secs * WIND_SPEED * (.4f + .6f * t);
            float wob = (float) Math.sin(secs * 2.3f + (hsh & 63)) * .5f;
            float px = x + ((hsh >>> 4) & 15) / 15f + WIND_X * drift - WIND_Y * wob;
            float py = y + ((hsh >>> 12) & 15) / 15f + WIND_Y * drift + WIND_X * wob;
            float s = drop.scale(z);
            if (!drop.inView(px, py, s, cellPx)) continue;
            if (z > 0f && drop.hidden(px, py, z)) continue;
            float fade = (float) Math.pow(1f - t, 1.3f) * Math.min(1f, t * 12f);
            float twinkle = .7f + .3f * (float) Math.sin(time * 11f + hsh);
            float g = LavaVoidLook.lerp(.85f, .28f, t), b = LavaVoidLook.lerp(.45f, .05f, t);
            float size = Math.max(2f, .3f * s * cellPx);
            float qx = drop.sx(px, s), qy = drop.sy(py, s);
            out.addAdditiveSprite(RenderLayer.GROUND, dot.sprite, qx, qy, size * 5f, size * 5f, 0f,
                    1f, g * .7f, b * .5f, .35f * fade * alpha);
            out.addAdditiveSprite(RenderLayer.GROUND, dot.sprite, qx, qy, size * 1.6f, size * 1.6f, 0f,
                    1f, g, b, fade * twinkle * alpha);
        }
    }

    /**
     * A deck of smoke {@code z} metres down (above the surface when negative): soft puffs on a grid drifting with the
     * wind, thick where a slow noise says so, standing only over the rift. Lit orange from below by {@code under}.
     */
    private void smoke(NaturalVoidField field, float z, float puffCells, float r, float g, float b, float thick,
                       boolean low, float time, int deck) {
        ShuttleSpriteCache sheet = sprites.spriteByPath(SMOKE_SHEET);
        if (sheet == null || sheet.pxW <= 0) return;
        BattleCamera cam = drop.camera();
        float s = drop.scale(z);
        float cellPx = cam.cellPxSize();
        float step = puffCells * .55f;
        float drift = time * WIND_SPEED * (low ? .35f : 1f);
        float dx = WIND_X * drift, dy = WIND_Y * drift;
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + puffCells, halfY = cam.vpH() / cellPx / s * .5f + puffCells;
        int i0 = (int) Math.floor((ccx - half - dx) / step), i1 = (int) Math.ceil((ccx + half - dx) / step);
        int j0 = (int) Math.floor((ccy - halfY - dy) / step), j1 = (int) Math.ceil((ccy + halfY - dy) / step);
        if ((long) (i1 - i0) * (j1 - j0) > 8000) return;
        int cells = SMOKE_SHEET_CELLS, cw = sheet.pxW / cells, ch = sheet.pxH / cells;
        int seed = drop.seed() * 13 + deck * 101;
        float alpha = drop.alpha();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = hash(i, j, seed);
                float x = i * step + dx + ((hsh & 255) / 255f - .5f) * step;
                float y = j * step + dy + (((hsh >> 8) & 255) / 255f - .5f) * step;
                // Smoke stands over the rift: where it came up from, upwind of here, and thins away from it.
                float over = overRift(field, x - WIND_X * SMOKE_LAG_CELLS, y - WIND_Y * SMOKE_LAG_CELLS, low);
                if (over <= 0f || field.nearBridge(x, y, puffCells * .75f)) continue;
                float n = .5f + .5f * NaturalVoidField.Noise.fbm(i * .21f, j * .21f, seed);
                if (n < .38f) continue;
                float size = puffCells * (.85f + ((hsh >> 16) & 255) / 255f * .6f) * cellPx * s;
                float a = thick * over * Math.min(1f, (n - .38f) / .3f) * alpha;
                // Lit orange from below, the more the nearer the melt.
                float lit = low ? .65f : .38f / (1f + deck);
                int cell = (hsh >>> 24) % (cells * cells);
                drop.drawList().addImageRegion(RenderLayer.GROUND, sheet.sprite, sheet.pxW, sheet.pxH,
                        cell % cells * cw, cell / cells * ch, cw, ch, drop.sx(x, s), drop.sy(y, s), size, size,
                        (hsh >> 4 & 1023) * .35f + time * (hsh % 2 == 0 ? 2f : -2f),
                        LavaVoidLook.lerp(r, LavaVoidLook.GLOW_R, lit), LavaVoidLook.lerp(g, LavaVoidLook.GLOW_G, lit),
                        LavaVoidLook.lerp(b, LavaVoidLook.GLOW_B, lit), a);
            }
        }
    }

    /** How much a point stands over the rift: 1 over the melt (or the open drop, above the surface), fading out. */
    private float overRift(NaturalVoidField field, float x, float y, boolean low) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        if (ix < 0 || iy < 0 || ix >= field.w || iy >= field.h) return 0f;
        if (low) return overFloor[iy * field.w + ix] ? 1f : 0f;
        if (field.byBridge(x, y)) return 0f;
        return LavaVoidLook.smooth(-2.5f, 2f, field.sample(field.edge, x, y));
    }

    /** Ash falling over everything in view, drifting on the wind, a few metres above the ground. */
    private void ash(float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(LIGHT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        float s = drop.scale(-6f);
        float cellPx = cam.cellPxSize();
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + 2f, halfY = cam.vpH() / cellPx / s * .5f + 2f;
        float drift = time * WIND_SPEED * .8f;
        float dx = WIND_X * drift, dy = WIND_Y * drift;
        int i0 = (int) Math.floor((ccx - half - dx) / ASH_SPACING), i1 = (int) Math.ceil((ccx + half - dx) / ASH_SPACING);
        int j0 = (int) Math.floor((ccy - halfY - dy) / ASH_SPACING), j1 = (int) Math.ceil((ccy + halfY - dy) / ASH_SPACING);
        if ((long) (i1 - i0) * (j1 - j0) > 12000) return;
        float alpha = drop.alpha();
        float size = Math.max(1.2f, .12f * cellPx * s);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = hash(i, j, 977);
                if ((hsh & 3) != 0) continue;
                float sway = (float) Math.sin(time * .9f + (hsh & 255)) * .6f;
                float x = i * ASH_SPACING + dx + ((hsh >>> 8) & 255) / 255f * ASH_SPACING - WIND_Y * sway;
                float y = j * ASH_SPACING + dy + ((hsh >>> 16) & 255) / 255f * ASH_SPACING + WIND_X * sway;
                float grey = .45f + .3f * ((hsh >>> 24) & 15) / 15f;
                drop.drawList().addSprite(RenderLayer.GROUND, dot.sprite, drop.sx(x, s), drop.sy(y, s), size, size,
                        0f, grey, grey * .96f, grey * .92f, .55f * alpha);
            }
        }
    }

    private static int hash(int i, int j, int k) {
        int h = i * 374761393 + j * 668265263 + k * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        return h ^ (h >>> 16);
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
