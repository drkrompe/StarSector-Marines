package com.dillon.starsectormarines.assets.tilemaps;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Atlas-aware wrapper around {@link TileMapDerivationKernel}: derives height
 * and normal data per grid/sliced-sheet cell with
 * {@link TileMapDerivationKernel.EdgeMode#CLAMP} addressing (a cell's
 * neighbors in the atlas are not its spatial neighbors, so the kernel's
 * default wrap-at-edges filtering would bleed unrelated tiles into each
 * other), while the percentile normalization window is computed once across
 * every cell's smoothed values so relative height stays comparable sheet-wide
 * — a flat grass tile doesn't get stretched to the full 0-1 range on its own.
 *
 * <p>A cell's derived pixels depend only on that cell's albedo: each cell is
 * blurred and central-differenced independently (clamp-addressed against its
 * own bounds), and the only cross-cell information that flows in is the
 * shared low/high percentile pair used to rescale every cell's already-local
 * height field.
 *
 * <p>Pixels outside every {@link Cell} (gutter space between sliced-sheet
 * frames) are filled with a flat/neutral value — they're not tiles, so
 * there's nothing to derive relief from.
 */
public final class AtlasTileMapDeriver {

    private static final int FLAT_HEIGHT_ARGB = 0xff808080; // mid-gray, height = 0.5
    private static final int FLAT_NORMAL_ARGB = 0xff8080ff; // (0,0,1) OpenGL-convention up

    private AtlasTileMapDeriver() {
    }

    /** One atlas cell's pixel rect on the source sheet, in sheet-pixel coordinates. */
    public record Cell(int x, int y, int width, int height) {

        public Cell {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Cell must have positive extent: " + width + "x" + height);
            }
        }
    }

    /**
     * Resolves the atlas cells of a fixed-{@code cellPx} grid sheet — every
     * {@code (col, row)} in {@code [0, width/cellPx) x [0, height/cellPx)},
     * left-to-right then top-to-bottom.
     */
    public static List<Cell> gridCells(BufferedImage sheet, int cellPx) {
        if (cellPx <= 0) {
            throw new IllegalArgumentException("cellPx must be positive: " + cellPx);
        }
        int cols = sheet.getWidth() / cellPx;
        int rows = sheet.getHeight() / cellPx;
        List<Cell> cells = new ArrayList<>(cols * rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cells.add(new Cell(col * cellPx, row * cellPx, cellPx, cellPx));
            }
        }
        return cells;
    }

    /**
     * Resolves the atlas cells of an auto-strip sliced sheet, using the same
     * alpha-threshold/min-gap algorithm the runtime {@code SpriteSheetSlicer}
     * uses (see {@link TileSheetSlicer}).
     */
    public static List<Cell> sliceCells(BufferedImage sheet, int alphaThreshold, int minGap) {
        return TileSheetSlicer.slice(sheet, alphaThreshold, minGap);
    }

    public static TileMapDerivationKernel.Result derive(
            BufferedImage sheet, List<Cell> cells, TileMapDerivationKernel.Recipe recipe) {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(recipe, "recipe");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Atlas derivation needs at least one cell");
        }

        int sheetWidth = sheet.getWidth();
        int sheetHeight = sheet.getHeight();

        // Per-cell smoothed luminance, clamp-addressed so a cell's blur never samples another cell.
        float[][] smoothedPerCell = new float[cells.size()][];
        int pooledSize = 0;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            BufferedImage sub = sheet.getSubimage(cell.x(), cell.y(), cell.width(), cell.height());
            float[] luminance = TileMapDerivationKernel.linearLuminance(sub);
            float[] smoothed = TileMapDerivationKernel.boxBlur(
                luminance, cell.width(), cell.height(), recipe.heightBlurRadius(),
                TileMapDerivationKernel.EdgeMode.CLAMP);
            smoothedPerCell[i] = smoothed;
            pooledSize += smoothed.length;
        }

        // Percentile window pooled from every cell's smoothed values — sheet-wide, not per-cell.
        float[] pooled = new float[pooledSize];
        int offset = 0;
        for (float[] cellValues : smoothedPerCell) {
            System.arraycopy(cellValues, 0, pooled, offset, cellValues.length);
            offset += cellValues.length;
        }
        float[] range = TileMapDerivationKernel.percentileRange(
            pooled, recipe.lowPercentile(), recipe.highPercentile());

        BufferedImage heightOut = flatImage(sheetWidth, sheetHeight, FLAT_HEIGHT_ARGB);
        BufferedImage normalOut = flatImage(sheetWidth, sheetHeight, FLAT_NORMAL_ARGB);

        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            float[] heightField = TileMapDerivationKernel.normalizeWithRange(
                smoothedPerCell[i], range[0], range[1], recipe.heightPolarity());
            BufferedImage cellHeight =
                TileMapDerivationKernel.grayImage(heightField, cell.width(), cell.height());
            BufferedImage cellNormal = TileMapDerivationKernel.normalImage(
                heightField, cell.width(), cell.height(), recipe.normalStrength(),
                TileMapDerivationKernel.EdgeMode.CLAMP);
            paste(heightOut, cellHeight, cell.x(), cell.y());
            paste(normalOut, cellNormal, cell.x(), cell.y());
        }

        return new TileMapDerivationKernel.Result(heightOut, normalOut);
    }

    private static BufferedImage flatImage(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        Arrays.fill(row, argb);
        for (int y = 0; y < height; y++) {
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    /** Bulk row-copy paste — no compositing/interpolation, so cell pixels land byte-identical. */
    private static void paste(BufferedImage dest, BufferedImage src, int destX, int destY) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            src.getRGB(0, y, width, 1, row, 0, width);
            dest.setRGB(destX, destY + y, width, 1, row, 0, width);
        }
    }
}
