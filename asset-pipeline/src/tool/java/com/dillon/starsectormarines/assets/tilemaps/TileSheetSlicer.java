package com.dillon.starsectormarines.assets.tilemaps;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-time duplicate of {@code battle.world.tiles.SpriteSheetSlicer}'s
 * auto-strip algorithm (alpha-threshold column scan + min-gap grouping,
 * single-row sheets only), vendored here because {@code :asset-pipeline}'s
 * {@code tool} source set cannot depend on the root module's {@code main}
 * without a project cycle (root depends on {@code :asset-pipeline}, not the
 * other way around).
 *
 * <p>Producing per-cell rects here rather than importing the runtime slicer
 * is what makes the atlas bake match: given the same albedo PNG and the same
 * {@code alphaThreshold}/{@code minGap} (both read from the sheet's own
 * {@code .tileset.json} {@code "slice"} block), this returns the identical
 * pixel rects {@code SpriteSheetSlicer} would at runtime. Keep the two in
 * lockstep if the runtime algorithm ever changes.
 */
final class TileSheetSlicer {

    private TileSheetSlicer() {
    }

    static List<AtlasTileMapDeriver.Cell> slice(BufferedImage img, int alphaThreshold, int minGap) {
        int w = img.getWidth();
        int h = img.getHeight();

        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        // Pass 1 — which columns contain any opaque pixel?
        boolean[] colHasContent = new boolean[w];
        for (int y = 0; y < h; y++) {
            int rowStart = y * w;
            for (int x = 0; x < w; x++) {
                int alpha = (pixels[rowStart + x] >>> 24) & 0xFF;
                if (alpha >= alphaThreshold) {
                    colHasContent[x] = true;
                }
            }
        }

        // Pass 2 — group columns into frames, swallowing small transparent gaps.
        List<int[]> ranges = new ArrayList<>(); // each: {startX, endX} inclusive
        int currentStart = -1;
        int gapCount = 0;
        for (int x = 0; x < w; x++) {
            if (colHasContent[x]) {
                if (currentStart < 0) currentStart = x;
                gapCount = 0;
            } else if (currentStart >= 0) {
                gapCount++;
                if (gapCount >= minGap) {
                    ranges.add(new int[]{currentStart, x - gapCount});
                    currentStart = -1;
                    gapCount = 0;
                }
            }
        }
        if (currentStart >= 0) {
            ranges.add(new int[]{currentStart, w - 1 - gapCount});
        }

        // Pass 3 — for each horizontal range, find the vertical extent of its content.
        List<AtlasTileMapDeriver.Cell> cells = new ArrayList<>(ranges.size());
        for (int[] r : ranges) {
            int minY = h;
            int maxY = -1;
            for (int x = r[0]; x <= r[1]; x++) {
                for (int y = 0; y < h; y++) {
                    int alpha = (pixels[y * w + x] >>> 24) & 0xFF;
                    if (alpha >= alphaThreshold) {
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (minY > maxY) { // shouldn't happen — guard against an empty range
                minY = 0;
                maxY = h - 1;
            }
            cells.add(new AtlasTileMapDeriver.Cell(r[0], minY, r[1] - r[0] + 1, maxY - minY + 1));
        }
        return cells;
    }
}
