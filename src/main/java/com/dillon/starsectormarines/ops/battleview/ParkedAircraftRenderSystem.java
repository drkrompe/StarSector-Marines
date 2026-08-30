package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Emits the {@link RenderLayer#VEHICLES} layer — parked aircraft resting on
 * their berths, drawn at their hull's authored length and pivot. Sits above
 * ground/decals and below doodads/units.
 *
 * <p>Parked road vehicles used to share this layer as a second, parallel prop
 * model with their own list, sheet cache, and footprint stamp. They are
 * ordinary registry doodads now and draw in {@link RenderLayer#DOODADS} with
 * every other prop, which is why this system carries aircraft alone.
 */
public final class ParkedAircraftRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public ParkedAircraftRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.VEHICLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<ParkedAircraft> aircraft = ctx.sim.getParkedAircraft();
        if (aircraft.isEmpty()) return;

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (ParkedAircraft parked : aircraft) {
            ShuttleSpriteCache cache = sprites.airframeSprites().get(parked.type);
            if (cache == null || cache.sprite == null) continue;

            float hullLenCells = HullFootprintResolver.visualLengthCells(
                    parked.type.renderHullId());
            float pxLen = hullLenCells * cellPx;
            float[] pivot = HullPivotResolver.pivotOffset(parked.type.renderHullId());
            float rad = (float) Math.toRadians(parked.facingDegrees);
            float c = (float) Math.cos(rad);
            float s = (float) Math.sin(rad);
            float cx = cam.cellToScreenX(parked.centerX + 0.5f
                    + pivot[0] * c - pivot[1] * s);
            float cy = cam.cellToScreenY(parked.centerY + 0.5f
                    + pivot[0] * s + pivot[1] * c);
            out.addSprite(RenderLayer.VEHICLES, cache.sprite,
                    cx, cy, pxLen * cache.aspect, pxLen,
                    parked.facingDegrees,
                    1f, 1f, 1f, alphaMult);
        }
    }
}
