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
    // The Hegemony APC's six tyres (art-source/vehicles/apc/hegemony): the straight middle of each tyre's outer half,
    // repeating its block tread every 17 pixels.
    private static final Band[] APC = {
            new Band(185, 275, 119, 28, 188, 17),
            new Band(372, 465, 119, 28, 375, 17),
            new Band(570, 668, 119, 28, 573, 17),
            new Band(185, 275, 431, 28, 188, 17),
            new Band(372, 465, 431, 28, 375, 17),
            new Band(570, 668, 431, 28, 573, 17)
    };

    // The medium tank's tracks (art-source/vehicles/medium-tank/pack.py): each side's links show past its armour
    // skirts in a forward and an aft straight run, repeating every 21 pixels.
    private static final Band[] MEDIUM = {
            new Band(161, 359, 116, 9, 163, 21),
            new Band(534, 662, 116, 27, 536, 21),
            new Band(161, 359, 451, 10, 163, 21),
            new Band(534, 662, 434, 26, 536, 21)
    };

    static void collect(VehicleType type, UnitSpriteCache cache, GroundBody body,
                        BattleCamera cam, float alpha, DrawList out) {
        // Image top becomes the vehicle's right side when the left-facing sprite points north.
        float cellsPerPixel = type.drawnLengthCells / 768f;
        float centerY = cache.frames.frames[0].h * .5f;
        for (Band band : bands(type)) {
            double lateral = (centerY - band.y - band.height * .5f) * cellsPerPixel;
            double travel = body.visualTravelCells + body.visualYawRadians * lateral;
            band(type, cache, body, cam, alpha, out, band, cellsPerPixel, centerY, travel);
        }
    }

    private static Band[] bands(VehicleType type) {
        if (type.isApc()) return APC;
        if (type == VehicleType.DOZER_BREACHER) return DOZER;
        return type == VehicleType.MEDIUM_TANK ? MEDIUM : TANK;
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
