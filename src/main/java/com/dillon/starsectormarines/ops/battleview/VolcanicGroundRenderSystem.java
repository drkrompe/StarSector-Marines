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
    private final LavaFlowLayer flows;
    private CellTopology builtFrom;
    private List<VolcanicGroundLook.Mark> marks = List.of();

    VolcanicGroundRenderSystem(BattleSprites sprites, LavaFlowLayer flows) {
        this.sprites = sprites;
        this.flows = flows;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim.surface() != SurfacePalette.VOLCANIC) return;
        CellTopology topology = ctx.sim.getTopology();
        if (builtFrom != topology) {
            builtFrom = topology;
            marks = VolcanicGroundLook.marks(topology);
        }
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float time = FireLight.clock(ctx.animationTimeSeconds);
        // The older flows ({@link CellTopology#lavaFlows}) under the marks, shaded so their margins run as curves.
        flows.collect(ctx, out, sprites.spriteByPath(MoltenVoidRenderSystem.CRUST_TEXTURE),
                MoltenVoidRenderSystem.CRUST_TEXTURE, time);
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
