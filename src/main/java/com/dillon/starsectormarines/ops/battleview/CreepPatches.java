package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;

/**
 * A frame's creep on stood-up walls ({@link CreepClimb}), as textured patches of the creep sheet: each its four corners
 * on screen (foot or outer side first, along, then top or inner side back), its strip and its place on it, its light
 * and alpha. Held until the walls they lie on have been drawn, then emitted over them. GL-free.
 */
final class CreepPatches {

    private static final int STRIDE = 16;
    private float[] data = new float[STRIDE * 64];
    private int count;

    void reset() {
        count = 0;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /**
     * One patch: corners {@code (x0,y0)} and {@code (x1,y1)} at {@code v1} (the foot, or the top's outer side) and
     * {@code (x2,y2)}, {@code (x3,y3)} at {@code v0}, {@code u0} at the first and last corner and {@code u1} at the
     * others; {@code u}, {@code v} in sheet cells on strip {@code row}, {@code v} down from the strip's top.
     */
    void add(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int row, float u0,
             float u1, float v0, float v1, float light, float alpha) {
        if (alpha <= .004f) return;
        if (data.length < (count + 1) * STRIDE) data = Arrays.copyOf(data, data.length * 2);
        int o = count++ * STRIDE;
        data[o] = x0; data[o + 1] = y0; data[o + 2] = x1; data[o + 3] = y1;
        data[o + 4] = x2; data[o + 5] = y2; data[o + 6] = x3; data[o + 7] = y3;
        data[o + 8] = row; data[o + 9] = u0; data[o + 10] = u1; data[o + 11] = v0; data[o + 12] = v1;
        data[o + 13] = light; data[o + 14] = alpha;
    }

    /**
     * Emits every patch into {@code layer} from {@code sheet}: each over its bounding box, its corners and their sheet
     * places as the patch's sixteen floats, and a seventeenth asking a review frame to map the places rather than clip
     * the region (GL maps them either way). The light is quantised so a review frame's tinted cuts stay few.
     */
    void emit(DrawList out, RenderLayer layer, SpriteAPI sheet) {
        if (sheet == null || count == 0) return;
        int stripPx = CreepClimb.SHEET_CELLS_UP * CreepClimb.SHEET_CELL_PX;
        int sheetW = CreepClimb.SHEET_CELLS_ALONG * CreepClimb.SHEET_CELL_PX;
        for (int i = 0; i < count; i++) {
            int o = i * STRIDE;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int k = 0; k < 8; k += 2) {
                minX = Math.min(minX, data[o + k]); maxX = Math.max(maxX, data[o + k]);
                minY = Math.min(minY, data[o + k + 1]); maxY = Math.max(maxY, data[o + k + 1]);
            }
            float w = maxX - minX, h = maxY - minY;
            if (w < .25f || h < .25f) continue;
            float u0 = data[o + 9] / CreepClimb.SHEET_CELLS_ALONG, u1 = data[o + 10] / CreepClimb.SHEET_CELLS_ALONG;
            // A patch's v runs up its strip: 1 at the strip's top, 0 at its foot.
            float top = 1f - data[o + 11] / CreepClimb.SHEET_CELLS_UP, foot = 1f - data[o + 12] / CreepClimb.SHEET_CELLS_UP;
            float[] patch = {
                    (data[o] - minX) / w, (data[o + 1] - minY) / h, (data[o + 2] - minX) / w, (data[o + 3] - minY) / h,
                    (data[o + 4] - minX) / w, (data[o + 5] - minY) / h, (data[o + 6] - minX) / w, (data[o + 7] - minY) / h,
                    u0, foot, u1, foot, u1, top, u0, top, 1f};
            float light = Math.round(Math.max(0f, Math.min(1f, data[o + 13])) * 32f) / 32f;
            out.addSheetPatch(layer, sheet, 0, (int) data[o + 8] * stripPx, sheetW, stripPx,
                    (minX + maxX) / 2f, (minY + maxY) / 2f, w, h, light, light, light, data[o + 14], patch);
        }
    }
}
