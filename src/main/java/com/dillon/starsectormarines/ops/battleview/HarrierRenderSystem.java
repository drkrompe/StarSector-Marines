package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierRules;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the Rav fliers on {@link RenderLayer#DRONES}, over the roofs as a drone is: each Harrier in its pose
 * ({@link HarrierPose}), drawn a little larger the higher it flies, and the fallen spinning down and fading where
 * they were hit. The shadow is {@link UnitShadowRenderSystem}'s, which is what makes the height read.
 */
public final class HarrierRenderSystem implements RenderSystem {

    /** How much larger a flier at loiter height draws than one at the bottom of a dive. */
    static final float ALTITUDE_SCALE_GAIN = 0.18f;
    static final float FALL_SPIN_DEG_PER_SEC = 540f;

    private final BattleSprites sprites;

    public HarrierRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DRONES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        SwarmFliers fliers = ctx.sim.swarmFliers();
        if (fliers.packs().isEmpty() && fliers.falls().isEmpty()) return;
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        FogOfWarService vis = ctx.sim.getFogOfWar();

        for (SwarmFliers.Fall fall : fliers.falls()) {
            float t = Math.min(1f, fall.seconds() / HarrierRules.FALL_SECONDS);
            ShuttleSpriteCache cache = sprites.spriteByPath(fall.look().dive());
            if (cache == null || !view.visible(fall.x(), fall.y(), 3f)) continue;
            float scale = (1f + ALTITUDE_SCALE_GAIN * fall.altitude() * (1f - t)) * (1f - 0.35f * t);
            out.addSprite(RenderLayer.DRONES, cache.sprite, cam.cellToScreenX(fall.x()), cam.cellToScreenY(fall.y()),
                    cache.pxW / HarrierLook.PX_PER_CELL * cellPx * scale,
                    cache.pxH / HarrierLook.PX_PER_CELL * cellPx * scale,
                    fall.facing() + FALL_SPIN_DEG_PER_SEC * fall.seconds(),
                    0.75f, 0.7f, 0.7f, ctx.alphaMult * (1f - t));
        }

        for (HarrierPack pack : fliers.packs()) {
            for (HarrierFlight m : pack.members) {
                float alpha = ctx.alphaMult * visibility(ctx, vis, m.id);
                if (alpha <= 0f || !view.visible(m.body.x, m.body.y, 3f)) continue;
                HarrierPose pose = HarrierPose.of(m, pack.phase());
                ShuttleSpriteCache cache = sprites.spriteByPath(pose.sprite());
                if (cache == null) continue;
                float scale = 1f + ALTITUDE_SCALE_GAIN * m.altitude;
                out.addSprite(RenderLayer.DRONES, cache.sprite, cam.cellToScreenX(m.body.x), cam.cellToScreenY(m.body.y),
                        cache.pxW / HarrierLook.PX_PER_CELL * cellPx * scale * pose.widthScale(),
                        cache.pxH / HarrierLook.PX_PER_CELL * cellPx * scale,
                        m.body.facingDegrees, 1f, 1f, 1f, alpha);
            }
        }
    }

    /** As a drone is: hidden in the fog where the host hides units, faded as it slips out of sight. */
    static float visibility(RenderContext ctx, FogOfWarService vis, long id) {
        if (!ctx.hostProfile.fogHidesUnits()) return 1f;
        int index = ctx.sim.liveUnitIndexOf(id);
        if (index < 0) return 1f;
        byte uv = vis.getUnitVisibility(index);
        if (uv == FogOfWarService.VIS_HIDDEN) return 0f;
        return uv == FogOfWarService.VIS_FADING ? vis.getFadeAlpha(index) : 1f;
    }
}
