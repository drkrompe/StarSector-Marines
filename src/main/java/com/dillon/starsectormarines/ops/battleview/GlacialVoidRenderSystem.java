package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.Arrays;

/**
 * The glacial crevasse's own drawing (`voids.md`, natural styles): what an ice floor and a cold wind add to the
 * natural drop that {@link NaturalVoidRenderSystem} draws. Its shape, walls and ledges are the drop's, in ice; this
 * is the floor and the air. Colour rules are {@link IceVoidLook}'s.
 *
 * <ul>
 *   <li><b>The floor</b> ({@link #drawFloor}), in the drop's painter's order in place of its water: old dark ice,
 *       cracked, snow drifted against the walls' feet, pools of meltwater frozen glassy with a glint moving on them;
 *       and a cold mist lying low over it.</li>
 *   <li><b>Over the ground</b> ({@link #collect}), after the tiles and the lip's fray: spindrift, snow blown off the
 *       lip wherever the wind leaves the ground for the drop, streaming out over it and sinking in, each streak
 *       hidden where the ice stands between it and the eye, in gusts; and fine snow falling over everything in
 *       view.</li>
 * </ul>
 *
 * <p>Snow over the tiles is drawn as added light, never composited: a translucent piece composited over the ground
 * thins the ground target's alpha, which the composite reads as shade, so white snow would land grey on the
 * snowfield. For the same reason no mist stands over the lip; the mist keeps to the drop.
 *
 * <p>Presentation only, in the ground layer, so units draw over all of it and nothing here hides one.
 */
final class GlacialVoidRenderSystem implements RenderSystem {

    /** A soft round light, white, as the city drop's lamps; drawn composited here, not added. */
    static final String DOT_SPRITE = VoidBackdropRenderSystem.LIGHT_SPRITE;
    /** The wind over the ice: its bearing and how fast it carries mist and snow, cells a second. */
    static final float WIND_X = .87f, WIND_Y = .49f, WIND_SPEED = 2.4f;
    /** The low mist over the floor: metres above it, puff size in cells and thickness. */
    static final float MIST_LOW_METERS = 3f, MIST_LOW_PUFF = 3.2f, MIST_LOW_ALPHA = .16f;
    /** Spindrift: streaks from each lip cell the wind blows off, how long one lives, seconds, and how far it sinks. */
    static final int DRIFT_PER_CELL = 2;
    static final float DRIFT_LIFE = 3.2f, DRIFT_SINK_METERS = 9f, DRIFT_ALPHA = .32f;
    /** How much of a lip cell's edge must face the wind for snow to blow off it, as the cosine between them. */
    static final float DRIFT_FACING = .25f;
    /** Falling snow: the spacing of flakes, in cells, and the metres above the ground they are drawn at. */
    static final float SNOW_SPACING = 1.25f, SNOW_ABOVE_METERS = 5f;

    private final NaturalVoidRenderSystem drop;
    private final BattleSprites sprites;

    private NaturalVoidField builtFrom;
    /** Field cells over the floor, for the low mist's footing. */
    private boolean[] overFloor;
    /** Lip cells the wind blows snow off: {@code {x, y}} in the field's cells, at the cell's centre. */
    private float[] lip = new float[0];
    private int lipCount;

    private final float[] rgb = new float[3];

