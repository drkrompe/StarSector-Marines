package com.dillon.starsectormarines.battle.world.tiles;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects sprite frame bounding boxes on a horizontal sheet by scanning for
 * runs of columns that contain opaque pixels. Designed for AI-generated sprite
 * strips where the artist couldn't guarantee uniform slot spacing — equivalent
 * to Unity's "Automatic" sprite slicing for a single-row sheet.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Bulk-read the image's ARGB pixels into an int array.</li>
 *   <li>For each column, flag whether any pixel has alpha ≥ {@link #ALPHA_THRESHOLD}
 *       (ignores anti-alias halos and stray noise).</li>
 *   <li>Walk left-to-right, grouping consecutive content columns into a frame.
 *       Gaps of fewer than {@link #MIN_GAP} transparent columns are treated as
 *       within-sprite negative space (gun barrel, etc.) and don't split frames.</li>
 *   <li>For each detected frame, scan vertically inside its column range to find
 *       its top and bottom row, producing a tight bounding box.</li>
 * </ol>
 *
 * <p>Single-row sheets only for now — multi-row support would mean doing the
 * same scan vertically first, then column-scanning each row band.
 */
public final class SpriteSheetSlicer {

    /** Per-pixel alpha threshold to count as "content" (out of 255). */
    public static final int ALPHA_THRESHOLD = 16;

    /** Minimum number of fully-transparent columns that count as a sprite boundary. */
    public static final int MIN_GAP = 4;

    private SpriteSheetSlicer() {}

    public static SpriteSheetFrames slice(BufferedImage img) {
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
                if (alpha >= ALPHA_THRESHOLD) {
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
                if (gapCount >= MIN_GAP) {
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
        SpriteSheetFrames.Frame[] frames = new SpriteSheetFrames.Frame[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) {
            int[] r = ranges.get(i);
            int minY = h;
            int maxY = -1;
            for (int x = r[0]; x <= r[1]; x++) {
                for (int y = 0; y < h; y++) {
                    int alpha = (pixels[y * w + x] >>> 24) & 0xFF;
                    if (alpha >= ALPHA_THRESHOLD) {
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (minY > maxY) { // shouldn't happen — guard against empty range
                minY = 0;
                maxY = h - 1;
            }
            frames[i] = new SpriteSheetFrames.Frame(
                    r[0], minY,
                    r[1] - r[0] + 1,
                    maxY - minY + 1);
        }

        return new SpriteSheetFrames(w, h, frames);
    }

    /**
     * Cuts a sheet laid out as a grid of {@code cellPx} square cells into one frame per cell, counted along rows. Each
     * frame is its cell cut to its content (pixels at {@link #ALPHA_THRESHOLD} or more), symmetric about the cell's
     * centre, so a frame turned about its middle turns about the centre it was drawn round. An empty cell is a frame
     * of no size.
     */
    public static SpriteSheetFrames sliceGrid(BufferedImage img, int cellPx) {
        int w = img.getWidth();
        int h = img.getHeight();
        int cols = w / cellPx;
        int rows = h / cellPx;
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        SpriteSheetFrames.Frame[] frames = new SpriteSheetFrames.Frame[cols * rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                frames[row * cols + col] = centredCut(pixels, w, col * cellPx, row * cellPx, cellPx);
            }
        }
        return new SpriteSheetFrames(w, h, frames);
    }

    private static SpriteSheetFrames.Frame centredCut(int[] pixels, int sheetWidth, int x0, int y0, int cellPx) {
        int half = cellPx / 2;
        int reachX = 0;
        int reachY = 0;
        for (int y = 0; y < cellPx; y++) {
            int rowStart = (y0 + y) * sheetWidth + x0;
            for (int x = 0; x < cellPx; x++) {
                if (((pixels[rowStart + x] >>> 24) & 0xFF) < ALPHA_THRESHOLD) continue;
                reachX = Math.max(reachX, x < half ? half - x : x + 1 - half);
                reachY = Math.max(reachY, y < half ? half - y : y + 1 - half);
            }
        }
        return new SpriteSheetFrames.Frame(x0 + half - reachX, y0 + half - reachY, reachX * 2, reachY * 2);
    }
}
