package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What can be read off a raw sheet's pixels, and nothing else.
 *
 * <p>It answers how big the sheet is, whether it carries a usable alpha channel,
 * and how many pieces alpha-keying finds at each of a few thresholds. This is
 * the only implementation: a {@code measure_sheet.py} answered the same
 * questions until 2026-08-28 and was deleted rather than kept in step, because
 * two measurements that disagree produce a committed seed whose numbers do not
 * match what the tool reports.
 *
 * <p>Counting pieces here calls the same {@link SheetSlicer} the authoring page
 * cuts with, so the reported count is what slicing will actually find rather
 * than a second opinion about it.
 *
 * <p><b>It does not detect the cell grid, and must not learn to.</b> Seam energy
 * and autocorrelation were both measured against sheets whose grids were known
 * and both read noise — generated tile art blends across its own boundaries, so
 * the grid is something the generator was told and the pixels do not say. A
 * stated grid is verified here; an unstated one stays unstated.
 *
 * <p>The judged half — what the sheet is for, what warning the next reader
 * needs, which blocks it contains — is deliberately absent. A tool that guesses
 * at it produces confident nonsense, and the note is the one field of a seed
 * that justifies the whole ingest procedure.
 */
public final class SheetMeasurement {

    private SheetMeasurement() {}

    /** Thresholds swept so a caller can see where a sheet stops fusing. */
    public static final List<Integer> SWEEP = List.of(16, 40, 96, 160);

    /**
     * A sheet's measurable facts.
     *
     * @param hasAlpha whether the sheet is keyed at all — RGBA does not mean keyed,
     *                 since a fully opaque RGBA sheet slices exactly like an RGB one
     * @param piecesByThreshold how many pieces each swept threshold finds, empty when unkeyed
     */
    public record Measurement(int width, int height, boolean hasAlpha,
                              double notFullyOpaqueFraction,
                              Map<Integer, Integer> piecesByThreshold) {

        /** The threshold that separates the most pieces, or the default when none does. */
        public int suggestedAlphaMin() {
            int best = SheetSlicer.DEFAULT_ALPHA_MIN;
            int bestCount = 1;
            for (Map.Entry<Integer, Integer> found : piecesByThreshold.entrySet()) {
                if (found.getValue() > bestCount) {
                    bestCount = found.getValue();
                    best = found.getKey();
                }
            }
            return best;
        }

        /** How many pieces {@link #suggestedAlphaMin} finds. */
        public int suggestedPieceCount() {
            return piecesByThreshold.getOrDefault(suggestedAlphaMin(), 1);
        }
    }

    /** A stated plate layout checked against the sheet it is stated for. */
    public record Grid(int cols, int rows, double cellPxX, double cellPxY) {

        /** Within a pixel. Non-square cells are ordinary for generated art, not an error. */
        public boolean square() {
            return Math.abs(cellPxX - cellPxY) <= 1.0;
        }
    }

