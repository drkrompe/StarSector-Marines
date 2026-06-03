package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.air.components.CrashingComponent;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.vision.VisionService;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.Map;

/**
 * Emits the {@link RenderLayer#DRONES} layer — recon/attack drones that hover at
 * roof altitude (painted above ROOFS so a drone over a building overlays the roof
 * rather than being occluded by it). Two passes: crashing wrecks (read straight
 * from the {@link CrashingComponent} component store, fading hull only, drawn first so they
 * sit under the living) then live drones (dense-registry iteration — one rotated
 * {@code SPRITE} hull + an HP bar via the shared {@link HpBarDecor}, placement
 * uses the layer's own {@code HP_BAR_GAP}).
 *
 * <p>Faithful port of the former inline {@code BattleRenderer.renderDrones}: same
 * crash/visibility gating and fade-alpha (VIS_FADING fade + crash fade-out), same
 * hull sizing and HP-bar geometry. The single units-list scan (live + dead) split
 * into a corpse pass over the crash store and a live pass over the registry — a
 * crashing drone is an entity with a {@code CrashingComponent} component and no live
 * registry row, so neither pass needs the legacy units list.
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
        UnitRegistry registry = ctx.sim.getUnitRegistry();
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        float pxH = Drone.VISUAL_CELLS * cellPx;
        float pxW = pxH * cache.aspect;
        float barW = cellPx * 0.9f;

        // CrashingComponent (dead, falling) drones first so their fading wrecks paint
        // UNDER the live drones. A dead drone is an entity that no longer sits in
        // the live registry but still carries a CrashingComponent component — read that
        // store directly (no units-list scan, no Entity handle): the component owns
        // the body the wreck tracks and the timer that fades it out. No vision
        // gate, matching the legacy pass (a crash is always shown).
        ComponentStore<CrashingComponent> crashStore = ctx.sim.getCrashing();
        for (Map.Entry<Long, CrashingComponent> e : crashStore.entries()) {
            CrashingComponent crash = e.getValue();
            AirBody body = crash.body;
            float t = Math.max(0f, Math.min(1f, crash.timer / Drone.CRASH_DURATION_SEC));
            float drawAlpha = alphaMult * t;
            float cx = cam.cellToScreenX(body.x);
            float cy = cam.cellToScreenY(body.y);
            out.addSprite(RenderLayer.DRONES, cache.sprite,
                    cx, cy, pxW, pxH, body.facingDegrees,
                    1f, 1f, 1f, drawAlpha);
        }

        // Live drones — iterate the dense registry; the corpse never appears.
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            Entity u = ctx.sim.liveUnitAt(i);
            if (!(u instanceof Drone)) continue;
            Drone d = (Drone) u;
            byte uv = vis.getUnitVisibility(i);
            if (uv == VisionService.VIS_HIDDEN) continue;

            float cx = cam.cellToScreenX(d.body.x);
            float cy = cam.cellToScreenY(d.body.y);
            float drawAlpha = alphaMult;
            if (uv == VisionService.VIS_FADING) {
                drawAlpha *= vis.getFadeAlpha(i);
            }

            out.addSprite(RenderLayer.DRONES, cache.sprite,
                    cx, cy, pxW, pxH, d.body.facingDegrees,
                    1f, 1f, 1f, drawAlpha);

            float barY = cy + pxH / 2f + BattleRenderer.HP_BAR_GAP;
            HpBarDecor.emit(out, RenderLayer.DRONES, cx, barY, barW,
                    registry.getHp(i) / registry.getMaxHp(i), drawAlpha);
        }
    }
}
