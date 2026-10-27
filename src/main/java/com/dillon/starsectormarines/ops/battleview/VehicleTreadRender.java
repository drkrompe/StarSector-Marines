package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.render2d.BattleCamera;

/** Wrapped source windows over the exposed tread tops; armor and end caps remain fixed. */
final class VehicleTreadRender {
    // Full registered 768-pixel hull frames. Keep the rounded tire ends and armor static.
    private record Band(int start, int end, int y, int height, int tileX, int tileWidth) {}
    private static final Band[] TANK = {
            new Band(88, 687, 187, 29, 91, 108),
            new Band(88, 687, 550, 24, 91, 108)
    };
    // The dozer's 4.6-cell frame carries the same hull drawn smaller, about the frame center.
    private static final Band[] DOZER = scaled(TANK, VehicleType.BREAKTHROUGH_TANK.drawnLengthCells
            / VehicleType.DOZER_BREACHER.drawnLengthCells);
    private static final Band[] APC = {
            new Band(148, 246, 111, 30, 151, 42),
            new Band(344, 444, 111, 30, 347, 42),
            new Band(552, 652, 111, 30, 555, 42),
            new Band(148, 246, 432, 28, 151, 42),
            new Band(344, 444, 432, 28, 347, 42),
            new Band(552, 652, 432, 28, 555, 42)
    };

    static void collect(VehicleType type, UnitSpriteCache cache, GroundBody body,
                        BattleCamera cam, float alpha, DrawList out) {
        // Image top becomes the vehicle's right side when the left-facing sprite points north.
        float cellsPerPixel = type.drawnLengthCells / 768f;
        float centerY = cache.frames.frames[0].h * .5f;
        for (Band band : type.isApc() ? APC : type == VehicleType.DOZER_BREACHER ? DOZER : TANK) {
            double lateral = (centerY - band.y - band.height * .5f) * cellsPerPixel;
            double travel = body.visualTravelCells + body.visualYawRadians * lateral;
            band(type, cache, body, cam, alpha, out, band, cellsPerPixel, centerY, travel);
        }
    }

    private static Band[] scaled(Band[] bands, float scale) {
        Band[] out = new Band[bands.length];
        for (int i = 0; i < bands.length; i++) {
            Band b = bands[i];
            out[i] = new Band(Math.round(384 + (b.start - 384) * scale), Math.round(384 + (b.end - 384) * scale),
                    Math.round(384 + (b.y - 384) * scale), Math.round(b.height * scale),
                    Math.round(384 + (b.tileX - 384) * scale), Math.round(b.tileWidth * scale));
        }
        return out;
    }

    private static void band(VehicleType type, UnitSpriteCache cache, GroundBody body, BattleCamera cam,
                             float alpha, DrawList out, Band band, float cellsPerPixel,
                             float centerY, double travel) {
        // The upper tread travels forward relative to the hull; left is forward in the atlas.
        int phase = Math.floorMod((int) (Math.floor(travel / cellsPerPixel) % band.tileWidth), band.tileWidth);
        float angle = body.facingDegrees + type.spriteFacingOffsetDeg;
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        for (int x = band.start; x < band.end;) {
            int sourceOffset = (x - band.start + phase) % band.tileWidth;
            int width = Math.min(band.tileWidth - sourceOffset, band.end - x);
            float localX = (x + width * .5f - 384f) * cellsPerPixel;
            float localY = (centerY - band.y - band.height * .5f) * cellsPerPixel;
            float worldX = body.x + localX * cos - localY * sin;
            float worldY = body.y + localX * sin + localY * cos;
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    band.tileX + sourceOffset, band.y, width, band.height,
                    cam.cellToScreenX(worldX), cam.cellToScreenY(worldY),
                    width * cellsPerPixel * cam.cellPxSize(), band.height * cellsPerPixel * cam.cellPxSize(),
                    angle, 1f, 1f, 1f, alpha);
            x += width;
        }
    }
}
