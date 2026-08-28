package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generated art is laid out to whatever the prompt asked for, so a plate's cells
 * are square only by coincidence. The split has to cut the stated grid exactly
 * and tile the plate with no gap and no overlap.
 */
class GridSplitTest {

    private static SheetSlicer.Piece plate(int w, int h) {
        return new SheetSlicer.Piece(0, 0, w, h);
    }

    /** One plate cut to the stated layout, as the parts alone. */
    private static List<TilesetExport.Entry> cells(int cols, int rows) {
        TilesetExport.Entry fused = new TilesetExport.Entry(plate(2500, 2600), "doodad.urban.plate");
        return TilesetOperations.splitOnGrid(
                List.of(fused), entry -> true, "doodad.urban", cols, rows);
    }

    /** Every part, laid end to end, must reconstruct the plate exactly. */
    private static void assertTiles(SheetSlicer.Piece whole, List<SheetSlicer.Piece> parts,
                                    int cols, int rows) {
        assertEquals(cols * rows, parts.size());
        long area = 0;
        for (SheetSlicer.Piece part : parts) {
            area += (long) part.width() * part.height();
            assertTrue(part.x() >= whole.x() && part.y() >= whole.y(), "part escapes left/top");
            assertTrue(part.right() <= whole.right() && part.bottom() <= whole.bottom(),
                    "part escapes right/bottom: " + part);
        }
        assertEquals((long) whole.width() * whole.height(), area,
                "the parts must tile the plate exactly");
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col + 1 < cols; col++) {
                SheetSlicer.Piece left = parts.get(row * cols + col);
                SheetSlicer.Piece right = parts.get(row * cols + col + 1);
                assertEquals(left.right() + 1, right.x(), "no gap or overlap between columns");
            }
        }
    }

    @Test
    void aTallStripIsCutIntoItsFramesRatherThanIntoSquares() {
        // nature-tiles: 20 frames of 109x724. The old cell-size split read this
        // as 20x7 because it assumed one number described both axes.
        SheetSlicer.Piece whole = plate(2172, 724);

        List<SheetSlicer.Piece> parts = SheetSlicer.splitOnGrid(whole, 20, 1);

        assertTiles(whole, parts, 20, 1);
        assertEquals(724, parts.get(0).height(), "a strip frame is the full height of the plate");
        assertTrue(parts.get(0).width() >= 108 && parts.get(0).width() <= 109);
    }

    @Test
    void anUnevenDivisionStillTilesThePlateExactly() {
        // 1225 does not divide by 25, and 1284 does not divide by 26.
        SheetSlicer.Piece whole = plate(1225, 1284);

        assertTiles(whole, SheetSlicer.splitOnGrid(whole, 25, 26), 25, 26);
    }

    @Test
    void theGridIsTakenAsStatedRatherThanInferred() {
        SheetSlicer.Piece whole = plate(1254, 1254);

        assertTiles(whole, SheetSlicer.splitOnGrid(whole, 10, 10), 10, 10);
        // The same plate cut to a different stated layout obeys the statement.
        assertTiles(whole, SheetSlicer.splitOnGrid(whole, 4, 7), 4, 7);
    }

    @Test
    void readingOrderIsRowMajorSoAPlateFillsABlocksSlotsInOrder() {
        List<SheetSlicer.Piece> parts = SheetSlicer.splitOnGrid(plate(300, 300), 3, 3);

        assertEquals(0, parts.get(0).x());
        assertEquals(0, parts.get(0).y());
        assertEquals(200, parts.get(2).x(), "third part is the top-right cell");
        assertEquals(0, parts.get(2).y());
        assertEquals(0, parts.get(3).x(), "fourth part starts the second row");
        assertEquals(100, parts.get(3).y());
    }

    @Test
    void aDegenerateGridIsRejectedRatherThanSilentlyClamped() {
        assertThrows(IllegalArgumentException.class,
                () -> SheetSlicer.splitOnGrid(plate(100, 100), 0, 4));
        assertThrows(IllegalArgumentException.class,
                () -> SheetSlicer.splitOnGrid(plate(100, 100), 4, -1));
    }

    @Test
    void onlyTheSelectedPlateIsCutAndItsPartsStandWhereItStood() {
        // The window cuts a table selection and the MCP tool cuts one named
        // piece; both go through here, so a sheet with a plate among its props
        // has to come back with the props untouched and in place.
        TilesetExport.Entry prop = new TilesetExport.Entry(plate(10, 10), "doodad.crate");
        prop.footprintX = 2;
        TilesetExport.Entry fused = new TilesetExport.Entry(plate(100, 100), "doodad.deck");
        fused.cover = "full";
        TilesetExport.Entry tail = new TilesetExport.Entry(plate(4, 4), "doodad.pipe");

        List<TilesetExport.Entry> replaced = TilesetOperations.splitOnGrid(
                List.of(prop, fused, tail), entry -> entry == fused, "doodad.hold", 2, 2);

        assertEquals(6, replaced.size());
        assertSame(prop, replaced.get(0), "an untouched piece keeps its identity, not a copy");
        assertSame(tail, replaced.get(5), "the parts stand where the plate stood");
        assertEquals("doodad.hold.c0r0", replaced.get(1).id,
                "a cell is named for where it sits, not for the plate it came out of");
        assertEquals("full", replaced.get(1).cover, "a plate's cover carries onto its cells");
        assertEquals(1, replaced.get(1).footprintX, "a cell of a plate is one cell");
    }

    @Test
    void everyCellIsNamedForWhereItSitsOnThePlate() {
        // The whole point: a row in the editor's table has to be findable in the
        // picture, and a person and a model have to be able to name one cell.
        List<TilesetExport.Entry> cut = cells(10, 10);

        assertEquals("doodad.urban.c0r0", cut.get(0).id);
        assertEquals("doodad.urban.c6r1", cut.get(16).id, "column first, then row");
        assertEquals("doodad.urban.c9r9", cut.get(99).id);

        Set<String> distinct = new HashSet<>();
        for (TilesetExport.Entry cell : cut) {
            assertTrue(distinct.add(cell.id), "two cells cannot share a name: " + cell.id);
        }
    }

    @Test
    void aStripIsNumberedAcrossRatherThanDown() {
        // nature-tiles is 20x1. Decomposing the part index by the row count
        // instead of the column count reads this strip as one column of twenty.
        List<TilesetExport.Entry> cut = cells(20, 1);

        assertEquals("doodad.urban.c0r0", cut.get(0).id);
        assertEquals("doodad.urban.c19r0", cut.get(19).id);
    }

    @Test
    void aPlateLargerThanTheAlphabetNamesEveryCellDistinctly() {
        // Floors_Tiles is 25x26 — 650 cells. The serial scheme this replaced ran
        // out of alphabet at 26 and had to carry into a second letter.
        Set<String> distinct = new HashSet<>();
        for (TilesetExport.Entry cell : cells(25, 26)) {
            assertTrue(cell.id.matches("doodad\\.urban\\.c\\d+r\\d+"), cell.id);
            assertTrue(distinct.add(cell.id), "two cells cannot share a name: " + cell.id);
        }
        assertEquals(650, distinct.size());
    }

    @Test
    void aCutThatWouldRenameAPieceOutOfExistenceIsRefused() {
        // A positional id cannot be stepped past the way a serial one can: the
        // number in it is the address, so dodging a clash would name the wrong
        // cell. Half-applying the cut would be worse still.
        TilesetExport.Entry standing = new TilesetExport.Entry(plate(10, 10), "doodad.urban.c1r0");
        TilesetExport.Entry fused = new TilesetExport.Entry(plate(100, 100), "doodad.urban.plate");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> TilesetOperations.splitOnGrid(
                        List.of(standing, fused), entry -> entry == fused, "doodad.urban", 2, 2));

        assertTrue(refused.getMessage().contains("doodad.urban.c1r0"),
                "the refusal has to name the id that clashed: " + refused.getMessage());
    }

    @Test
    void theAtlasDestinationFollowsWhatTheSheetContains() {
        assertEquals("graphics/tilesets/reactor.png",
                TilesetDocument.defaultOutputSheet("reactor", true));
        assertEquals("graphics/doodads/reactor.png",
                TilesetDocument.defaultOutputSheet("reactor", false));

        TilesetDocument doc = new TilesetDocument();
        doc.sheetName = "reactor";
        assertEquals("graphics/tilesets/reactor.png", doc.resolvedOutputSheet(true));
        doc.outputSheet = "graphics/props/reactor.png";
        assertEquals("graphics/props/reactor.png", doc.resolvedOutputSheet(true),
                "an explicit destination wins over the derived one");
    }

    @Test
    void cellSizesAreDerivedPerAxisFromTheStatedLayout() {
        TilesetDocument doc = new TilesetDocument();
        doc.gridCols = 20;
        doc.gridRows = 1;

        assertEquals(109, doc.cellPxX(2172));
        assertEquals(724, doc.cellPxY(724), "the two axes are not one number");
    }

    @Test
    void aMeasuredPitchIsTheCellSizeRatherThanTheCanvasDivision() {
        TilesetDocument doc = new TilesetDocument();
        doc.gridCols = 10;
        doc.gridRows = 10;
        doc.setCut(GridCut.dividing(1254, 1254, 10, 10).withColumnAxis(8.5, 123.23));

        assertEquals(123, doc.cellPxX(1254), "the cell is the size the art drew it");
        assertEquals(125, doc.cellPxY(1254), "the unmeasured axis still divides the canvas");
    }

    @Test
    void aPlacedCutStartsWhereItIsToldAndStillTilesWithoutGaps() {
        // The bug: dividing 1254 by ten puts the boundaries 125.4 apart from
        // zero, while the art's are 123.23 apart from 8.5.
        GridCut cut = new GridCut(10, 10, 8.5, 123.23, 8.5, 123.23);

        List<SheetSlicer.Piece> parts = SheetSlicer.splitOnGrid(cut);

        assertEquals(100, parts.size());
        assertEquals(9, parts.get(0).x(), "the grid starts inside the margin, not at zero");
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col + 1 < 10; col++) {
                assertEquals(parts.get(row * 10 + col).right() + 1,
                        parts.get(row * 10 + col + 1).x(),
                        "no gap or overlap between columns");
            }
        }
        assertEquals(1241, parts.get(99).right() + 1,
                "the margin past the last line is outside the grid, which is what a margin is");
    }

    @Test
    void aCutIsNotADivisionOfTheCanvasOnceItHasBeenPlaced() {
        assertTrue(GridCut.dividing(1254, 1254, 10, 10).isDivisionOf(1254, 1254));
        assertFalse(GridCut.dividing(1254, 1254, 10, 10)
                .withColumnAxis(8.5, 123.23).isDivisionOf(1254, 1254));
    }

    @Test
    void reCuttingMovesTheCellsAndKeepsEverythingWrittenOnThem() {
        // Ids are positional and block membership lives on the entry, so a
        // re-cut recomputes rectangles. Going back through the slicer would
        // destroy both, which is why re-slicing a cut plate is refused.
        List<TilesetExport.Entry> cells = new ArrayList<>(cells(3, 3));
        cells.get(4).blockId = "urban.wall";
        cells.get(4).slot = "center";
        cells.get(4).note = "the wall's interior";
        TilesetExport.Entry prop = new TilesetExport.Entry(plate(10, 10), "doodad.urban.crate");
        cells.add(prop);

        TilesetOperations.Recut recut = TilesetOperations.recut(
                cells, "doodad.urban", new GridCut(3, 3, 8.5, 123.23, 8.5, 123.23));

        assertEquals(9, recut.moved());
        assertEquals(9, recut.shifted());
        assertEquals(List.of(), recut.outside());
        assertEquals("urban.wall", cells.get(4).blockId, "membership is on the entry, not the cut");
        assertEquals("center", cells.get(4).slot);
        assertEquals("the wall's interior", cells.get(4).note);
        assertEquals("doodad.urban.c1r1", cells.get(4).id, "a positional id is stable across a re-cut");
        assertEquals(new SheetSlicer.Piece(132, 132, 123, 123), cells.get(4).piece);
        assertEquals(plate(10, 10), prop.piece, "a piece that is not a cut cell is left alone");
    }

    @Test
    void aCellOutsideANarrowedCutIsReportedRatherThanMoved() {
        List<TilesetExport.Entry> cells = new ArrayList<>(cells(3, 3));

        TilesetOperations.Recut recut = TilesetOperations.recut(
                cells, "doodad.urban", new GridCut(2, 2, 0, 100, 0, 100));

        assertEquals(4, recut.moved());
        assertEquals(List.of("doodad.urban.c2r0", "doodad.urban.c2r1",
                        "doodad.urban.c0r2", "doodad.urban.c1r2", "doodad.urban.c2r2"),
                recut.outside(),
                "shrinking the stated count is a decision, so a re-cut reports it and stops");
    }
}
