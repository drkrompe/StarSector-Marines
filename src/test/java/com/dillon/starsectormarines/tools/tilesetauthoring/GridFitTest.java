package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The measurement that found the bug is the measurement that closes it.
 *
 * <p>{@code urban-tileset} is a real, regular ten-column grid whose lines are
 * roughly 123px apart starting about 8px in. Dividing its 1254px canvas by ten
 * puts them 125.4px apart starting at zero, and the two disagree by up to nine
 * pixels at the ends — enough that the cell cut at column five carried a visible
 * sliver of column six's art. This works against the real sheet on purpose: an
 * arithmetic test on synthetic input would have passed against the broken cut
 * too.
 *
 * <p>The row axis of the same sheet is the counterexample and is pinned as one.
 * Its lower rows are empty, so there are fewer seams to fit and they lie less
 * well on a line; the fit is expected to disown itself rather than be applied.
 */
class GridFitTest {

    private static final Path SHEET =
            Path.of(TilesetLibrary.SOURCE_DIR, "urban-tileset.raw.png");
    private static final int COLS = 10;
    private static final int ROWS = 10;

    /** The nine column boundaries measured on the sheet, for reference in failures. */
    private static final List<Integer> KNOWN_COLUMN_SEAMS =
            List.of(134, 256, 376, 501, 624, 747, 869, 994, 1121);

    private static BufferedImage sheet() throws Exception {
        assertTrue(Files.isRegularFile(SHEET), "the raw sheet should be in the project: " + SHEET);
        return TilesetOperations.toArgb(ImageIO.read(SHEET.toFile()));
    }

    private static GridFit.Measured measured() throws Exception {
        return GridFit.measure(sheet(), GridCut.dividing(1254, 1254, COLS, ROWS));
    }

    @Test
    void theColumnSeamsOfTheSheetAreFoundWhereTheArtChanges() throws Exception {
        GridFit.Axis columns = measured().columns();

        List<Integer> found = new ArrayList<>();
        for (GridFit.Seam seam : columns.seams()) found.add(seam.position());
        assertEquals(KNOWN_COLUMN_SEAMS, found,
                "the column boundaries of urban-tileset are visible in the art and do not move");
        assertEquals(9, columns.strongSeams(),
                "every one of them is a real seam, not a peak in noise");
    }

    @Test
    void theFittedColumnCutLandsOnTheSeamsAndTheCanvasDivisionDoesNot() throws Exception {
        GridFit.Axis columns = measured().columns();
        GridCut divided = GridCut.dividing(1254, 1254, COLS, ROWS);

        double before = columns.worstOffsetFrom(divided.originX(), divided.pitchX());
        double after = columns.worstOffsetFrom(columns.origin(), columns.pitch());

        // Measured against the same seams, so this compares two placements
        // rather than each placement against its own evidence.
        assertTrue(before > 8.5, "dividing the canvas should miss badly: " + before);
        assertTrue(after <= 4.0, "the fitted cut should land on the art: " + after);
        assertTrue(after < before / 2, "the fit should more than halve the error: "
                + before + " -> " + after);
    }

    @Test
    void theFittedColumnAxisIsUsableAndSaysWhereItPutTheGrid() throws Exception {
        GridFit.Axis columns = measured().columns();

        assertTrue(columns.trustworthy(), columns.describe("columns"));
        // Pinned loosely: the numbers are a measurement of art that is not
        // changing, and pinning them exactly would fail on a re-render of the
        // sheet rather than on a regression in the fit.
        assertTrue(Math.abs(columns.pitch() - 123.23) < 0.5,
                "the pitch should be the one the art has: " + columns.pitch());
        assertTrue(Math.abs(columns.origin() - 8.5) < 1.5,
                "the grid starts inside a margin: " + columns.origin());
    }

    @Test
    void anAxisWithTooLittleEvidenceRefusesItselfRatherThanBeingApplied() throws Exception {
        // Rows 8 and 9 of this sheet are empty, so two of the nine row
        // boundaries have no art to sit on and the rest lie less well on a line.
        GridFit.Axis rows = measured().rows();

        assertFalse(rows.trustworthy(), rows.describe("rows"));
        assertTrue(rows.maxResidual() > rows.residualTolerance()
                        || rows.strongSeams() < rows.count() - 1,
                "the refusal has to have a stated cause: " + rows.describe("rows"));
        assertTrue(rows.describe("rows").contains("NOT usable"), rows.describe("rows"));
    }

    @Test
    void applyingAMeasurementTakesOnlyTheAxesThatMeasuredWell() throws Exception {
        GridCut stated = GridCut.dividing(1254, 1254, COLS, ROWS);

        GridCut applied = measured().appliedTo(stated);

        assertTrue(applied.originX() > 1, "the column axis fitted and should be taken");
        assertEquals(stated.originY(), applied.originY(),
                "the row axis did not fit, so it keeps the cut it came in with");
        assertEquals(stated.pitchY(), applied.pitchY());
        assertEquals(COLS, applied.cols(), "a measurement never revises a stated count");
        assertEquals(ROWS, applied.rows());
    }

    @Test
    void theCheckedInCutIsTheOneTheArtHas() throws Exception {
        // The document is the durable answer, so it is what has to be right;
        // the fit above only says how it was arrived at.
        TilesetDocument document =
                TilesetDocument.read(TilesetDocument.pathFor(Path.of("."), "urban-tileset"));
        GridFit.Axis columns = measured().columns();
        GridCut cut = document.cut(1254, 1254);

        assertTrue(columns.worstOffsetFrom(cut.originX(), cut.pitchX()) <= 4.0,
                "the cut saved for urban-tileset should sit on the art's own column seams");
        for (TilesetExport.Entry entry : document.entries) {
            if (!entry.id.equals(TilesetOperations.gridId(document.idPrefix, 5, 0))) continue;
            assertEquals(cut.cell(5, 0), entry.piece,
                    "every cell's rectangle should come from the saved cut");
        }
    }
}
