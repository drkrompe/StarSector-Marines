package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.render2d.BattleCamera;

/** Wrapped source windows over the exposed tread tops; armor and end caps remain fixed. */
final class TankTrackRender {
    private static final float CELLS_PER_PIXEL = VehicleType.BREAKTHROUGH_TANK.visualLengthCells / 768f;
    // Measured in the registered live hull frame. The two strips reuse its painted tread material.
    private static final int START = 88, END = 687, TILE_X = 91, TILE_WIDTH = 108;

    static void collect(UnitSpriteCache cache, GroundBody body, BattleCamera cam, float alpha, DrawList out) {
        // Image top becomes the vehicle's right side when the left-facing sprite points north.
        band(cache, body, cam, alpha, out, 187, 29, body.visualTravelCells + body.visualYawRadians * 1.02);
        band(cache, body, cam, alpha, out, 550, 24, body.visualTravelCells - body.visualYawRadians * 1.00);
    }

    private static void band(UnitSpriteCache cache, GroundBody body, BattleCamera cam,
                             float alpha, DrawList out, int sourceY, int height, double travel) {
        // The upper tread travels forward relative to the hull; left is forward in the atlas.
        int phase = Math.floorMod((int) (Math.floor(travel / CELLS_PER_PIXEL) % TILE_WIDTH), TILE_WIDTH);
        float angle = body.facingDegrees + VehicleType.BREAKTHROUGH_TANK.spriteFacingOffsetDeg;
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        for (int x = START; x < END;) {
            int sourceOffset = (x - START + phase) % TILE_WIDTH;
            int width = Math.min(TILE_WIDTH - sourceOffset, END - x);
            float localX = (x + width * .5f - 384f) * CELLS_PER_PIXEL;
            float localY = (384f - sourceY - height * .5f) * CELLS_PER_PIXEL;
            float worldX = body.x + localX * cos - localY * sin;
            float worldY = body.y + localX * sin + localY * cos;
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    TILE_X + sourceOffset, sourceY, width, height,
                    cam.cellToScreenX(worldX), cam.cellToScreenY(worldY),
                    width * CELLS_PER_PIXEL * cam.cellPxSize(), height * CELLS_PER_PIXEL * cam.cellPxSize(),
                    angle, 1f, 1f, 1f, alpha);
            x += width;
        }
    }
}
