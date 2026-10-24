package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFall;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierRules;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the Rav fliers on {@link RenderLayer#DRONES}, over the roofs as a drone is: each Harrier in its pose
 * ({@link HarrierPose}) with its body laid on in segments ({@link HarrierBodyComposer}), drawn a little larger the higher it flies, and the fallen tumbling through their fall
 * frames ({@link HarrierFall}) until their bodies lie on the ground. The shadow is {@link UnitShadowRenderSystem}'s, which is what makes the height read.
 */
public final class HarrierRenderSystem implements RenderSystem {

    /** How much larger a flier at loiter height draws than one at the bottom of a dive. */
    static final float ALTITUDE_SCALE_GAIN = 0.18f;
    /** Fall frames a second as a shot flier tumbles. */
    static final float FALL_FRAMES_PER_SEC = 11f;

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

        for (HarrierFall fall : fliers.falls()) {
            ShuttleSpriteCache cache = sprites.spriteByPath(fallSprite(fall));
            if (cache == null || !view.visible(fall.x(), fall.y(), 3f)) continue;
            float scale = 1f + ALTITUDE_SCALE_GAIN * fall.altitude();
            out.addSprite(RenderLayer.DRONES, cache.sprite, cam.cellToScreenX(fall.x()), cam.cellToScreenY(fall.y()),
                    cache.pxW / HarrierLook.PX_PER_CELL * cellPx * scale,
                    cache.pxH / HarrierLook.PX_PER_CELL * cellPx * scale,
                    fall.facing(), 1f, 1f, 1f, ctx.alphaMult);
        }

        for (HarrierPack pack : fliers.packs()) {
            for (HarrierFlight m : pack.members) {
                float alpha = ctx.alphaMult * visibility(ctx, vis, m.id);
                if (alpha <= 0f || !view.visible(m.body.x, m.body.y, 3f)) continue;
                HarrierPose pose = HarrierPose.of(m, pack.phase());
                float scale = 1f + ALTITUDE_SCALE_GAIN * m.shownAltitude;
                HarrierBodyComposer.compose(sprites, m, pose.sprite(), m.body.x, m.body.y, scale,
                        (part, x, y, degrees, w, h) -> out.addSprite(RenderLayer.DRONES, part.sprite,
                                cam.cellToScreenX(x), cam.cellToScreenY(y), w * cellPx, h * cellPx, degrees,
                                1f, 1f, 1f, alpha));
            }
        }
    }

    /** The fall frame a tumbling flier shows: limp wings thrown about, a new drawing every few ticks. */
    static String fallSprite(HarrierFall fall) {
        int frame = (int) ((long) (fall.seconds() * FALL_FRAMES_PER_SEC) + fall.id) % HarrierLook.FALL_FRAMES;
        return fall.look.fall(frame + 1);
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
