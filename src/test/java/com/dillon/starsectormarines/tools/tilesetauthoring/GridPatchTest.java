package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving a whole plate's cut as one grid.
 *
 * <p>The thing being pinned is that the grid <em>describes</em> cells that were
 * already regular before it replaces them: a patch read off a plate and written
 * straight back must not move a pixel, or every visit to this screen would nudge
 * the sheet. Everything else here is about what happens when it is deliberately
 * moved.
 */
public class GridPatchTest {

    /** A cols x rows plate of touching cells, the shape a wall block is cut in. */
    private static List<TilesetExport.Entry> plate(int originX, int originY,
                                                   int cols, int rows, int cell) {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                entries.add(new TilesetExport.Entry(
                        new SheetSlicer.Piece(originX + col * cell, originY + row * cell,
                                cell, cell),
                        "doodad.road.c" + col + "r" + row));
            }
        }
        return entries;
    }

    private static GridPatch patchOf(List<TilesetExport.Entry> entries) {
        GridPatch.Derived derived = GridPatch.of(entries);
        assertNull(derived.refusal(), "expected these to form a grid");
        return derived.patch();
    }

    @Test
    void aBlocksNineCellsAreReadBackAsTheGridTheyWereCutOn() {
        GridPatch patch = patchOf(plate(96, 0, 3, 3, 32));

        assertEquals(3, patch.cut().cols());
        assertEquals(3, patch.cut().rows());
        assertEquals(96.0, patch.cut().originX(), 1e-9);
        assertEquals(32.0, patch.cut().pitchX(), 1e-9);
        assertEquals(new SheetSlicer.Piece(96, 0, 96, 96), patch.bounds());
        assertEquals(0, patch.drift(),
                "reading a regular plate and writing it back must not move anything");
    }

    /** One piece is the 1x1 case, so a single correction is the same operation. */
    @Test
    void onePieceIsAOneByOnePatch() {
        GridPatch patch = patchOf(List.of(new TilesetExport.Entry(
                new SheetSlicer.Piece(41, 2, 30, 29), "piece-0")));

        assertEquals(1, patch.cut().cols());
        assertEquals(1, patch.cut().rows());
        assertEquals(new SheetSlicer.Piece(41, 2, 30, 29), patch.bounds());
        assertEquals(0, patch.drift());
    }

    @Test
    void movingTheOriginMovesEveryCellAndResizesNone() throws IOException {
        List<TilesetExport.Entry> entries = plate(96, 0, 3, 3, 32);
        GridPatch patch = patchOf(entries);

        GridPatch.Applied applied = patch
                .withCut(patch.cut().withColumnAxis(94.0, 32.0))
                .applyTo(512, 192);

        assertEquals(9, applied.moved());
        assertEquals(9, applied.shifted());
        assertEquals(2, applied.maxShift());
        for (TilesetExport.Entry entry : entries) {
            assertEquals(32, entry.piece.width(), entry.id + " must keep its size");
            assertEquals(32, entry.piece.height(), entry.id + " must keep its size");
        }
        assertEquals(new SheetSlicer.Piece(94, 0, 32, 32), entries.get(0).piece);
        assertEquals(new SheetSlicer.Piece(158, 64, 32, 32), entries.get(8).piece);
    }

    /** Resizing the cell moves the seams with it, so the cells stay touching. */
    @Test
    void changingTheCellSizeKeepsTheCellsTouching() throws IOException {
        List<TilesetExport.Entry> entries = plate(0, 0, 3, 1, 32);
        GridPatch patch = patchOf(entries);

        patch.withCut(patch.cut().withColumnAxis(0.0, 30.0)).applyTo(512, 192);

        assertEquals(new SheetSlicer.Piece(0, 0, 30, 32), entries.get(0).piece);
        assertEquals(new SheetSlicer.Piece(30, 0, 30, 32), entries.get(1).piece);
        assertEquals(new SheetSlicer.Piece(60, 0, 30, 32), entries.get(2).piece);
    }

    /**
     * A plate whose pitch is not a whole number reads back as the cells it has.
     *
     * <p>This is the fault the patch exists for: 47.55 rounds to three different
     * widths across three columns, and a grid that could only describe whole
     * pixels would have to move one of them to say so.
     */
    @Test
    void aPitchThatIsNotAWholeNumberSurvivesBeingReadBack() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        GridCut source = new GridCut(3, 1, 0.0, 47.55, 0.0, 32.0);
        for (int col = 0; col < 3; col++) {
            entries.add(new TilesetExport.Entry(source.cell(col, 0), "cell-" + col));
        }
        assertNotEquals(entries.get(0).piece.width(), entries.get(1).piece.width(),
                "this plate's columns are deliberately not all the same width");

        GridPatch patch = patchOf(entries);

        assertNotEquals(Math.rint(patch.cut().pitchX()), patch.cut().pitchX(),
                "a fractional pitch must not be rounded away");
        assertEquals(0, patch.drift(), "and reading it back must reproduce the cells exactly");
    }

    /**
     * A cell somebody already nudged is still in its column.
     *
     * <p>Otherwise the one screen that fixes a cut would refuse every plate that
     * had ever been fixed on it.
     */
    @Test
    void aCellNudgedByAPixelStaysInItsColumn() {
        List<TilesetExport.Entry> entries = plate(0, 0, 3, 3, 32);
        entries.get(4).piece = new SheetSlicer.Piece(33, 32, 32, 32);

        GridPatch patch = patchOf(entries);

        assertEquals(3, patch.cut().cols());
        assertEquals(3, patch.cut().rows());
        assertEquals(1, patch.drift(),
                "and adopting the grid says how far it would move it back");
    }

    @Test
    void aSelectionThatIsNotAFilledRectangleIsNotAGrid() {
        List<TilesetExport.Entry> entries = new ArrayList<>(plate(0, 0, 2, 2, 32));
        entries.remove(3);

        GridPatch.Derived derived = GridPatch.of(entries);

        assertNull(derived.patch());
        assertTrue(derived.refusal().contains("2x2"), derived.refusal());
        assertTrue(derived.refusal().contains("filled"), derived.refusal());
    }

    @Test
    void nothingIsSelectedIsSaidPlainlyRatherThanRefused() {
        GridPatch.Derived derived = GridPatch.of(List.of());

        assertNull(derived.patch());
        assertTrue(derived.refusal().contains("Pick a piece"), derived.refusal());
    }

    /** A patch half-applied is worse than one not applied. */
    @Test
    void aGridThatWouldLeaveTheSheetMovesNothing() {
        List<TilesetExport.Entry> entries = plate(96, 0, 3, 3, 32);
        GridPatch patch = patchOf(entries);

        IOException refused = assertThrows(IOException.class, () -> patch
                .withCut(patch.cut().withColumnAxis(440.0, 32.0))
                .applyTo(512, 192));

        assertTrue(refused.getMessage().contains("runs off"), refused.getMessage());
        for (TilesetExport.Entry entry : entries) {
            assertTrue(entry.piece.x() >= 96 && entry.piece.x() <= 160,
                    entry.id + " moved despite the refusal: " + entry.piece);
        }
    }
}
