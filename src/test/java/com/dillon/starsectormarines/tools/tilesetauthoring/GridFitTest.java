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
 * <p>{@code urban-tileset} is a real, regular ten-column grid that separates its
 * cells with a dark gutter roughly seven pixels wide, and its cell pitch is
 * about 123.25px starting about 2.3px in. Two placements have been wrong here in
 * turn and each was found by eye first. Dividing its 1254px canvas by ten put
 * the boundaries 125.4px apart from zero, up to seventeen pixels away from the
 * gaps. Fitting them to the strongest change then put them on a gutter's
 * <em>edge</em> — the boundary of the neighbouring cell's art — which is five
 * pixels inside cell six at the 5|6 boundary and still a visible sliver.
 *
 * <p>So the assertions below are about the gap rather than about a residual: a
 * column boundary is right when it lands between the two cells, and the numbers
 * that say so are the pixel coordinates of the gutter measured off the sheet.
 * This works against the real sheet on purpose; an arithmetic test on synthetic
 * input would have passed against both broken cuts.
 *
 * <p>The sheets that do not fit are pinned as counterexamples. They are not
 * regular grids at their stated counts — the row axis of this very sheet has
 * empty lower rows, and the prop strips have frames of differing widths — and a
 * change to the fit that makes any of them pass has broken the refusal rather
 * than rescued the sheet.
 */
class GridFitTest {

    private static final Path SHEET =
            Path.of(TilesetLibrary.SOURCE_DIR, "urban-tileset.raw.png");
    private static final int COLS = 10;
    private static final int ROWS = 10;

    /**
     * The nine column gutters measured on the sheet, for reference in failures.
     *
     * <p>The last one sits in a flat-bottomed trough — the mean column
     * brightness runs {@code 18.6 20.7 18.3 20.3 18.3 18.4 20.9 18.8} across
     * x=1112..1119 — so which pixel of it scores highest is decided by tenths
     * and moved from 1116 to 1114 when the sheet was given its alpha. Both are
     * the same gap. The other eight did not move.
     */
    private static final List<Integer> KNOWN_COLUMN_GUTTERS =
            List.of(127, 249, 372, 496, 617, 740, 864, 986, 1114);

    /**
     * The gap between cells five and six, in sheet pixels.
     *
     * <p>Read off the mean column brightness, which runs
     * {@code … 60 32 8 16 8 4 11 7 11 23 …} across x=735..744: the art either
     * side sits near 50 and the gap bottoms out below 10. The boundary belongs
     * in there, and both of the placements this test exists to rule out — 752
     * from dividing the canvas, 748 from fitting to the strongest change — sit
     * outside it, inside cell six's art.
     */
    private static final int GUTTER_5_6_FROM = 737;
    private static final int GUTTER_5_6_TO = 743;

    private static BufferedImage sheet() throws Exception {
        assertTrue(Files.isRegularFile(SHEET), "the raw sheet should be in the project: " + SHEET);
        return TilesetOperations.toArgb(ImageIO.read(SHEET.toFile()));
    }

    private static GridFit.Measured measured() throws Exception {
        return GridFit.measure(sheet(), GridCut.dividing(1254, 1254, COLS, ROWS));
    }

    @Test
    void theColumnBoundariesOfTheSheetAreFoundInItsGutters() throws Exception {
        GridFit.Axis columns = measured().columns();

        assertEquals(GridFit.Boundary.GUTTER, columns.onto(),
                "this plate leaves a gap between its cells, so that is what to fit");
        List<Integer> found = new ArrayList<>();
        for (GridFit.Seam seam : columns.seams()) found.add(seam.position());
        assertEquals(KNOWN_COLUMN_GUTTERS, found,
                "the column gutters of urban-tileset are visible in the art and do not move");
        assertEquals(9, columns.strongSeams(),
                "every one of them is a real gap, not a dip inside a cell's own art");
    }

    @Test
    void theFittedColumnCutPutsTheFifthBoundaryInsideTheGutterAndNotOnItsEdge() throws Exception {
        GridFit.Axis columns = measured().columns();

        GridCut fitted = GridCut.dividing(1254, 1254, COLS, ROWS)
                .withColumnAxis(columns.origin(), columns.pitch());
        int boundary = (int) Math.round(fitted.columnLine(6));

        assertTrue(boundary >= GUTTER_5_6_FROM && boundary <= GUTTER_5_6_TO,
                "the 5|6 boundary should sit in the gap between the two cells (x "
                        + GUTTER_5_6_FROM + ".." + GUTTER_5_6_TO + "), not on the edge of "
                        + "either one's art: " + boundary);
        assertEquals(boundary, fitted.cell(6, 0).x(),
                "cell six starts at the boundary, so it carries no sliver of cell five");
        assertEquals(boundary - 1, fitted.cell(5, 0).right(),
                "and cell five ends just before it, so the two meet with no gap");
    }

