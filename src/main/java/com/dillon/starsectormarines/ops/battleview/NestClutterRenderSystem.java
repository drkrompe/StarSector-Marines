package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.NestClutter;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * A nest's clutter ({@link NestClutter}): egg clutches, brood pods and shed husks lying on the creep beside its roots
 * and round its mouth, over the creep and under everything that stands on it, as decals are. A clutch's and a pod's
 * amber breathes on its own glow layer. Upright, as every swarm painting is, since their light is painted in.
 */
final class NestClutterRenderSystem implements RenderSystem {

    /** Drawn width of each kind's painting, in cells (the export's canvas cells times its display scale). */
    static final float[] SIZE_CELLS = {1.89f, 2.1f, 2.1f};
    /** How deep the amber breathes, and how fast. */
    static final float GLOW_DEPTH = 0.4f, GLOW_HZ = 0.35f;

    private final BattleSprites sprites;

    NestClutterRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DECALS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim.swarmBores() == null || ctx.sim.swarmBores().bores().isEmpty()) return;
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float clock = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : 0f;
        for (NestClutter.Item it : NestClutter.of(ctx.sim)) {
            if (!view.visible(it.x(), it.y(), 2f)) continue;
            String base = "graphics/structures/" + it.kind().name + "-" + it.variant();
            ShuttleSpriteCache body = sprites.spriteByPath(base + ".png");
            if (body == null) continue;
            float w = SIZE_CELLS[it.kind().ordinal()] * cellPx, h = w * body.pxH / Math.max(1f, body.pxW);
            float sx = cam.cellToScreenX(it.x()), sy = cam.cellToScreenY(it.y());
            out.addSprite(RenderLayer.DECALS, body.sprite, sx, sy, w, h, 0f, 1f, 1f, 1f, ctx.alphaMult);
            if (it.kind() == NestClutter.Kind.HUSK) continue;
            ShuttleSpriteCache glow = sprites.spriteByPath(base + "-glow.png");
            if (glow == null) continue;
            double wave = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * GLOW_HZ * clock + it.x() * 1.7 + it.y() * 0.9);
            float bright = 1f - GLOW_DEPTH * (float) wave;
            out.addSprite(RenderLayer.DECALS, glow.sprite, sx, sy, w, h, 0f, bright, bright, bright, ctx.alphaMult);
        }
    }
}
