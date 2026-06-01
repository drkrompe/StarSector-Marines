package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.component.Crashing;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.vision.VisionService;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the {@link RenderLayer#DRONES} layer — recon/attack drones that hover at
 * roof altitude (painted above ROOFS so a drone over a building overlays the roof
 * rather than being occluded by it). Per live/crashing drone: one rotated
 * {@code SPRITE} hull, then — while alive — an HP bar via the shared
 * {@link HpBarDecor} (placement uses the layer's own {@code HP_BAR_GAP}).
 *
 * <p>Faithful port of the former inline {@code BattleRenderer.renderDrones}: same
 * crash/visibility gating and fade-alpha (VIS_FADING fade + crash fade-out), same
 * hull sizing and HP-bar geometry, same per-drone hull-then-bar submission order.
 * The hull goes through the whole-texture {@code SPRITE} command (no batch
 * registration), which resets sprite angle after each draw — so the old
 * end-of-pass {@code setAngle(0)} reset is dropped. The drone sprite is loaded at
 * {@code BattleScreen.attach} (it was lazily ensured inside the pass) so
 * {@link #collect} stays GL-free.
 */
public final class DroneRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public DroneRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DRONES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        ShuttleSpriteCache cache = sprites.droneSprite();
        if (cache == null) return;

        VisionService vis = ctx.sim.getVision();
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        float pxH = Drone.VISUAL_CELLS * cellPx;
        float pxW = pxH * cache.aspect;
        float barW = cellPx * 0.9f;

        ComponentStore<Crashing> crashStore = ctx.sim.getCrashing();
        for (Unit u : ctx.sim.getUnits()) {
            if (!(u instanceof Drone)) continue;
            Drone d = (Drone) u;
            boolean alive = d.isAlive();
            // A dead drone is drawn only while it carries a Crashing component
            // (falling); once it settles the component is gone and it drops off.
            Crashing crash = alive ? null : crashStore.get(d.entityId);
            if (!alive && crash == null) continue;
            byte uv = vis.getUnitVisibility(d.denseIdx);
            if (alive && uv == VisionService.VIS_HIDDEN) continue;

            float cx = cam.cellToScreenX(d.body.x);
            float cy = cam.cellToScreenY(d.body.y);
            float drawAlpha = alphaMult;
            if (alive && uv == VisionService.VIS_FADING) {
                drawAlpha *= vis.getFadeAlpha(d.denseIdx);
            }
            if (!alive) {
                float t = Math.max(0f, Math.min(1f, crash.timer / Drone.CRASH_DURATION_SEC));
                drawAlpha *= t;
            }

            out.addSprite(RenderLayer.DRONES, cache.sprite,
                    cx, cy, pxW, pxH, d.body.facingDegrees,
                    1f, 1f, 1f, drawAlpha);

            if (alive) {
                float barY = cy + pxH / 2f + BattleRenderer.HP_BAR_GAP;
                HpBarDecor.emit(out, RenderLayer.DRONES, cx, barY, barW,
                        d.getHp() / d.getMaxHp(), drawAlpha);
            }
        }
    }
}
