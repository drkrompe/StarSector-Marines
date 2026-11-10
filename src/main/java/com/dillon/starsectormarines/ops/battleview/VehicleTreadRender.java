package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.TrailerKinematic;
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

    // The midline carriers' tyres (art-source/vehicles/midline-carriers/treads.py, which measures them off the packed
    // sheets): the straight middle of each tyre's outer half on the live hull frame, its segment and its lugs' repeat.
    private record TyreBand(int segment, int start, int end, int y, int height, int tileX, int tileWidth) {}
    private static final TyreBand[] LIGHT_CARRIER = {
            new TyreBand(0, 90, 147, 29, 31, 90, 18),
            new TyreBand(0, 91, 146, 251, 33, 91, 18),
            new TyreBand(1, 369, 427, 26, 38, 369, 16),
            new TyreBand(1, 368, 427, 248, 37, 368, 17)
    };
    private static final TyreBand[] MEDIUM_CARRIER = {
            new TyreBand(0, 94, 147, 29, 43, 94, 20),
            new TyreBand(0, 94, 149, 287, 47, 94, 22),
            new TyreBand(1, 366, 427, 24, 55, 366, 22),
            new TyreBand(1, 368, 426, 289, 49, 368, 23),
            new TyreBand(1, 554, 613, 24, 49, 554, 22),
            new TyreBand(1, 555, 612, 285, 53, 555, 22)
    };
    private static final TyreBand[] HEAVY_CARRIER = {
            new TyreBand(0, 92, 154, 33, 43, 92, 20),
            new TyreBand(0, 93, 154, 344, 49, 93, 22),
            new TyreBand(1, 334, 393, 33, 44, 334, 20),
            new TyreBand(1, 332, 391, 341, 51, 332, 22),
            new TyreBand(2, 570, 631, 24, 36, 570, 19),
            new TyreBand(2, 568, 629, 353, 47, 568, 20),
            new TyreBand(2, 763, 823, 24, 37, 763, 19),
            new TyreBand(2, 763, 824, 352, 48, 763, 20)
    };

    private static TyreBand[] tyres(VehicleType type) {
        return switch (type) {
            case LIGHT_CARRIER -> LIGHT_CARRIER;
            case MEDIUM_CARRIER -> MEDIUM_CARRIER;
            case HEAVY_CARRIER -> HEAVY_CARRIER;
            default -> new TyreBand[0];
        };
    }

    /**
     * An articulated carrier's tyres on segment {@code segment}, rolling with the hull's travel as the tanks' treads
     * do, each drawn on the module it is bolted to as that module bends ({@link TrailerKinematic#toWorld}).
     */
    static void collectCarrierSegment(VehicleType type, UnitSpriteCache cache, TrailerKinematic train, int segment,
                                      GroundBody body, BattleCamera cam, float alpha, DrawList out, float[] point) {
        var hull = cache.frames.frames[0];
        float cellsPerPixel = type.drawnLengthCells / hull.w;
        float angle = train.headingDeg(segment) + type.spriteFacingOffsetDeg;
        float px = cellsPerPixel * cam.cellPxSize();
        for (TyreBand tyre : tyres(type)) {
            if (tyre.segment != segment) continue;
            // The frame's top is the hull's right, +x; its left is the nose, +y.
            float lateral = (hull.h * .5f - tyre.y - tyre.height * .5f) * cellsPerPixel;
            double travel = body.visualTravelCells + body.visualYawRadians * lateral;
            int phase = Math.floorMod((int) (Math.floor(travel / cellsPerPixel) % tyre.tileWidth), tyre.tileWidth);
            for (int x = tyre.start; x < tyre.end;) {
                int sourceOffset = (x - tyre.start + phase) % tyre.tileWidth;
                int width = Math.min(tyre.tileWidth - sourceOffset, tyre.end - x);
                float along = (hull.w * .5f - x - width * .5f) * cellsPerPixel;
                train.toWorld(body.x, body.y, segment, lateral, along, point);
                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        hull.x + tyre.tileX + sourceOffset, hull.y + tyre.y, width, tyre.height,
                        cam.cellToScreenX(point[0]), cam.cellToScreenY(point[1]), width * px, tyre.height * px,
                        angle, 1f, 1f, 1f, alpha);
                x += width;
            }
        }
    }

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
