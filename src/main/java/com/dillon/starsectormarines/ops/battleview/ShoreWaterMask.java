package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.stream.IntStream;

/**
 * Where the organic shore draws water or its wet bank, at
 * {@link #TEXELS_PER_CELL} texels a cell: {@link WaterShore#fieldAt} baked once
 * per map, one byte a texel, full over open water and ramping linearly from
 * zero at a land cell's centre to half at the {@link WaterShore.Band#WET wet
 * band}'s level. The ramp is that wide because the creep reads its edge off
 * its coverage's gradient over half-cell taps: a ramp as gentle as its own
 * edge between two cells gives one clean lip, where a steep one doubled it. The
 * creep subtracts it from its coverage as it does {@link CliffFaceMask}
 * ({@link CreepLayer}), so its edge follows the contoured waterline instead of
 * the cells' squares, and the wet film shows between the creep and the water.
 */
final class ShoreWaterMask {

    /** Texels per cell, as the face mask's: a shore's corners are cut a fraction of a cell deep. */
    static final int TEXELS_PER_CELL = 4;

    /** Half-width of the ramp about the wet band's level, in field units: from a land centre's -1 up to it. */
    static final double RAMP = 1 + WaterShore.Band.WET.level;

    private final int width, height;
    private final byte[] texels;

    private ShoreWaterMask(int width, int height, byte[] texels) {
        this.width = width;
        this.height = height;
        this.texels = texels;
    }

    /** The mask over a map, or null when the shore is drawn square or the map has no natural water. */
    static ShoreWaterMask of(CellTopology topology) {
        if (!WaterShore.enabled()) return null;
        int cellsW = topology.getWidth(), cellsH = topology.getHeight();
        boolean any = false;
        for (int y = 0; y < cellsH && !any; y++) {
            for (int x = 0; x < cellsW && !any; x++) any = WaterShore.isShoreWater(topology, x, y);
        }
        if (!any) return null;
        int w = cellsW * TEXELS_PER_CELL, h = cellsH * TEXELS_PER_CELL;
        double lo = WaterShore.Band.WET.level - RAMP, span = 2 * RAMP;
        byte[] out = new byte[w * h];
        IntStream.range(0, cellsH).parallel().forEach(cy -> {
            for (int cx = 0; cx < cellsW; cx++) {
                boolean shore = WaterShore.takesPart(topology, cx, cy);
                if (!shore && WaterShore.kind(topology, cx, cy) != WaterShore.WATER) continue;
                for (int sy = 0; sy < TEXELS_PER_CELL; sy++) {
                    int ty = cy * TEXELS_PER_CELL + sy;
                    double wy = (ty + .5) / TEXELS_PER_CELL;
                    for (int sx = 0; sx < TEXELS_PER_CELL; sx++) {
                        int tx = cx * TEXELS_PER_CELL + sx;
                        double cover = 1;
                        if (shore) {
                            double f = WaterShore.fieldAt(topology, (tx + .5) / TEXELS_PER_CELL, wy);
                            cover = Math.max(0, Math.min(1, (f - lo) / span));
                        }
                        out[ty * w + tx] = (byte) Math.round(cover * 255);
                    }
                }
            }
        });
        return new ShoreWaterMask(w, h, out);
    }

    int width() { return width; }
    int height() { return height; }
    byte[] texels() { return texels; }

    /** Water cover at a point (cells), bilinear between texel centres as GL's linear filter reads it; 0..1. */
    float sample(float cellX, float cellY) {
        float u = cellX * TEXELS_PER_CELL - .5f, v = cellY * TEXELS_PER_CELL - .5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        float a = at(x0, y0), b = at(x0 + 1, y0), c = at(x0, y0 + 1), d = at(x0 + 1, y0 + 1);
        return ((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy) / 255f;
    }

    private int at(int x, int y) {
        x = x < 0 ? 0 : Math.min(width - 1, x);
        y = y < 0 ? 0 : Math.min(height - 1, y);
        return texels[y * width + x] & 0xFF;
    }
}