    @Test
    void theCanvasDivisionMissesTheGuttersEntirely() throws Exception {
        GridFit.Axis columns = measured().columns();
        GridCut divided = GridCut.dividing(1254, 1254, COLS, ROWS);

        double before = columns.worstOffsetFrom(divided.originX(), divided.pitchX());
        double after = columns.worstOffsetFrom(columns.origin(), columns.pitch());

        // Measured against the same gutters, so this compares two placements
        // rather than each placement against its own evidence.
        assertTrue(before > 8.5, "dividing the canvas should miss badly: " + before);
        assertTrue(after <= columns.residualTolerance(),
                "the fitted cut should land in the gaps: " + after);
        assertTrue(after < before / 2, "the fit should more than halve the error: "
                + before + " -> " + after);
    }

    @Test
    void theFittedColumnAxisIsUsableAndSaysWhereItPutTheGrid() throws Exception {
        GridFit.Axis columns = measured().columns();

        assertTrue(columns.trustworthy(), columns.describe("columns"));
        assertTrue(columns.describe("columns").contains("gutters"), columns.describe("columns"));
        // Pinned loosely: the numbers are a measurement of art that is not
        // changing, and pinning them exactly would fail on a re-render of the
        // sheet rather than on a regression in the fit.
        assertTrue(Math.abs(columns.pitch() - 123.25) < 0.5,
                "the pitch should be the one the art has: " + columns.pitch());
        assertTrue(Math.abs(columns.origin() - 2.31) < 1.5,
                "the grid starts inside a margin: " + columns.origin());
    }

    @Test
    void anAxisWithTooLittleEvidenceRefusesItselfRatherThanBeingApplied() throws Exception {
        // Rows 8 and 9 of this sheet are empty. There is no art bounding a gap
        // down there, so the gutter fit finds nothing to hold onto and the axis
        // falls back to the change in the art — which those rows also lack.
        GridFit.Axis rows = measured().rows();

        assertEquals(GridFit.Boundary.CHANGE, rows.onto(),
                "an empty region is uniformly dark, which is not a gutter");
        assertFalse(rows.trustworthy(), rows.describe("rows"));
        assertTrue(rows.maxResidual() > rows.residualTolerance()
                        || rows.strongSeams() < rows.count() - 1,
                "the refusal has to have a stated cause: " + rows.describe("rows"));
        assertTrue(rows.describe("rows").contains("NOT usable"), rows.describe("rows"));
    }

    @Test
    void theSheetsThatAreNotRegularGridsStayRefused() throws Exception {
        // Fitting to gutters rescues none of these, and it must not: a plate of
        // props whose frames differ in width has no single pitch to find, gutters
        // or no gutters. Anything here turning usable is a hole in the gate.
        for (String name : List.of("Floors_Tiles", "Water_tiles", "nature-tiles",
                "urban-tileset-3")) {
            Path root = Path.of(".");
            TilesetDocument document = TilesetDocument.read(TilesetDocument.pathFor(root, name));
            BufferedImage plate = TilesetOperations.readSheet(root, document);
            GridFit.Measured fit = GridFit.measure(plate,
                    document.cut(plate.getWidth(), plate.getHeight()));

            if (document.gridCols > 1) {
                assertFalse(fit.columns().trustworthy(),
                        name + " " + fit.columns().describe("columns"));
            }
            if (document.gridRows > 1) {
                assertFalse(fit.rows().trustworthy(), name + " " + fit.rows().describe("rows"));
            }
        }
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

        assertTrue(columns.worstOffsetFrom(cut.originX(), cut.pitchX())
                        <= columns.residualTolerance(),
                "the cut saved for urban-tileset should sit in the art's own column gutters");
        int boundary = (int) Math.round(cut.columnLine(6));
        assertTrue(boundary >= GUTTER_5_6_FROM && boundary <= GUTTER_5_6_TO,
                "the saved cut's 5|6 boundary should be in the gap too, not five pixels "
                        + "inside cell six: " + boundary);
        for (TilesetExport.Entry entry : document.entries) {
            if (!entry.id.equals(TilesetOperations.gridId(document.idPrefix, 5, 0))) continue;
            assertEquals(cut.cell(5, 0), entry.piece,
                    "every cell's rectangle should come from the saved cut");
        }
    }
}