    GlacialVoidRenderSystem(NaturalVoidRenderSystem drop, BattleSprites sprites) {
        this.drop = drop;
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    static boolean glacial(NaturalVoid look) {
        return look != null && look.style() == NaturalVoid.Style.ICE;
    }

    /**
     * Whether snow blows off ground whose edge into the drop points along {@code (gx, gy)} (not necessarily of unit
     * length): when the wind leaves the ground for the drop there, not when it blows in over the lip from it.
     */
    static boolean windward(float gx, float gy, float windX, float windY) {
        float len = (float) Math.hypot(gx, gy);
        return len > 1e-4f && (gx * windX + gy * windY) / len >= DRIFT_FACING;
    }

    // ----- shape -----

    private void build(NaturalVoidField field) {
        builtFrom = field;
        int w = field.w, h = field.h;
        lipCount = 0;
        overFloor = VoidHaze.overFloor(field);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float cx = x + .5f, cy = y + .5f;
                if (field.open[i]) continue;
                float out = -field.sample(field.edge, cx, cy);
                if (out < 0f || out > 1f) continue;
                // A bridge's end is road onto the deck, not the lip: no snow blows off it.
                if (field.byBridge(cx, cy)) continue;
                float gx = field.sample(field.edge, cx + .5f, cy) - field.sample(field.edge, cx - .5f, cy);
                float gy = field.sample(field.edge, cx, cy + .5f) - field.sample(field.edge, cx, cy - .5f);
                if (!windward(gx, gy, WIND_X, WIND_Y)) continue;
                lip = grow(lip, lipCount * 2 + 2);
                lip[lipCount * 2] = cx;
                lip[lipCount * 2 + 1] = cy;
                lipCount++;
            }
        }
    }

    private static float[] grow(float[] a, int need) {
        return need <= a.length ? a : Arrays.copyOf(a, Math.max(need, a.length * 2 + 64));
    }

    // ----- the floor, in the drop's painter's order -----

    /** The ice floor at its depth, in place of the drop's own floor, and the mist lying over it. */
    void drawFloor(NaturalVoidField.Band band, float time) {
        NaturalVoidField field = drop.field();
        if (builtFrom != field) build(field);
        BattleCamera cam = drop.camera();
        float alpha = drop.alpha();
        float margin = cam.cellPxSize() * 2f;
        int seed = drop.seed();
        PolyMesh mesh = drop.poly();
        float[] qd = band.quads, at = band.attrs;
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            // Each piece at its own depth along the crevasse, as blue as that depth is against the crevasse's steps:
            // a floor the crevasse shallows to takes less of the deep.
            float tint = IceVoidLook.deepTint(Math.max(1f, band.z(q, 4)), Math.max(1f, band.depth));
            if (!drop.inView(at[o], at[o + 1], drop.scale(band.z(q, 4)), margin)) continue;
            float x = at[o], y = at[o + 1], light = at[o + 2];
            // Cells in from the floor's edge, from the smooth field rather than the piece's own figure, which steps
            // between neighbouring pieces and would speckle the pools and drifts.
            float in = field.sample(field.reach[field.steps()], x, y);
            float fx = at[o + 4], fy = at[o + 5], sky = at[o + 6];
            float along = x * fx + y * fy, across = -x * fy + y * fx;
            float pool = NaturalVoidField.Noise.fbm(x * .2f, y * .2f, seed + 61);
            float crack = NaturalVoidField.Noise.fbm(x * .35f, y * .35f, seed + 63);
            float glint = NaturalVoidField.Noise.value(along * .22f - time * .2f, across * .6f, seed + 67);
            IceVoidLook.floor(in, pool, crack, glint, sky, light, rgb);
            float r = IceVoidLook.lerp(rgb[0], IceVoidLook.DEEP_R, tint * .6f);
            float g = IceVoidLook.lerp(rgb[1], IceVoidLook.DEEP_G, tint * .6f);
            float b = IceVoidLook.lerp(rgb[2], IceVoidLook.DEEP_B, tint * .6f);
            float s0 = drop.scale(band.z(q, 0)), s1 = drop.scale(band.z(q, 1));
            float s2 = drop.scale(band.z(q, 2)), s3 = drop.scale(band.z(q, 3));
            mesh.appendQuad(drop.sx(qd[o], s0), drop.sy(qd[o + 1], s0), drop.sx(qd[o + 2], s1), drop.sy(qd[o + 3], s1),
                    drop.sx(qd[o + 4], s2), drop.sy(qd[o + 5], s2), drop.sx(qd[o + 6], s3), drop.sy(qd[o + 7], s3),
                    r, g, b, alpha);
        }
        if (!mesh.isEmpty()) drop.drawList().addPoly(RenderLayer.GROUND, mesh);
        VoidHaze.draw(drop, sprites, overFloor, MIST_LOW_METERS, MIST_LOW_PUFF, MIST_LOW_ALPHA, WIND_X, WIND_Y,
                WIND_SPEED * .12f, IceVoidLook.MIST_R, IceVoidLook.MIST_G, IceVoidLook.MIST_B, time);
    }

    // ----- over the ground -----

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!glacial(ctx.sim.getTopology().naturalVoid()) || !drop.begin(ctx, out)) return;
        NaturalVoidField field = drop.field();
        if (builtFrom != field) build(field);
        float time = FireLight.clock(ctx.animationTimeSeconds);
        spindrift(field, time);
        snowfall(time);
    }

    /**
     * Snow blown off the lip: from each lip cell the wind leaves for the drop, streaks streaming out downwind over
     * the crevasse and sinking into it as they go, thinning out, coming in gusts. A streak deep enough that the ice
     * stands between it and the eye is not drawn.
     */
    private void spindrift(NaturalVoidField field, float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(DOT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        float cellPx = cam.cellPxSize();
        float alpha = drop.alpha();
        int seed = drop.seed();
        float angle = (float) Math.toDegrees(Math.atan2(WIND_Y, WIND_X));
        for (int c = 0; c < lipCount; c++) {
            float lx = lip[c * 2], ly = lip[c * 2 + 1];
            if (!drop.inView(lx, ly, 1f, cellPx * 8f)) continue;
            for (int k = 0; k < DRIFT_PER_CELL; k++) {
                int hsh = hash((int) lx * 3 + k, (int) ly, seed + 71);
                float life = DRIFT_LIFE * (.7f + .6f * ((hsh >>> 8) & 255) / 255f);
                float t = frac(time / life + ((hsh >>> 16) & 255) / 255f);
                float secs = t * life;
                float run = secs * WIND_SPEED * (.8f + .4f * ((hsh >>> 4) & 15) / 15f);
                float wob = (float) Math.sin(secs * 1.7f + (hsh & 63)) * .35f;
                float px = lx + ((hsh & 15) / 15f - .5f) * .8f + WIND_X * run - WIND_Y * wob;
                float py = ly + (((hsh >>> 12) & 15) / 15f - .5f) * .8f + WIND_Y * run + WIND_X * wob;
                // Over the ground it skims the surface; once out over the drop it sinks into it.
                float over = field.sample(field.edge, px, py);
                float z = -.8f + (over > 0f ? DRIFT_SINK_METERS * t * t : 0f);
                float s = drop.scale(z);
                if (!drop.inView(px, py, s, cellPx * 2f)) continue;
                if (z > 0f && drop.hidden(px, py, z)) continue;
                float gust = .35f + .65f * IceVoidLook.clamp01(.5f + .9f
                        * NaturalVoidField.Noise.value(time * .3f + lx * .04f, ly * .04f, seed + 73));
                float fade = (float) Math.sin(Math.PI * t);
                float a = DRIFT_ALPHA * fade * gust * alpha;
                if (a < .01f) continue;
                float len = (1.1f + .9f * ((hsh >>> 20) & 15) / 15f) * cellPx * s;
                drop.drawList().addAdditiveSprite(RenderLayer.GROUND, dot.sprite, drop.sx(px, s), drop.sy(py, s),
                        len, .32f * cellPx * s, angle, .93f, .96f, 1f, a);
            }
        }
    }

    /** Fine snow falling over everything in view, drifting on the wind, a few metres above the ground. */
    private void snowfall(float time) {
        ShuttleSpriteCache dot = sprites.spriteByPath(DOT_SPRITE);
        if (dot == null) return;
        BattleCamera cam = drop.camera();
        float s = drop.scale(-SNOW_ABOVE_METERS);
        float cellPx = cam.cellPxSize();
        float ccx = cam.panCellX() + NaturalVoidRenderSystem.MARGIN, ccy = cam.panCellY() + NaturalVoidRenderSystem.MARGIN;
        float half = cam.vpW() / cellPx / s * .5f + 2f, halfY = cam.vpH() / cellPx / s * .5f + 2f;
        float drift = time * WIND_SPEED * .6f;
        float dx = WIND_X * drift, dy = WIND_Y * drift;
        int i0 = (int) Math.floor((ccx - half - dx) / SNOW_SPACING), i1 = (int) Math.ceil((ccx + half - dx) / SNOW_SPACING);
        int j0 = (int) Math.floor((ccy - halfY - dy) / SNOW_SPACING), j1 = (int) Math.ceil((ccy + halfY - dy) / SNOW_SPACING);
        if ((long) (i1 - i0) * (j1 - j0) > 12000) return;
        float alpha = drop.alpha();
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int hsh = hash(i, j, 1913);
                if ((hsh & 3) != 0) continue;
                float sway = (float) Math.sin(time * 1.1f + (hsh & 255)) * .5f;
                float x = i * SNOW_SPACING + dx + ((hsh >>> 8) & 255) / 255f * SNOW_SPACING - WIND_Y * sway;
                float y = j * SNOW_SPACING + dy + ((hsh >>> 16) & 255) / 255f * SNOW_SPACING + WIND_X * sway;
                float size = Math.max(1.2f, (.10f + .06f * ((hsh >>> 24) & 15) / 15f) * cellPx * s);
                drop.drawList().addAdditiveSprite(RenderLayer.GROUND, dot.sprite, drop.sx(x, s), drop.sy(y, s), size,
                        size, 0f, .95f, .97f, 1f, .45f * alpha);
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