    public static Measurement measure(BufferedImage sheet) {
        BufferedImage argb = TilesetOperations.toArgb(sheet);
        int width = argb.getWidth();
        int height = argb.getHeight();
        long notFullyOpaque = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((argb.getRGB(x, y) >>> 24) < 255) notFullyOpaque++;
            }
        }
        double fraction = notFullyOpaque / (double) Math.max(1, (long) width * height);
        // A handful of soft pixels at the border of an otherwise opaque sheet
        // is not a key.
        boolean hasAlpha = fraction > 0.001;

        Map<Integer, Integer> pieces = new LinkedHashMap<>();
        if (hasAlpha) {
            for (int threshold : SWEEP) {
                pieces.put(threshold, SheetSlicer
                        .slice(argb, threshold, SheetSlicer.DEFAULT_MIN_AREA).size());
            }
        }
        return new Measurement(width, height, hasAlpha, fraction, pieces);
    }

    public static Grid statedGrid(Measurement measurement, int cols, int rows) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("a grid needs at least one cell: "
                    + cols + "x" + rows);
        }
        return new Grid(cols, rows,
                measurement.width() / (double) cols,
                measurement.height() / (double) rows);
    }

    /**
     * The report, in the terms the ingest skill's table uses, so a reader can
     * match what they see here to the row that says how to annotate it.
     */
    public static String report(String sheetName, Measurement measurement, Grid grid) {
        StringBuilder out = new StringBuilder();
        out.append(sheetName).append(": ")
                .append(measurement.width()).append('x').append(measurement.height()).append('\n');
        if (measurement.hasAlpha()) {
            out.append(String.format("  alpha channel: yes (%.1f%% of pixels not fully opaque)%n",
                    measurement.notFullyOpaqueFraction() * 100));
            StringBuilder sweep = new StringBuilder();
            for (Map.Entry<Integer, Integer> found : measurement.piecesByThreshold().entrySet()) {
                if (sweep.length() > 0) sweep.append("; ");
                sweep.append(found.getKey()).append(": ").append(found.getValue());
            }
            out.append("  pieces by alpha threshold - ").append(sweep).append('\n');
            out.append("  -> a cut-out sheet. Pick the threshold where the count stops\n");
            out.append("     changing; one piece means the key is too soft to separate them.\n");
        } else {
            out.append("  alpha channel: NO (every pixel fully opaque)\n");
            out.append("  -> a fused plate. Slicing finds one piece covering the whole sheet,\n");
            out.append("     which is correct: select it and split it on the grid.\n");
        }
        if (grid == null) {
            if (!measurement.hasAlpha()) {
                out.append("  grid: NOT STATED. This sheet cannot be cut without one, and it\n");
                out.append("     cannot be read off the pixels - state gridCols and gridRows.\n");
            }
            return out.toString();
        }
        out.append(String.format("  grid: %dx%d stated -> %.1f x %.1f px per cell%n",
                grid.cols(), grid.rows(), grid.cellPxX(), grid.cellPxY()));
        if (!grid.square()) {
            out.append("     Cells are not square, which is ordinary for generated art. The\n");
            out.append("     split divides the plate into exactly the stated grid, so this is\n");
            out.append("     cut the same as any other plate.\n");
        }
        return out.toString();
    }

    /**
     * A seed drafted from the measurement, with the note left explicitly unwritten.
     *
     * <p>The note carries the placeholder rather than being omitted, because
     * {@code ProjectTilesetSeedsTest} fails the build for a seed still carrying
     * it. Making the unfinished state loud is the mechanism that keeps replacing
     * it part of ingestion rather than a thing to remember.
     */
    public static TilesetDocument draftSeed(String sheetRelativePath, String sheetName,
                                            Measurement measurement, Grid grid) {
        TilesetDocument seed = new TilesetDocument();
        seed.sheet = sheetRelativePath;
        seed.sheetName = sheetName;
        seed.idPrefix = "doodad." + sheetName;
        seed.cellPx = 32;
        seed.alphaMin = measurement.hasAlpha()
                ? measurement.suggestedAlphaMin() : SheetSlicer.DEFAULT_ALPHA_MIN;
        seed.gridCols = grid == null ? 1 : grid.cols();
        seed.gridRows = grid == null ? 1 : grid.rows();

        String measured;
        if (measurement.hasAlpha()) {
            measured = measurement.width() + "x" + measurement.height() + ", alpha-keyed; slicing "
                    + "finds " + measurement.suggestedPieceCount() + " pieces at threshold "
                    + seed.alphaMin + ".";
        } else {
            measured = measurement.width() + "x" + measurement.height() + ", no alpha channel, "
                    + "so slicing finds one fused piece covering the whole sheet.";
            if (grid != null) {
                measured += String.format(" Select it and split on the %dx%d grid, whose cells"
                        + " are %.0fx%.0f px.", grid.cols(), grid.rows(),
                        grid.cellPxX(), grid.cellPxY());
            }
        }
        seed.note = "TODO - say what this sheet is for and what the next reader would otherwise "
                + "learn by failing. Measured: " + measured;
        return seed;
    }
}
