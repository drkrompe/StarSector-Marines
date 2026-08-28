package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half of the annotation conversation that has to leave the window.
 *
 * <p>What is asserted here is the shared vocabulary rather than the pixels: that
 * a cell is named by its grid coordinate wherever a coordinate is honest, that
 * the same name appears in both halves of the handoff, and that a selection
 * keeps its arrangement, since a wall reads as a building only when its nine
 * cells are laid out as one.
 */
class TilesetSelectionReportTest {

    private static final int COLS = 10;
    private static final int ROWS = 10;
    private static final int CELL = 20;

    private static List<TilesetExport.Entry> grid() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                entries.add(new TilesetExport.Entry(
                        new SheetSlicer.Piece(col * CELL, row * CELL, CELL, CELL),
                        "doodad.t.c" + col + "r" + row));
            }
        }
        return entries;
    }

    private static BufferedImage sheet() {
        return new BufferedImage(COLS * CELL, ROWS * CELL, BufferedImage.TYPE_INT_ARGB);
    }

    /** The nine cells of a wall patch at origin (3,0), in layout order. */
    private static int[] wallPatch() {
        int[] selected = new int[9];
        for (int i = 0; i < 9; i++) {
            selected[i] = (i / 3) * COLS + 3 + (i % 3);
        }
        return selected;
    }

    @Test
    void aCellIsNamedByItsGridCoordinate() {
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(grid(), new int[]{13}, COLS, ROWS);
        assertEquals(1, cells.size());
        assertEquals(3, cells.get(0).col());
        assertEquals(1, cells.get(0).row());
        assertEquals("3,1", cells.get(0).label());
    }

    @Test
    void aSheetThatIsNotAGridIsNamedByIndexInstead() {
        List<TilesetExport.Entry> entries = grid().subList(0, 4);
        // A stated grid that does not account for every entry has been cut and
        // then edited; a coordinate off it would point at the wrong cell.
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(entries, new int[]{2}, COLS, ROWS);
        assertEquals(-1, cells.get(0).col());
        assertEquals("#2", cells.get(0).label());
    }

    @Test
    void outOfRangeSelectionIsDroppedRatherThanThrowing() {
        // The table and the canvas can disagree for one repaint while a document
        // is being replaced; a stale index must not take the page down.
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(grid(), new int[]{-1, 5, 900}, COLS, ROWS);
        assertEquals(1, cells.size());
        assertEquals("5,0", cells.get(0).label());
    }

    @Test
    void theTableIsKeyedByTheSameNamesTheImageIs() {
        List<TilesetExport.Entry> entries = grid();
        entries.get(3).blockId = "t.wall";
        entries.get(3).slot = "nw";
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(entries, wallPatch(), COLS, ROWS);

        String markdown = TilesetSelectionReport.markdown(
                "t", "do not export over the shipped one", entries.size(), cells, "C:/out.png");

        assertTrue(markdown.contains("9 of 100 cells selected"), markdown);
        assertTrue(markdown.contains("| 3,0 |"), markdown);
        assertTrue(markdown.contains("| 5,2 |"), markdown);
        assertTrue(markdown.contains("t.wall / nw"), markdown);
        // The sheet's own note is the only place a reason not to export is written.
        assertTrue(markdown.contains("do not export over the shipped one"), markdown);
        assertTrue(markdown.contains("C:/out.png"), markdown);
    }

    @Test
    void aNoteCannotBreakOutOfItsRow() {
        List<TilesetExport.Entry> entries = grid();
        entries.get(0).note = "two | pipes\nand a newline";
        String markdown = TilesetSelectionReport.markdown(
                "t", "", entries.size(),
                TilesetSelectionReport.cells(entries, new int[]{0}, COLS, ROWS), null);

        // The header row and one data row, and nothing the newline split off.
        long rows = markdown.lines().filter(line -> line.startsWith("| ")).count();
        assertEquals(2, rows, markdown);
        assertTrue(markdown.contains("two \\| pipes and a newline"), markdown);
        assertFalse(markdown.contains("Image of this selection"), markdown);
    }

    @Test
    void aPatchKeepsItsArrangementSoAMirroredWallWouldBeVisible() {
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(grid(), wallPatch(), COLS, ROWS);
        BufferedImage patch = TilesetSelectionReport.contactSheet(sheet(), "t", cells);

        BufferedImage one = TilesetSelectionReport.contactSheet(sheet(),
                "t", TilesetSelectionReport.cells(grid(), new int[]{0}, COLS, ROWS));
        // Three cells across and three down, against one of each — the 3x3 is
        // drawn as a 3x3 rather than flattened into a row.
        assertTrue(patch.getWidth() > one.getWidth() * 2, "width " + patch.getWidth());
        assertTrue(patch.getHeight() > one.getHeight() * 2, "height " + patch.getHeight());
    }

    @Test
    void aSelectionWithHolesIsDrawnWhereTheCellsActuallyAre() {
        // Two cells four columns apart keep the gap between them, because the
        // gap is information about the sheet.
        List<TilesetSelectionReport.Cell> cells =
                TilesetSelectionReport.cells(grid(), new int[]{0, 4}, COLS, ROWS);
        BufferedImage spread = TilesetSelectionReport.contactSheet(sheet(), "t", cells);
        BufferedImage adjacent = TilesetSelectionReport.contactSheet(sheet(),
                "t", TilesetSelectionReport.cells(grid(), new int[]{0, 1}, COLS, ROWS));
        assertTrue(spread.getWidth() > adjacent.getWidth(), "spread " + spread.getWidth());
    }

    @Test
    void anEmptySelectionIsRefusedRatherThanDrawnBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> TilesetSelectionReport.contactSheet(sheet(), "t", List.of()));
    }
}
