package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.stream.IntStream;

/**
 * Where the organic shore draws water or its wet bank, and where it draws a
 * ford's bed, at {@link #TEXELS_PER_CELL} texels a cell, baked once per map
 * from {@link WaterShore#fieldAt}: two bytes a texel, ford then water.
 *
 * <p><b>Water</b> is full over open water and ramps linearly from zero at a
 * land cell's centre to half at the {@link WaterShore.Band#WET wet band}'s
 * level. The ramp is that wide because the creep reads its edge off its
 * coverage's gradient over half-cell taps: a ramp as gentle as its own edge
 * between two cells gives one clean lip, where a steep one doubled it. The
 * creep subtracts it from its coverage as it does {@link CliffFaceMask}
 * ({@link CreepLayer}), so its edge follows the contoured waterline instead of
 * the cells' squares, and the wet film shows between the creep and the water.
 *
 * <p><b>Ford</b> is how much of the point is a ford's bed: where the wet field
 * (a ford is water) and the deep field (a ford is ground) disagree, faded out
 * where the deep field reaches the open water's level. It is zero on every
 * bank without shallows, where the two fields are one. Over it the creep lays
 * faint veins and a film where it stands, as over a cliff face, and never its
 * body.
 */
final class ShoreWaterMask {

    /** Texels per cell, as the face mask's: a shore's corners are cut a fraction of a cell deep. */
    static final int TEXELS_PER_CELL = 4;

    /** Half-width of the ramp about the wet band's level, in field units: from a land centre's -1 up to it. */
    static final double RAMP = 1 + WaterShore.Band.WET.level;

    /** Half-width of the deep field's fade about the open water's level, in field units. */
    static final double DEEP_RAMP = 0.5;

    private final int width, height;
    /** Per texel: ford cover, then water cover, as 0..255. */
    private final byte[] texels;

    private ShoreWaterMask(int width, int height, byte[] texels) {
        this.width = width;
        this.height = height;
        this.texels = texels;
    }

    /** The mask over a map, or null when the map has no natural water. */
    static ShoreWaterMask of(CellTopology topology) {
        int cellsW = topology.getWidth(), cellsH = topology.getHeight();
        boolean any = false;
        for (int y = 0; y < cellsH && !any; y++) {
            for (int x = 0; x < cellsW && !any; x++) any = WaterShore.isShoreWater(topology, x, y);
        }
        if (!any) return null;
        int w = cellsW * TEXELS_PER_CELL, h = cellsH * TEXELS_PER_CELL;
        double lo = WaterShore.Band.WET.level - RAMP, span = 2 * RAMP;
        double deepLo = WaterShore.Band.WATER.level - DEEP_RAMP, deepSpan = 2 * DEEP_RAMP;
        byte[] out = new byte[w * h * 2];
        IntStream.range(0, cellsH).parallel().forEach(cy -> {
            for (int cx = 0; cx < cellsW; cx++) {
                boolean shore = WaterShore.takesPart(topology, cx, cy);
                if (!shore && WaterShore.kind(topology, cx, cy) != WaterShore.WATER) continue;
                for (int sy = 0; sy < TEXELS_PER_CELL; sy++) {
                    int ty = cy * TEXELS_PER_CELL + sy;
                    double wy = (ty + .5) / TEXELS_PER_CELL;
                    for (int sx = 0; sx < TEXELS_PER_CELL; sx++) {
                        int tx = cx * TEXELS_PER_CELL + sx;
                        double wx = (tx + .5) / TEXELS_PER_CELL;
                        double cover = 1, ford = 0;
                        if (shore) {
                            double wet = WaterShore.fieldAt(topology, wx, wy);
                            double deep = WaterShore.fieldAt(topology, wx, wy, true);
                            cover = clamp01((wet - lo) / span);
                            ford = clamp01((wet - deep) / 2) * (1 - clamp01((deep - deepLo) / deepSpan));
                        }
                        int at = (ty * w + tx) * 2;
                        out[at] = (byte) Math.round(ford * 255);
                        out[at + 1] = (byte) Math.round(cover * 255);
                    }
                }
            }
        });
        return new ShoreWaterMask(w, h, out);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    int width() { return width; }
    int height() { return height; }
    /** Two bytes a texel, ford then water: luminance-alpha as GL uploads it. */
    byte[] texels() { return texels; }

    /** Water cover at a point (cells), bilinear between texel centres as GL's linear filter reads it; 0..1. */
    float sample(float cellX, float cellY) {
        return bilinear(cellX, cellY, 1);
    }

    /** Ford cover at a point (cells), bilinear as {@link #sample}; 0..1. */
    float sampleFord(float cellX, float cellY) {
        return bilinear(cellX, cellY, 0);
    }

    private float bilinear(float cellX, float cellY, int channel) {
        float u = cellX * TEXELS_PER_CELL - .5f, v = cellY * TEXELS_PER_CELL - .5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        float a = at(x0, y0, channel), b = at(x0 + 1, y0, channel);
        float c = at(x0, y0 + 1, channel), d = at(x0 + 1, y0 + 1, channel);
        return ((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy) / 255f;
    }

    private int at(int x, int y, int channel) {
        x = x < 0 ? 0 : Math.min(width - 1, x);
        y = y < 0 ? 0 : Math.min(height - 1, y);
        return texels[(y * width + x) * 2 + channel] & 0xFF;
    }
}
