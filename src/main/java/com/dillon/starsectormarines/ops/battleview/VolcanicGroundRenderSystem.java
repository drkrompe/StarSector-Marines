package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * A volcanic world's ground, over its basalt tiles ({@code world-surface-palette.md}, Volcanic): ash drifted into
 * the hollows, and where the flow beneath is still hot, glowing cracks, vents and embers, each breathing a little and
 * casting a soft pool of the lava rift's own light ({@link LavaVoidLook}) on the rock around it. Where the marks go
 * is {@link VolcanicGroundLook}'s.
 *
 * <p>Presentation only, in the ground layer after the tiles, so raised ground, formations and units all draw over
 * it. Everything is drawn additively: the sheets are light on black, so the black adds nothing, and nothing here
 * thins the ground target's alpha (which would read as shade).
 */
final class VolcanicGroundRenderSystem implements RenderSystem {

    static final String DIR = "graphics/battle/volcanic/";
    /** How bright the ash lies, and the glowing marks at their steadiest. */
    static final float ASH_ALPHA = .42f, GLOW_ALPHA = .95f;
    /** The soft light a glowing mark casts around itself: its size against the mark's, and its strength. */
    static final float POOL_SIZE = 2.6f, POOL_ALPHA = .16f;
    /** How deep and how fast the glow breathes. */
    static final float BREATH = .22f, BREATH_RATE = .7f;

    private final BattleSprites sprites;
    private CellTopology builtFrom;
    private List<VolcanicGroundLook.Mark> marks = List.of();
    private float[] flowCover = new float[0];

    VolcanicGroundRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim.surface() != SurfacePalette.VOLCANIC) return;
        CellTopology topology = ctx.sim.getTopology();
        if (builtFrom != topology) {
            builtFrom = topology;
            marks = VolcanicGroundLook.marks(topology);
            flowCover = blur(topology.lavaFlows(), topology.getWidth(), topology.getHeight());
        }
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float time = FireLight.clock(ctx.animationTimeSeconds);
        flows(ctx, topology, time, out);
        ShuttleSpriteCache light = sprites.spriteByPath(MoltenVoidRenderSystem.LIGHT_SPRITE);
        for (VolcanicGroundLook.Mark m : marks) {
            float size = m.cells() * cellPx;
            float sx = cam.cellToScreenX(m.x()), sy = cam.cellToScreenY(m.y());
            float reach = size * (m.kind() == VolcanicGroundLook.Kind.ASH ? .75f : POOL_SIZE * .5f);
            if (sx + reach < cam.vpX() || sx - reach > cam.vpX() + cam.vpW()
                    || sy + reach < cam.vpY() || sy - reach > cam.vpY() + cam.vpH()) continue;
            ShuttleSpriteCache sprite = sprites.spriteByPath(path(m));
            if (sprite == null) continue;
            float a = ctx.alphaMult;
            if (m.kind() == VolcanicGroundLook.Kind.ASH) {
                out.addAdditiveSprite(RenderLayer.GROUND, sprite.sprite, sx, sy, size, size, m.angle(),
                        1f, 1f, 1f, ASH_ALPHA * a);
                continue;
            }
            float breath = 1f - BREATH * .5f * (1f + (float) Math.sin(time * BREATH_RATE * 6.2832f
                    + VolcanicGroundLook.hash((int) m.x(), (int) m.y(), 7) % 628 * .01f));
            if (light != null) {
                float pool = size * POOL_SIZE;
                out.addAdditiveSprite(RenderLayer.GROUND, light.sprite, sx, sy, pool, pool, 0f,
                        LavaVoidLook.GLOW_R, LavaVoidLook.GLOW_G, LavaVoidLook.GLOW_B, POOL_ALPHA * breath * a);
            }
            out.addAdditiveSprite(RenderLayer.GROUND, sprite.sprite, sx, sy, size, size, m.angle(),
                    1f, 1f, 1f, GLOW_ALPHA * breath * a);
        }
    }

    /** Cells one repeat of the crust image spans on an older flow. */
    static final float FLOW_TILE_CELLS = 10f;
    /** The cooled crust's tint and cover, inside a flow and along its edge. */
    static final float FLOW_R = .30f, FLOW_G = .26f, FLOW_B = .25f, FLOW_ALPHA = .94f, FLOW_EDGE_ALPHA = .62f;
    /** How strongly a hot flow's cracks still glow. */
    static final float FLOW_GLOW = .85f;

    /**
     * A volcanic world's older flows ({@link CellTopology#lavaFlows}): the lava rift's crust image laid dark and
     * still over each flow cell, and where the flow is still hot, the same image again added in the melt's light so
     * its cracks glow, breathing.
     */
    private void flows(RenderContext ctx, CellTopology topology, float time, DrawList out) {
        float[] heat = topology.lavaFlows();
        if (heat.length == 0 || flowCover.length != heat.length) return;
        ShuttleSpriteCache crust = sprites.spriteByPath(MoltenVoidRenderSystem.CRUST_TEXTURE);
        if (crust == null || crust.pxW < 4) return;
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize(), a = ctx.alphaMult;
        int w = topology.getWidth(), h = topology.getHeight();
        int tile = (int) FLOW_TILE_CELLS;
        int perX = Math.max(1, Math.round(crust.pxW / FLOW_TILE_CELLS));
        int perY = Math.max(1, Math.round(crust.pxH / FLOW_TILE_CELLS));
        for (int y = 0; y < h; y++) {
            float sy = cam.cellToScreenY(y + .5f);
            if (sy + cellPx < cam.vpY() || sy - cellPx > cam.vpY() + cam.vpH()) continue;
            for (int x = 0; x < w; x++) {
                float hot = heat[y * w + x];
                if (flowCover[y * w + x] <= 0f) continue;
                float sx = cam.cellToScreenX(x + .5f);
                if (sx + cellPx < cam.vpX() || sx - cellPx > cam.vpX() + cam.vpW()) continue;
                int srcX = Math.min(crust.pxW - perX, Math.floorMod(x, tile) * perX);
                int srcY = Math.min(crust.pxH - perY, Math.floorMod(y, tile) * perY);
                boolean edge = edge(heat, w, h, x, y);
                if (!edge) {
                    out.addImageRegion(RenderLayer.GROUND, crust.sprite, crust.pxW, crust.pxH, srcX, srcY, perX,
                            perY, sx, sy, cellPx, cellPx, 0f, FLOW_R, FLOW_G, FLOW_B, FLOW_ALPHA * a);
                } else {
                    // An edge cell, flow or beside one, in pieces each as covered as the blended flow round it and
                    // ragged by a little noise: a rounded, lobed margin rather than the cells' steps.
                    float part = cellPx / EDGE_PARTS;
                    int pw = Math.max(1, perX / EDGE_PARTS), ph = Math.max(1, perY / EDGE_PARTS);
                    for (int j = 0; j < EDGE_PARTS; j++) for (int i = 0; i < EDGE_PARTS; i++) {
                        float fx = x + (i + .5f) / EDGE_PARTS, fy = y + (j + .5f) / EDGE_PARTS;
                        float rag = (Math.floorMod(VolcanicGroundLook.hash(x * EDGE_PARTS + i, y * EDGE_PARTS + j,
                                0x464c), 100) - 50) * .0024f;
                        float k = Math.max(0f, Math.min(1f, (cover(flowCover, w, h, fx, fy) + rag - .4f) / .2f));
                        if (k <= 0f) continue;
                        out.addImageRegion(RenderLayer.GROUND, crust.sprite, crust.pxW, crust.pxH, srcX + i * pw,
                                srcY + j * ph, pw, ph, cam.cellToScreenX(fx), cam.cellToScreenY(fy), part, part, 0f,
                                FLOW_R, FLOW_G, FLOW_B, FLOW_ALPHA * k * a);
                    }
                }
                if (hot < .05f) continue;
                float breath = 1f - BREATH * .5f * (1f + (float) Math.sin(time * BREATH_RATE * 6.2832f
                        + (x * 7 + y * 13) % 628 * .01f));
                out.addAdditiveImageRegion(RenderLayer.GROUND, crust.sprite, crust.pxW, crust.pxH, srcX, srcY, perX,
                        perY, sx, sy, cellPx, cellPx, 0f, LavaVoidLook.GLOW_R, LavaVoidLook.GLOW_G * .7f,
                        LavaVoidLook.GLOW_B * .5f, FLOW_GLOW * hot * hot * breath * (edge ? .3f : 1f) * a);
            }
        }
    }

    /** Pieces a side an older flow's edge cells are drawn in. */
    static final int EDGE_PARTS = 4;

    /** Whether any of the eight cells round {@code (x, y)} lies off the flows. */
    static boolean edge(float[] heat, int w, int h, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h || heat[ny * w + nx] <= 0f) return true;
        }
        return false;
    }

    /** How much of the ground about the point {@code (fx, fy)}, in cells, is flow: the blurred cover, blended. */
    static float cover(float[] blur, int w, int h, float fx, float fy) {
        float gx = fx - .5f, gy = fy - .5f;
        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy);
        float tx = gx - x0, ty = gy - y0;
        return lerp(lerp(at(blur, w, h, x0, y0), at(blur, w, h, x0 + 1, y0), tx),
                lerp(at(blur, w, h, x0, y0 + 1), at(blur, w, h, x0 + 1, y0 + 1), tx), ty);
    }

    private static float at(float[] blur, int w, int h, int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h ? 0f : blur[y * w + x];
    }

    /** Each cell's share of flow among the nine round it: 0 on cells more than a cell off any flow. */
    static float[] blur(float[] heat, int w, int h) {
        if (heat.length == 0) return new float[0];
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int n = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && heat[ny * w + nx] > 0f) n++;
            }
            out[y * w + x] = n / 9f;
        }
        return out;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static String path(VolcanicGroundLook.Mark m) {
        String name = switch (m.kind()) {
            case FISSURE -> "fissure-";
            case VENT -> "vent-";
            case EMBERS -> "embers-";
            case ASH -> "ash-drift-";
        };
        return DIR + name + (m.sprite() + 1) + ".png";
    }
}
